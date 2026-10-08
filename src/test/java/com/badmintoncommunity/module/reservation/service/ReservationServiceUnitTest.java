package com.badmintoncommunity.module.reservation.service;

import com.badmintoncommunity.common.BusinessException;
import com.badmintoncommunity.common.ResultCode;
import com.badmintoncommunity.module.reservation.dto.CreateReservationRequest;
import com.badmintoncommunity.module.reservation.dto.ReservationVO;
import com.badmintoncommunity.module.reservation.entity.Reservation;
import com.badmintoncommunity.module.reservation.mapper.ReservationMapper;
import com.badmintoncommunity.module.user.mapper.UserMapper;
import com.badmintoncommunity.module.venue.entity.Court;
import com.badmintoncommunity.module.venue.mapper.CourtMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 预约 Service 的纯单测：不连数据库，用替身验证「判断顺序」与「合并算法」。
 *
 * <h2>为什么这两类东西必须用单测，不能只靠接口测试</h2>
 * <p>接口测试（从外面发 HTTP）只能看到<b>最终结果</b>。而 {@code ReservationService} 里有两处
 * 关键正确性藏在中途：</p>
 * <ol>
 *   <li><b>加锁顺序</b>：先锁 user 行、再锁 court 行，是防死锁的固定顺序（business-flows §0.4）。
 *       顺序错了，接口照样返回成功 —— 只在两个事务交叉时偶发死锁。<b>从结果看不出来，
 *       只能断言调用顺序。</b></li>
 *   <li><b>2 小时合并算法</b>：它要把当天同球场的多个占用段合并后再判断跨度，
 *       是真正的算法逻辑（有排序、有断档、有相邻合并）。这类逻辑用几十个边界用例逐一钉死，
 *       比从接口绕一圈便宜得多。</li>
 * </ol>
 *
 * <p>其余"真的要和数据库交互才成立"的部分（并发、事务、约束）放在集成测试里。</p>
 */
@DisplayName("预约 Service（单测：顺序与算法）")
class ReservationServiceUnitTest {

    private static final long USER_ID = 7L;
    private static final long COURT_ID = 3L;

    private ReservationMapper reservationMapper;
    private CourtMapper courtMapper;
    private UserMapper userMapper;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        // 刻意不用 MockitoExtension：默认的严格替身会在"某个分支用不到某替身"时直接报错，
        // 而本类正是靠"某分支不该调用某替身"来断言的（见 加锁顺序 分组）。
        reservationMapper = Mockito.mock(ReservationMapper.class);
        courtMapper = Mockito.mock(CourtMapper.class);
        userMapper = Mockito.mock(UserMapper.class);
        service = new ReservationService(reservationMapper, courtMapper, userMapper);
    }

    // ==================== 加锁顺序（防死锁契约） ====================

    @Nested
    @DisplayName("加锁顺序")
    class LockOrdering {

        @Test
        @DisplayName("先锁 user 行 → 再锁 court 行 → 最后才做冲突检查")
        void acquiresLocksInFixedOrderAndChecksLast() {
            stubHappyPath(List.of(), 0L, 0L);

            service.create(USER_ID, request(COURT_ID, tomorrow(10), tomorrow(11)));

            InOrder order = Mockito.inOrder(userMapper, courtMapper, reservationMapper);
            order.verify(userMapper).findByIdForUpdate(USER_ID);
            order.verify(courtMapper).findByIdForUpdate(COURT_ID);
            order.verify(reservationMapper).countCourtOverlap(any(), any(), any());
        }

        @Test
        @DisplayName("时段非法时，一个锁都不该加")
        void validatesIntervalBeforeAnyLock() {
            LocalDateTime notOnTheHour = tomorrow(10).plusMinutes(30);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.create(USER_ID, request(COURT_ID, notOnTheHour, tomorrow(11))));

            assertEquals(ResultCode.INVALID_TIME, ex.getResultCode());
            verifyNoInteractions(userMapper, courtMapper, reservationMapper);
        }

        @Test
        @DisplayName("个人重叠已达上限时，不该再去锁球场行（提前失败，少占一把锁）")
        void rejectsOverlapLimitBeforeTouchingCourtLock() {
            when(reservationMapper.countPersonalOverlap(any(), any(), any())).thenReturn(2L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.create(USER_ID, request(COURT_ID, tomorrow(10), tomorrow(11))));

            assertEquals(ResultCode.PERSONAL_RESERVATION_LIMIT_EXCEEDED, ex.getResultCode());
            verify(userMapper).findByIdForUpdate(USER_ID);
            verifyNoInteractions(courtMapper);
        }
    }

    // ==================== 同日同场 2 小时上限（合并算法） ====================

    @Nested
    @DisplayName("同日同场 2 小时上限（requirements 规则 18）")
    class DailyDurationLimit {

        @Test
        @DisplayName("无历史占用时，恰好 2 小时的单次预约放行")
        void allowsExactlyTwoHoursAlone() {
            stubHappyPath(List.of(), 0L, 0L);
            assertDoesNotThrow(() -> service.create(USER_ID, request(COURT_ID, tomorrow(10), tomorrow(12))));
        }

        @Test
        @DisplayName("与已有占用首尾相接（10-11 + 11-12）合计 2 小时 → 放行")
        void mergesAdjacentSegmentsUpToTwoHours() {
            stubHappyPath(List.of(existingReservation(10, 11)), 0L, 0L);
            assertDoesNotThrow(() -> service.create(USER_ID, request(COURT_ID, tomorrow(11), tomorrow(12))));
        }

        @Test
        @DisplayName("首尾相接后超 2 小时（10-11 + 11-13）→ 拒绝")
        void rejectsAdjacentSegmentsOverTwoHours() {
            stubHappyPath(List.of(existingReservation(10, 11)), 0L, 0L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.create(USER_ID, request(COURT_ID, tomorrow(11), tomorrow(13))));

            assertEquals(ResultCode.COURT_DAILY_DURATION_EXCEEDED, ex.getResultCode());
        }

        @Test
        @DisplayName("与已有占用重叠（09-12 被包住）合并后恰好 2 小时 → 放行")
        void mergesOverlappingSegments() {
            stubHappyPath(List.of(existingReservation(10, 11)), 0L, 0L);
            assertDoesNotThrow(() -> service.create(USER_ID, request(COURT_ID, tomorrow(10), tomorrow(12))));
        }

        @Test
        @DisplayName("与已有占用重叠后超 2 小时（10-11 + 10-13 取并集 10-13）→ 拒绝")
        void rejectsOverlappingUnionOverTwoHours() {
            stubHappyPath(List.of(existingReservation(10, 11)), 0L, 0L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.create(USER_ID, request(COURT_ID, tomorrow(10), tomorrow(13))));

            assertEquals(ResultCode.COURT_DAILY_DURATION_EXCEEDED, ex.getResultCode());
        }

        @Test
        @DisplayName("中间有空档时按两段分别判断（9-10 与 11-12 各自 1 小时）→ 放行")
        void treatsGapAsSeparateSegments() {
            stubHappyPath(List.of(existingReservation(9, 10)), 0L, 0L);
            assertDoesNotThrow(() -> service.create(USER_ID, request(COURT_ID, tomorrow(11), tomorrow(12))));
        }

        @Test
        @DisplayName("空档两侧各自合法、但其中一段超限 → 仍拒绝（不能只看总时长）")
        void rejectsWhenAnyMergedSegmentExceedsLimit() {
            // 已有 8-11（3 小时，历史数据），新约 13-14：两段互不相邻，
            // 但 8-11 这一段本身就超限 —— 说明判据是"任一段跨度"而不是"当天累计"
            stubHappyPath(List.of(existingReservation(8, 11)), 0L, 0L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.create(USER_ID, request(COURT_ID, tomorrow(13), tomorrow(14))));

            assertEquals(ResultCode.COURT_DAILY_DURATION_EXCEEDED, ex.getResultCode());
        }

        @Test
        @DisplayName("当天没有其它占用时按单段判断（不会误把空列表当成超限）")
        void handlesEmptyHistoryAsSingleSegment() {
            // 说明边界：日范围过滤由 SQL 负责（listPersonalOnCourtAndDay 带 dayStart/dayEnd），
            // 服务层拿到的是"当天同球场"的行。这里验证空历史下不会误判。
            stubHappyPath(List.of(), 0L, 0L);
            assertDoesNotThrow(() -> service.create(USER_ID, request(COURT_ID, tomorrow(10), tomorrow(12))));
        }
    }

    // ==================== 球场侧校验与写入 ====================

    @Nested
    @DisplayName("球场侧校验与写入")
    class CourtChecks {

        @Test
        @DisplayName("球场不存在 → COURT_NOT_FOUND")
        void rejectsMissingCourt() {
            when(reservationMapper.countPersonalOverlap(any(), any(), any())).thenReturn(0L);
            when(courtMapper.findByIdForUpdate(any())).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.create(USER_ID, request(COURT_ID, tomorrow(10), tomorrow(11))));

            assertEquals(ResultCode.COURT_NOT_FOUND, ex.getResultCode());
        }

        @Test
        @DisplayName("球场已停用 → COURT_UNAVAILABLE")
        void rejectsUnavailableCourt() {
            stubHappyPath(List.of(), 0L, 0L);
            when(courtMapper.findByIdForUpdate(any())).thenReturn(courtWithStatus(2));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.create(USER_ID, request(COURT_ID, tomorrow(10), tomorrow(11))));

            assertEquals(ResultCode.COURT_UNAVAILABLE, ex.getResultCode());
        }

        @Test
        @DisplayName("时段已被占（含活动占场）→ COURT_ALREADY_RESERVED")
        void rejectsAlreadyReservedSlot() {
            stubHappyPath(List.of(), 0L, 1L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.create(USER_ID, request(COURT_ID, tomorrow(10), tomorrow(11))));

            assertEquals(ResultCode.COURT_ALREADY_RESERVED, ex.getResultCode());
        }

        @Test
        @DisplayName("成功时写入的是「个人行」：user_id 有值、activity_id 必须为空")
        void insertsPersonalOwnershipRow() {
            stubHappyPath(List.of(), 0L, 0L);

            service.create(USER_ID, request(COURT_ID, tomorrow(10), tomorrow(11)));

            ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
            verify(reservationMapper).insert(captor.capture());

            Reservation saved = captor.getValue();
            assertEquals(USER_ID, saved.getUserId());
            assertNull(saved.getActivityId(), "个人预约的 activity_id 必须为空（账本归属不变量）");
            assertEquals(COURT_ID, saved.getCourtId());
            assertEquals(1, saved.getStatus());
            assertEquals(tomorrow(10), saved.getStartTime());
            assertEquals(tomorrow(11), saved.getEndTime());
        }
    }

    // ==================== 夹具与替身 ====================

    private static LocalDateTime tomorrow(int hour) {
        return LocalDate.now().plusDays(1).atTime(hour, 0);
    }

    private static CreateReservationRequest request(long courtId, LocalDateTime start, LocalDateTime end) {
        CreateReservationRequest req = new CreateReservationRequest();
        req.setCourtId(courtId);
        req.setStartTime(start);
        req.setEndTime(end);
        return req;
    }

    /** 一条落在「明天 + 本球场」的已有个人占用，用于驱动合并算法。 */
    private static Reservation existingReservation(int fromHour, int toHour) {
        Reservation r = new Reservation();
        r.setId(100L + fromHour);
        r.setCourtId(COURT_ID);
        r.setUserId(USER_ID);
        r.setActivityId(null);
        r.setStatus(1);
        r.setStartTime(tomorrow(fromHour));
        r.setEndTime(tomorrow(toHour));
        return r;
    }

    private static Court courtWithStatus(int status) {
        Court court = new Court();
        court.setId(COURT_ID);
        court.setVenueId(1L);
        court.setName("1 号场");
        court.setStatus(status);
        return court;
    }

    /**
     * 把"能走到写入"这条路径上的替身都配好。
     *
     * @param sameDayRows     当天同球场已有的个人占用（驱动 2 小时合并）
     * @param personalOverlap 与目标时段重叠的个人预约数
     * @param courtOverlap    与目标时段重叠的球场占用数
     */
    private void stubHappyPath(List<Reservation> sameDayRows, long personalOverlap, long courtOverlap) {
        when(reservationMapper.countPersonalOverlap(any(), any(), any())).thenReturn(personalOverlap);
        when(reservationMapper.listPersonalOnCourtAndDay(any(), any(), any(), any())).thenReturn(sameDayRows);
        when(courtMapper.findByIdForUpdate(any())).thenReturn(courtWithStatus(1));
        when(reservationMapper.countCourtOverlap(any(), any(), any())).thenReturn(courtOverlap);
        when(reservationMapper.insert(any())).thenReturn(1);
        when(reservationMapper.findVOById(any())).thenReturn(new ReservationVO());
    }
}
