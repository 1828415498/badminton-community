package com.badmintoncommunity.module.reservation.service;

import com.badmintoncommunity.common.BusinessException;
import com.badmintoncommunity.common.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 预约时间规则单测（ADR-0004 / requirements 规则 3、4）。
 *
 * <p>这是整个项目<b>最该有单测的一段代码</b>：它是纯函数、零依赖、被两个模块共用
 * （个人预约与活动锁场都调它），而且它守的是"能不能约"的第一道门。
 * 这几条规则错一条，用户就会遇到"约上了却打不了"或"明明能约却约不了"。</p>
 */
class TimeRulesTest {

    private static LocalDateTime at(int dayOffset, int hour, int minute) {
        return LocalDateTime.now().plusDays(dayOffset).withHour(hour).withMinute(minute)
                .withSecond(0).withNano(0);
    }

    // ==================== validateInterval：时段本身是否合法 ====================

    @Test
    @DisplayName("时段：起止都为 null 时按参数不合法处理")
    void rejectsNullBounds() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> TimeRules.validateInterval(null, at(1, 10, 0)));
        assertEquals(ResultCode.PARAM_INVALID, ex.getResultCode());

        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> TimeRules.validateInterval(at(1, 10, 0), null));
        assertEquals(ResultCode.PARAM_INVALID, ex2.getResultCode());
    }

    @Test
    @DisplayName("时段：开始时间不是整点则拒绝")
    void rejectsNonHourlyStart() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> TimeRules.validateInterval(at(1, 10, 30), at(1, 11, 0)));
        assertEquals(ResultCode.INVALID_TIME, ex.getResultCode());
    }

    @Test
    @DisplayName("时段：结束时间不是整点则拒绝")
    void rejectsNonHourlyEnd() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> TimeRules.validateInterval(at(1, 10, 0), at(1, 11, 15)));
        assertEquals(ResultCode.INVALID_TIME, ex.getResultCode());
    }

    @Test
    @DisplayName("时段：end 必须严格晚于 start（相等也不行）")
    void rejectsEndNotAfterStart() {
        assertEquals(ResultCode.INVALID_TIME, assertThrows(BusinessException.class,
                () -> TimeRules.validateInterval(at(1, 10, 0), at(1, 10, 0))).getResultCode());
        assertEquals(ResultCode.INVALID_TIME, assertThrows(BusinessException.class,
                () -> TimeRules.validateInterval(at(1, 11, 0), at(1, 10, 0))).getResultCode());
    }

    @Test
    @DisplayName("时段：不允许跨自然日")
    void rejectsCrossDayInterval() {
        // 23:00 → 次日 01:00：两个时间点都合法（整点、end>start），但跨天必须被拦
        BusinessException ex = assertThrows(BusinessException.class,
                () -> TimeRules.validateInterval(at(1, 23, 0), at(2, 1, 0)));
        assertEquals(ResultCode.INVALID_TIME, ex.getResultCode());
    }

    @Test
    @DisplayName("时段：合法的整点区间应当放行")
    void acceptsValidInterval() {
        assertDoesNotThrow(() -> TimeRules.validateInterval(at(1, 10, 0), at(1, 11, 0)));
        // 单小时最长 23:00-24:00 这类边界：仍属同一天
        assertDoesNotThrow(() -> TimeRules.validateInterval(at(1, 0, 0), at(1, 23, 0)));
    }

    // ==================== ensureStartBookable：7 天窗口 ====================

    @Test
    @DisplayName("窗口：开始时间等于当前时刻要拒绝（必须晚于现在）")
    void rejectsStartEqualToNow() {
        LocalDateTime now = LocalDateTime.now();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> TimeRules.ensureStartBookable(now, now));
        assertEquals(ResultCode.BOOKING_WINDOW_EXCEEDED, ex.getResultCode());
    }

    @Test
    @DisplayName("窗口：过去时段要拒绝")
    void rejectsPastStart() {
        LocalDateTime now = LocalDateTime.now();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> TimeRules.ensureStartBookable(now.minusHours(1), now));
        assertEquals(ResultCode.BOOKING_WINDOW_EXCEEDED, ex.getResultCode());
    }

    @Test
    @DisplayName("窗口：今天稍晚一点可以约（允许当天）")
    void allowsLaterToday() {
        LocalDateTime now = LocalDateTime.now();
        assertDoesNotThrow(() -> TimeRules.ensureStartBookable(now.plusHours(1), now));
    }

    @Test
    @DisplayName("窗口：第 7 天可以约，第 8 天不行（边界两侧）")
    void enforcesSevenDayWindow() {
        LocalDateTime now = LocalDateTime.now();

        // 边界内侧：now + 7 天的同一时刻 —— 仍在窗口内（判据是日期不晚于 today+7）
        assertDoesNotThrow(() -> TimeRules.ensureStartBookable(now.plusDays(7), now));

        // 边界外侧：now + 8 天
        BusinessException ex = assertThrows(BusinessException.class,
                () -> TimeRules.ensureStartBookable(now.plusDays(8), now));
        assertEquals(ResultCode.BOOKING_WINDOW_EXCEEDED, ex.getResultCode());
    }
    @Test
    @DisplayName("第 7 个自然日允许，第 8 个自然日拒绝")
    void checksNaturalDayBoundary() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 23, 10, 30);
        LocalDateTime day7 = LocalDateTime.of(2026, 9, 30, 22, 0);
        LocalDateTime day8 = LocalDateTime.of(2026, 10, 1, 0, 0);

        assertDoesNotThrow(
                () -> TimeRules.ensureStartBookable(day7, now));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> TimeRules.ensureStartBookable(day8, now));
        assertEquals(ResultCode.BOOKING_WINDOW_EXCEEDED, ex.getResultCode());
    }
}
