package com.badmintoncommunity.module.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人信息请求（api.md §2.4）。至少传一项；username/role 不可修改。
 */
@Data
public class UserUpdateRequest {

    @Size(max = 64, message = "昵称长度不能超过 64")
    private String nickname;

    @Size(max = 255, message = "头像地址过长")
    private String avatarUrl;
}
