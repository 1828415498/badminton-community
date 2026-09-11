package com.badmintoncommunity.module.activity.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动实体（表 activity，database.md §4.4）。
 *
 * <p>只存"组织信息 + 时间区间 + 人数上限 + 生命周期状态"。
 * 活动关联的球场<b>不存字段</b>：由 reservation 中 activity_id = 本活动的行反查得到
 * （一个活动占 N 块场 = N 条 reservation 行，见 database.md §4.4 注释）。</p>
 */
@Data
public class Activity {

    private Long id;

    /** 创建者（组织者），外键 → user.id */
    private Long creatorId;

    private String title;
    private String description;

    /** 活动时间区间（整点、不跨天，database.md §2 前提 2） */
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /** 报名人数上限（≥1；创建者自动占 1 个名额） */
    private Integer maxMembers;

    /**
     * 生命周期状态：0=待审核，1=已发布，2=已驳回，3=已取消，4=已结束。
     * 状态 4 由定时任务把「已发布且已过期」的活动扫出来置位（Q1=A）。
     */
    private Integer status;

    /** 审核人（管理员），外键 → user.id */
    private Long reviewerId;
    private LocalDateTime reviewTime;

    /** 驳回原因，展示给创建者 */
    private String rejectReason;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
