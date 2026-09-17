package com.badmintoncommunity.module.activity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动列表项（api.md §5.2 / §5.4 / §5.6 共用）。
 *
 * <p>列表刻意不含 description：列表页只需要"办什么、什么时候、几个人、什么状态"，
 * 描述等重字段留给详情接口，避免一页拉取大量文本。</p>
 */
@Data
public class ActivityItemVO {

    private Long id;
    private String title;

    private Long creatorId;
    private String creatorNickname;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private Integer maxMembers;

    /** 当前有效报名数（实时计算，用于推导"是否满员"，接口不下发 full 字段） */
    private Integer joinedCount;

    /** 0=待审核，1=已发布，2=已驳回，3=已取消，4=已结束 */
    private Integer status;

    /**
     * 占用的球场 id 列表（与详情接口的 {@code court_ids} 同义）。
     *
     * <p>列表接口在 Service 层按 activityIds <b>批量查一次</b> reservation 填充，
     * 不写进 SQL join —— 一个活动可占多块场，join 出来会把行数打散成笛卡尔积，
     * 再在 Java 里聚合反而更脏。批量查是 1 次查询，不是 N+1。</p>
     *
     * <p>为什么列表也要这个字段：用户扫一行球局时问的是"这场球我能不能去"，
     * "在哪片场打"是决策信息之一。只有详情页才有场地信息，意味着用户必须
     * 逐个点进去才能比较，首页无法承担"帮你选一场"的职责。</p>
     */
    private List<Long> courtIds;
}
