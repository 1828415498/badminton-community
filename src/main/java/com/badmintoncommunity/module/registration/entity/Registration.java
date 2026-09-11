package com.badmintoncommunity.module.registration.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动报名实体（表 registration，database.md §4.6）。
 *
 * <p>"活动的参与者名册"：一人一活动一条记录（唯一约束 uk_registration_user_activity 兜底）。
 * 它<b>只管人、不碰场地</b>——场地占用永远在 reservation 里，两者职责不重叠。</p>
 *
 * <p>取消报名是"把 status 置为 2"而不是删行：这样用户重新报名时只需把状态置回 1
 * （不新增第二行），也让"谁曾经报过名"可追溯。</p>
 */
@Data
public class Registration {

    private Long id;

    /** 报名用户，外键 → user.id */
    private Long userId;

    /** 报名活动，外键 → activity.id */
    private Long activityId;

    /** 1=已报名，2=已取消 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
