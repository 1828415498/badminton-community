package com.badmintoncommunity.module.community.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帖子列表项（api.md §7.1）。
 *
 * <p>注意：与活动列表（不含 description）不同，帖子列表<b>带 content</b> ——
 * 社区场景下用户习惯在列表里直接读全文（Q1 决策 A）。</p>
 */
@Data
public class PostItemVO {

    private Long id;
    private Long userId;

    private String authorNickname;
    private String authorAvatarUrl;

    private String title;
    private String content;

    /** 点赞数（post_like 行数，实时统计，不存冗余列） */
    private Integer likeCount;
    /** 评论数（comment 行数，实时统计） */
    private Integer commentCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
