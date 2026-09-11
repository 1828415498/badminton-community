package com.badmintoncommunity.module.activity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动详情响应（api.md §5.3）。
 *
 * <p>比列表项多了 description / 审核痕迹 / court_ids。
 * joined_count 与 court_ids 都由查询实时得到，不落冗余字段。</p>
 */
@Data
public class ActivityVO {

    private Long id;
    private Long creatorId;
    private String creatorNickname;

    private String title;
    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private Integer maxMembers;

    /** 当前有效报名数（status=1 的 registration 行数，实时计算） */
    private Integer joinedCount;

    /** 0=待审核，1=已发布，2=已驳回，3=已取消，4=已结束 */
    private Integer status;

    private Long reviewerId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;
    private String rejectReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 该活动占用的球场 id 列表（由 reservation 反查，按 court_id 升序） */
    private List<Long> courtIds;
}
