package com.badmintoncommunity.module.activity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

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
}
