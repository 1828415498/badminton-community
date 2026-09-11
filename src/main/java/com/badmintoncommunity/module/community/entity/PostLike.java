package com.badmintoncommunity.module.community.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 点赞关系（表 post_like，database.md §4.9）。
 *
 * <p>这张表<b>没有 id 主键</b>，用的是复合主键 `(post_id, user_id)` —— 这本身就是
 * "一个用户对同一帖子只能点一次赞"的数据库级约束，比在 Java 里查一遍再插入可靠得多：
 * 并发重复点赞时，第二条 INSERT 会被主键直接拒绝。</p>
 *
 * <p>没有 status：取消点赞 = 删除这一行（关系型数据，取消就该消失）。</p>
 */
@Data
public class PostLike {

    private Long postId;
    private Long userId;
    private LocalDateTime createTime;
}
