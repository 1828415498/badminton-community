package com.badmintoncommunity.module.community.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论实体（表 comment，database.md §4.8）。
 *
 * <p>只做<b>一级评论</b>：不存 parent_id、不做嵌套回复、不支持编辑。</p>
 *
 * <p>没有 status 字段 —— 评论不支持"删除"这个业务动作，
 * 它只在所属帖子被删除时随外键 `ON DELETE CASCADE` 一起消失。</p>
 */
@Data
public class Comment {

    private Long id;

    /** 所属帖子，外键 → post.id（级联删除） */
    private Long postId;

    /** 评论者，外键 → user.id */
    private Long userId;

    private String content;

    private LocalDateTime createTime;
}
