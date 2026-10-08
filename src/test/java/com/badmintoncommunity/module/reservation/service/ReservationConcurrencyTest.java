package com.badmintoncommunity.module.reservation.service;

import com.badmintoncommunity.common.BusinessException;
import com.badmintoncommunity.module.reservation.dto.CreateReservationRequest;
import com.badmintoncommunity.support.Concurrency;
import com.badmintoncommunity.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预约并发测试：验证「同一时段不会被约到两次」。
 *
 * <h2>为什么这类用例必须连真库、且不能加 @Transactional</h2>
 * <p>并发正确性来自<b>数据库的行锁</b>与<b>事务隔离级别</b>。测试方法上的事务不会自动传到
 * 新建的工作线程；若测试数据尚未提交，工作线程可能看不到，测试方法回滚也不会回滚它们的写入。
 * 因此每个线程走 {@code ReservationService} 自己的真实事务，测试数据由夹具清理。</p>
 *
 * <h2>断言的是「分布」，不是「谁赢」</h2>
 * <p>并发下锁的归属不确定，所以只断言"恰好 1 个人成功、其余全部收到已占用"这种
 * <b>与调度顺序无关的不变量</b>。</p>
 *
 * <h2>历史缺陷及回归守卫</h2>
 * <p>{@link #onlyOneWinnerForSameSlot} 曾稳定失败（{@code {OK=4}}，库里 4 行）。
 * 根因是 {@code ReservationService.create} 在同一时段尚无占用行时，冲突检查沿用了旧快照：</p>
 * <ol>
 *   <li>{@code create} 先执 {@code findByIdForUpdate(userId)} 与
 *       {@code countPersonalOverlap}／{@code listPersonalOnCourtAndDay} —— 这些都是<b>普通快照读</b>。</li>
 *   <li>快照读在 REPEATABLE READ 下会把事务的<b>一致性读快照固定</b>在那一刻。</li>
 *   <li>修复前 {@code countCourtOverlap} 也是普通快照读，<b>只看旧快照</b>，
 *       看不到其它事务在快照之后提交的新行 —— 即使那些事务已经提交、即使我方已经持有 court 行锁。</li>
 *   <li>于是每个线程都读到"占用 0"，全部通过检查并插入。</li>
 * </ol>
 * <p>关键佐证：把 {@code create} 里的前置查询去掉、只留「锁 court → 查重 → 插入」，
 * 同一场景立刻变成正确的 1 行；在查重与插入之间人为加 200ms 延迟也仍是 1 行
 * （证明锁确实保护了时间窗，问题不在窗口宽度，而在<b>快照陈旧</b>）。
 * 现在 {@code countCourtOverlap} 使用当前读；本用例继续作为回归守卫。
 * 详见 {@code docs/known-issues.md}。</p>
 */

@DisplayName("预约并发：同一时段不能被约两次")
class ReservationConcurrencyTest extends IntegrationTestBase {

    @Autowired
    private ReservationService reservationService;

    private long court1;
    private long court2;
    private long court3;
    private final List<Long> users = new ArrayList<>();

    @BeforeEach
    void seed() {
        long venueId = fx.venue("venue");
        court1 = fx.court(venueId, "court-1", 1);
        court2 = fx.court(venueId, "court-2", 1);
        court3 = fx.court(venueId, "court-3", 1);

        users.clear();
        for (int i = 0; i < 6; i++) {
            users.add(fx.user("racer-" + i));
        }
    }

    @Test
    @DisplayName("4 个用户同时抢同一球场同一时段：恰好 1 人成功，其余全部「已被预约」")
    void onlyOneWinnerForSameSlot() throws Exception {
        LocalDateTime start = tomorrow(10);
        LocalDateTime end = tomorrow(11);

        Map<String, Integer> outcome = Concurrency.race(4, i -> attempt(users.get(i), court1, start, end));

        // 回归守卫：即使前置查询在 RR 下建立旧快照，球场冲突检查仍须看到已提交的占用。
        assertEquals(1, outcome.getOrDefault("OK", 0),
                "同一球场同一时段只允许 1 人约上，实际结果分布：" + outcome);
        assertEquals(3, outcome.getOrDefault("COURT_ALREADY_RESERVED", 0),
                "其余 3 人应全部收到「已被预约」，实际结果分布：" + outcome);
        assertEquals(1, fx.countActiveReservations(court1, start, end),
                "库里该时段最终只应有 1 条有效占用");
    }

    @Test
    @DisplayName("同一用户同时抢 3 块不同球场：受「最多 2 块重叠」约束，恰好 2 块成功")
    void personalOverlapLimitHoldsUnderConcurrency() throws Exception {
        LocalDateTime start = tomorrow(10);
        LocalDateTime end = tomorrow(11);
        long singleUser = users.get(0);
        List<Long> courts = List.of(court1, court2, court3);

        Map<String, Integer> outcome = Concurrency.race(3,
                i -> attempt(singleUser, courts.get(i), start, end));

        assertEquals(2, outcome.getOrDefault("OK", 0),
                "同一用户重叠时段上限为 2，实际结果分布：" + outcome);
        assertEquals(1, outcome.getOrDefault("PERSONAL_RESERVATION_LIMIT_EXCEEDED", 0),
                "第 3 块场应被「重叠上限」规则拒绝，实际结果分布：" + outcome);

        long occupied = courts.stream()
                .mapToLong(court -> fx.countActiveReservations(court, start, end))
                .sum();
        assertEquals(2, occupied, "该用户在同一时段最终应只占 2 块场");
    }

    @Test
    @DisplayName("时段已被活动锁场：并发抢也一个都进不去")
    void activityOccupancyBlocksAllConcurrentAttempts() throws Exception {
        LocalDateTime start = tomorrow(10);
        LocalDateTime end = tomorrow(11);

        long creator = fx.user("creator");
        long activityId = fx.activity(creator, "act-lock", tomorrow(10), tomorrow(12), 8, 1);
        fx.activityReservation(court1, activityId, start, end);

        Map<String, Integer> outcome = Concurrency.race(3, i -> attempt(users.get(i), court1, start, end));

        assertTrue(outcome.getOrDefault("OK", 0) == 0,
                "活动锁过的场不该被个人预约抢到，实际结果分布：" + outcome);
        assertEquals(1, fx.countActiveReservations(court1, start, end),
                "该时段仍应只有活动那 1 条占用");
    }

    // ==================== 工具 ====================

    /** 执行一次预约尝试，把异常翻译成结果标签（并发用例只关心分布，不关心异常类型）。 */
    private String attempt(long userId, long courtId, LocalDateTime start, LocalDateTime end) {
        CreateReservationRequest req = new CreateReservationRequest();
        req.setCourtId(courtId);
        req.setStartTime(start);
        req.setEndTime(end);
        try {
            reservationService.create(userId, req);
            return "OK";
        } catch (BusinessException ex) {
            return ex.getResultCode().getCode();
        } catch (Exception ex) {
            // 任何非业务异常都是"我们没预料到的并发后果"（死锁、约束冲突、超时），
            // 必须显式暴露成独立标签，否则会伪装成"被规则拒绝了"
            return "UNEXPECTED:" + ex.getClass().getSimpleName();
        }
    }

    private static LocalDateTime tomorrow(int hour) {
        return LocalDate.now().plusDays(1).atTime(hour, 0);
    }
}
