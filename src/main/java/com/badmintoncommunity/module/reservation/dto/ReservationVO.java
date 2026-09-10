package com.badmintoncommunity.module.reservation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预约信息响应（api.md §4.2 / §4.3）。court_name 由查询时 join 得到。
 */
@Data
public class ReservationVO {

    private Long id;
    private Long courtId;
    private String courtName;

    /** 个人预约显示本人 id；活动占场行为空（不经个人接口暴露） */
    private Long userId;
    private Long activityId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 1=有效，2=已取消 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cancelTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
