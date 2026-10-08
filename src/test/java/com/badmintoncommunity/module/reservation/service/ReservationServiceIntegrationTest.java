package com.badmintoncommunity.module.reservation.service;

import com.badmintoncommunity.common.BusinessException;
import com.badmintoncommunity.common.ResultCode;
import com.badmintoncommunity.module.reservation.dto.CreateReservationRequest;
import com.badmintoncommunity.module.reservation.dto.ReservationVO;
import com.badmintoncommunity.support.Fixtures;
import com.badmintoncommunity.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预约业务规则集成测试：连真实 MySQL，走真实事务。
 *
 * <p>与 {@link ReservationServiceUnitTest} 的分工：单测验证"判断顺序与算法"，
 * 本类验证"规则落在数据库上到底成不成立" —— 冲突检查的 SQL 写对没有、
 * 取消是否真的把场地放开、账本里活动占场与个人预约是否互相排斥。</p>
 *
 * <p>加 {@code @Transactional} 是为了让用例自动回滚：这些用例只读断言，不需要
 * 真实提交。真正要验证"提交后别人看得见"的并发场景放在
 * {@link ReservationConcurrencyTest}（那一类<b>不能</b>加事务，否则多线程互相看不见）。</p>
 */
@Transactional
@DisplayName("预约 Service（集成：真实 MySQL）")
class ReservationServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ReservationService reservationService;

    private long userA;
    private long userB;
    private long court1;
    private long court2;
    private long court3;
    private long disabledCourt;

    @BeforeEach
    void seed() {
        long venueId = fx.venue("venue");
        court1 = fx.court(venueId, "court-1", 1);
        court2 = fx.court(venueId, "court-2", 1);
        court3 = fx.court(venueId, "court-3", 1);
        disabledCourt = fx.court(venueId, "court-disabled", 2);
        userA = fx.user("userA");
        userB = fx.user("userB");
    }

    // ==================== 创建：正常路径 ====================

    @Test
    @DisplayName("创建成功：返回有效预约，场地名来自 join，库里多出一条有效占用")
    void createsReservation() {
        ReservationVO created = reservationService.create(userA, request(court1, 10, 11));

        assertNotNull(created.getId());
        assertEquals(court1, created.getCourtId());
        assertEquals(userA, created.getUserId());
        assertEquals(1, created.getStatus());
        assertEquals(tomorrow(10), created.getStartTime());
        assertEquals(tomorrow(11), created.getEndTime());
        assertEquals(Fixtures.MARK + "court-1", created.getCourtName(),
                "court_name 应由查询时 join court 得到");
        assertEquals(1, fx.countActiveReservations(court1, tomorrow(10), tomorrow(11)));
    }

    // ==================== 创建：拒绝路径 ====================

    @Test
    @DisplayName("球场不存在 → COURT_NOT_FOUND")
    void rejectsUnknownCourt() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> reservationService.create(userA, request(999_999L, 10, 11)));
        assertEquals(ResultCode.COURT_NOT_FOUND, ex.getResultCode());
    }

    @Test
    @DisplayName("球场已停用 → COURT_UNAVAILABLE")
    void rejectsDisabledCourt() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> reservationService.create(userA, request(disabledCourt, 10, 11)));
        assertEquals(ResultCode.COURT_UNAVAILABLE, ex.getResultCode());
    }

    @Test
    @DisplayName("时段已被他人预约 → COURT_ALREADY_RESERVED")
    void rejectsSlotTakenByAnotherUser() {
        reservationService.create(userB, request(court1, 10, 11));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reservationService.create(userA, request(court1, 10, 11)));
        assertEquals(ResultCode.COURT_ALREADY_RESERVED, ex.getResultCode());
    }

    @Test
    @DisplayName("时段被活动锁场占用 → COURT_ALREADY_RESERVED（统一账本：活动与个人互斥）")
    void rejectsSlotOccupiedByActivity() {
        long creator = fx.user("creator");
        long activityId = fx.activity(creator, "act-occupy", tomorrow(10), tomorrow(12), 4, 1);
        fx.activityReservation(court1, activityId, tomorrow(10), tomorrow(11));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reservationService.create(userA, request(court1, 10, 11)));
        assertEquals(ResultCode.COURT_ALREADY_RESERVED, ex.getResultCode());
    }

    @Test
    @DisplayName("同一用户重叠时段最多 2 块场，第 3 块被拒")
    void rejectsThirdOverlappingReservation() {
        reservationService.create(userA, request(court1, 10, 11));
        reservationService.create(userA, request(court2, 10, 11));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reservationService.create(userA, request(court3, 10, 11)));
        assertEquals(ResultCode.PERSONAL_RESERVATION_LIMIT_EXCEEDED, ex.getResultCode());
    }

    @Test
    @DisplayName("不重叠的时段不受「最多 2 块场」限制")
    void allowsNonOverlappingReservationsBeyondTwo() {
        reservationService.create(userA, request(court1, 10, 11));
        reservationService.create(userA, request(court1, 11, 12));
        // 12-13 与前面两段都不重叠 → 不计入"重叠上限"（但会撞上同场 2 小时上限，见下一个用例）
        // 这里改用另一块场，隔离掉规则 18 的干扰
        reservationService.create(userA, request(court2, 12, 13));
        reservationService.create(userA, request(court3, 12, 13));

        assertEquals(1, fx.countActiveReservations(court2, tomorrow(12), tomorrow(13)));
        assertEquals(1, fx.countActiveReservations(court3, tomorrow(12), tomorrow(13)));
    }

    @Test
    @DisplayName("同日同场连续占场超过 2 小时 → COURT_DAILY_DURATION_EXCEEDED")
    void rejectsDailyDurationOverTwoHours() {
        reservationService.create(userA, request(court1, 10, 11));
        reservationService.create(userA, request(court1, 11, 12));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reservationService.create(userA, request(court1, 12, 13)));
        assertEquals(ResultCode.COURT_DAILY_DURATION_EXCEEDED, ex.getResultCode());
    }

    @Test
    @DisplayName("已过去的时段 → BOOKING_WINDOW_EXCEEDED")
    void rejectsPastSlot() {
        // 用"今天 00:00-01:00"而不是"now 减 N 小时"：后者在凌晨会跨到前一天，
        // 变成先撞上跨天校验，断言就跟着当前时间漂移了
        LocalDateTime pastStart = LocalDate.now().atStartOfDay();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> reservationService.create(userA, request(court1, pastStart, pastStart.plusHours(1))));
        assertEquals(ResultCode.BOOKING_WINDOW_EXCEEDED, ex.getResultCode());
    }

    // ==================== 取消 ====================

    @Test
    @DisplayName("取消成功：状态变已取消，场地立刻放开给别人约")
    void cancelReleasesCourt() {
        ReservationVO created = reservationService.create(userA, request(court1, 10, 11));
        assertEquals(1, fx.countActiveReservations(court1, tomorrow(10), tomorrow(11)));

        ReservationVO cancelled = reservationService.cancel(userA, created.getId());
        assertEquals(2, cancelled.getStatus());
        assertNotNull(cancelled.getCancelTime());
        assertEquals(0, fx.countActiveReservations(court1, tomorrow(10), tomorrow(11)));

        // 场地真的空出来了：另一个人能约上
        ReservationVO byOther = reservationService.create(userB, request(court1, 10, 11));
        assertEquals(1, byOther.getStatus());
    }

    @Test
    @DisplayName("重复取消是幂等的：不报错、不重复写 cancel_time")
    void cancelIsIdempotent() {
        ReservationVO created = reservationService.create(userA, request(court1, 10, 11));

        ReservationVO first = reservationService.cancel(userA, created.getId());
        ReservationVO second = reservationService.cancel(userA, created.getId());

        assertEquals(2, second.getStatus());
        assertEquals(first.getCancelTime(), second.getCancelTime(), "重复取消不该刷新取消时间");
    }

    @Test
    @DisplayName("开场前不足 4 小时不能自行取消 → CANCEL_WINDOW_EXCEEDED")
    void rejectsCancelInsideFourHourWindow() {
        LocalDateTime soon = LocalDateTime.now().plusHours(2).truncatedTo(ChronoUnit.HOURS);
        long reservationId = fx.personalReservation(court1, userA, soon, soon.plusHours(1), 1);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reservationService.cancel(userA, reservationId));
        assertEquals(ResultCode.CANCEL_WINDOW_EXCEEDED, ex.getResultCode());
    }

    @Test
    @DisplayName("开场前超过 4 小时可以自行取消")
    void allowsCancelOutsideFourHourWindow() {
        LocalDateTime later = LocalDateTime.now().plusHours(5).truncatedTo(ChronoUnit.HOURS);
        long reservationId = fx.personalReservation(court1, userA, later, later.plusHours(1), 1);

        assertEquals(2, reservationService.cancel(userA, reservationId).getStatus());
    }

    @Test
    @DisplayName("取消别人的预约 → 按「找不到」处理，不泄露存在性")
    void rejectsCancelByAnotherUser() {
        ReservationVO created = reservationService.create(userA, request(court1, 10, 11));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reservationService.cancel(userB, created.getId()));
        assertEquals(ResultCode.RESERVATION_NOT_FOUND, ex.getResultCode());
    }

    @Test
    @DisplayName("活动占场行不能通过个人接口取消 → RESERVATION_NOT_FOUND")
    void rejectsCancelOfActivityOccupancy() {
        long creator = fx.user("creator");
        long activityId = fx.activity(creator, "act-cancel", tomorrow(10), tomorrow(11), 4, 1);
        long occupancyId = fx.activityReservation(court1, activityId, tomorrow(10), tomorrow(11));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reservationService.cancel(userA, occupancyId));
        assertEquals(ResultCode.RESERVATION_NOT_FOUND, ex.getResultCode());
        assertTrue(fx.countActiveReservations(court1, tomorrow(10), tomorrow(11)) == 1,
                "被拒绝的取消不该影响活动的占场");
    }

    @Test
    @DisplayName("不存在的预约 → RESERVATION_NOT_FOUND")
    void rejectsCancelOfUnknownReservation() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> reservationService.cancel(userA, 999_999L));
        assertEquals(ResultCode.RESERVATION_NOT_FOUND, ex.getResultCode());
    }

    // ==================== 时间夹具 ====================

    private static LocalDateTime tomorrow(int hour) {
        return LocalDate.now().plusDays(1).atTime(hour, 0);
    }

    private static CreateReservationRequest request(long courtId, int fromHour, int toHour) {
        return request(courtId, tomorrow(fromHour), tomorrow(toHour));
    }

    private static CreateReservationRequest request(long courtId, LocalDateTime start, LocalDateTime end) {
        CreateReservationRequest req = new CreateReservationRequest();
        req.setCourtId(courtId);
        req.setStartTime(start);
        req.setEndTime(end);
        return req;
    }
}
