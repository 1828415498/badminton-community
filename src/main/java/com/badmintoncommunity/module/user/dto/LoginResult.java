package com.badmintoncommunity.module.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录结果（api.md §2.2）：用户信息 + 登录凭据。
 * 凭据携带方式（Authorization: Bearer token）与 ADR-0007 一致。
 */
@Data
@AllArgsConstructor
public class LoginResult {

    private UserVO user;
    private String token;
}
