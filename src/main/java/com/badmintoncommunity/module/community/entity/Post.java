package com.badmintoncommunity.module.community.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帖子实体（表 post，database.md §4.7）。
 *
 * <p>社区内容的主体。与 activity 最大的不同：<b>post 支持物理删除</b>，
 * 而 comment / post_like 通过外键 `ON DELETE CASCADE` 随之自动清理。</p>
 */
@Data
public class Post {

    private Long id;

    /** 作者，外键 → user.id */
    private Long userId;

    private String title;

    /** 正文（TEXT 类型，长度不受 VARCHAR 限制） */
    private String content;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
