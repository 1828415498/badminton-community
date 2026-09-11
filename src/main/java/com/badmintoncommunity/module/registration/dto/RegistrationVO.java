package com.badmintoncommunity.module.registration.dto;

import lombok.Data;

/**
 * 报名操作响应（api.md §6.1 报名 / §6.2 取消报名）。
 *
 * <p>只回报名行本身的最小信息：id、归属、状态。前端据此判断"我是否已报名"。</p>
 */
@Data
public class RegistrationVO {

    private Long id;
    private Long userId;
    private Long activityId;

    /** 1=已报名，2=已取消 */
    private Integer status;
}
