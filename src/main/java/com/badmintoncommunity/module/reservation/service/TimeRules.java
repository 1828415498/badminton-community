package com.badmintoncommunity.module.reservation.service;

import com.badmintoncommunity.common.BusinessException;
import com.badmintoncommunity.common.ResultCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预约时间规则（整点 / 不跨天 / 7 天窗口）。
 *
 * 抽出为静态工具：个人预约（本模块）与活动锁场（活动模块）走同一套规则
 *（database.md §7 / business-flows §0.3，ADR-0004：允许当天但须晚于当前时刻）
 */
public final class TimeRules {

    private TimeRules() {
    }

    /**
     * 校验时段本身是否合法：整点对齐、end &gt; start、不跨自然日。
     */
    public static void validateInterval(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "start_time / end_time 不能为空");
        }
        if (start.getMinute() != 0 || start.getSecond() != 0
                || end.getMinute() != 0 || end.getSecond() != 0) {
            throw new BusinessException(ResultCode.INVALID_TIME, "预约时间必须以整点为单位");
        }
        if (!end.isAfter(start)) {
            throw new BusinessException(ResultCode.INVALID_TIME, "end_time 必须晚于 start_time");
        }
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            throw new BusinessException(ResultCode.INVALID_TIME, "单个时段不允许跨自然日");
        }
    }

    /**
     * 校验开始时间是否落在可预约窗口内（ADR-0004）：
     * 1) 允许当天，但开始时间必须晚于当前时刻（不能约已经开始/已过去的时段）；
     * 2) 最远可约到「今天 + 7 个自然日」。
     */
    public static void ensureStartBookable(LocalDateTime start, LocalDateTime now) {
        if (!start.isAfter(now)) {
            throw new BusinessException(ResultCode.BOOKING_WINDOW_EXCEEDED,
                    "只能预约尚未开始的时段（允许当天，但须晚于当前时刻）");
        }
        LocalDate latestDate = now.toLocalDate().plusDays(7);
        if (start.toLocalDate().isAfter(latestDate)) {
            throw new BusinessException(ResultCode.BOOKING_WINDOW_EXCEEDED,
                    "最多可预约未来 7 个自然日内的场地");
        }
    }
}
