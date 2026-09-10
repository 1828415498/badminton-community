package com.badmintoncommunity.module.reservation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建个人球场预约请求（api.md §4.1）。
 *
 * <p>接口只收"哪块场、从几点到几点"。窗口/冲突/重叠上限等规则全部由 Service 判定，
 * 前端不做任何限制性计算。</p>
 */
@Data
public class CreateReservationRequest {

    @NotNull(message = "court_id 不能为空")
    private Long courtId;

    /** 开始时间（整点；须晚于当前时刻，7 天窗口内） */
    @NotNull(message = "start_time 不能为空")
    private LocalDateTime startTime;

    /** 结束时间（整点；须晚于 start_time，且与 start 同一天） */
    @NotNull(message = "end_time 不能为空")
    private LocalDateTime endTime;
}
