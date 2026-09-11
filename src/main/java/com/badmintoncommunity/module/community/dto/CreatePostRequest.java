package com.badmintoncommunity.module.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建帖子请求（api.md §7.3）。
 */
@Data
public class CreatePostRequest {

    @NotBlank(message = "title 不能为空")
    @Size(max = 200, message = "title 长度不能超过 200")
    private String title;

    @NotBlank(message = "content 不能为空")
    private String content;
}
