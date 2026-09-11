package com.badmintoncommunity.module.registration.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 「我参加的活动」查询的平铺行（MyBatis 直接映射用，不对外）。
 *
 * <p>SQL 一次 join 出"报名 + 活动 + 创建者"三张表的信息，得到一张宽行；
 * Service 再把它组装成 api.md §6.4 要求的嵌套结构。用平铺行是为了避免
 * 在 MyBatis 里写复杂的嵌套 resultMap —— 组装逻辑放在 Java 里更直观。</p>
 */
@Data
public class MyRegistrationRow {

    private Long registrationId;
    private Integer registrationStatus;
    private LocalDateTime registrationCreateTime;

    private Long activityId;
    private String title;
    private Long creatorId;
    private String creatorNickname;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxMembers;
    private Integer activityStatus;
    private Integer joinedCount;
}
