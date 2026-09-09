package com.badmintoncommunity.module.reservation.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 球场某时间范围的「占用事实」响应（api.md §3.3）。
 * kind 由归属推导（不落库）：user_id 非空=PERSONAL，否则=ACTIVITY。
 */
@Data
public class OccupancySlot {

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String kind;
    private Long activityId;
}
