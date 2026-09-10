package com.badmintoncommunity.module.reservation.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 场地占用账本实体（表 reservation，database.md §4.5）。
 *
 * <p>一条记录 = 球场 × 时段 × 归属。归属二选一：个人行 user_id 有值、活动行 activity_id 有值。</p>
 */
@Data
public class Reservation {

    private Long id;
    private Long courtId;

    /** 个人预约有值；活动占场必须为空 */
    private Long userId;

    /** 活动占场有值；个人预约必须为空 */
    private Long activityId;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /** 1=有效，2=已取消 */
    private Integer status;

    private LocalDateTime cancelTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
