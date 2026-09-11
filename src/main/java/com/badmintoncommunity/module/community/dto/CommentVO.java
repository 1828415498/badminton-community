package com.badmintoncommunity.module.community.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论项（api.md §7.7）。
 *
 * <p>字段名用 nickname / avatarUrl（不加 author_ 前缀）—— 与 api.md 的响应约定一致：
 * 评论列表里当前只有"评论者"一种身份，不存在歧义。</p>
 */
@Data
public class CommentVO {

    private Long id;
    private Long userId;

    private String nickname;
    private String avatarUrl;

    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
