package com.badmintoncommunity.module.community.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帖子详情（api.md §7.2）。比列表项多一个 {@code liked}。
 *
 * <p>{@code liked} 是"当前登录用户有没有点过赞"。未登录时恒为 false ——
 * 实现上靠"未登录则 userId 传 null"天然达成，不需要额外分支。</p>
 */
@Data
public class PostVO {

    private Long id;
    private Long userId;

    private String authorNickname;
    private String authorAvatarUrl;

    private String title;
    private String content;

    private Integer likeCount;
    private Integer commentCount;

    /** 当前登录用户是否已点赞（未登录 = false） */
    private Boolean liked;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
