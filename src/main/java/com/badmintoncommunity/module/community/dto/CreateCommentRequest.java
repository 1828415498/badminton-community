package com.badmintoncommunity.module.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发表评论请求（api.md §7.6）。
 *
 * <p>评论只做一级，因此请求体里只有内容 —— 没有 parent_id、没有回复目标。</p>
 */
@Data
public class CreateCommentRequest {

    @NotBlank(message = "content 不能为空")
    @Size(max = 1000, message = "content 长度不能超过 1000")
    private String content;
}
