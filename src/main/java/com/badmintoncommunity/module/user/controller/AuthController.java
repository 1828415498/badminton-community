package com.badmintoncommunity.module.user.controller;

import com.badmintoncommunity.common.Result;
import com.badmintoncommunity.module.user.dto.LoginRequest;
import com.badmintoncommunity.module.user.dto.LoginResult;
import com.badmintoncommunity.module.user.dto.RegisterRequest;
import com.badmintoncommunity.module.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（api.md §2.1 注册 / §2.2 登录 / §2.5 退出登录）。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /** 业务入口：Controller 只做"接收参数/返回结果"，不写业务逻辑（api.md §12） */
    private final UserService userService;

    /**
     * 注册（匿名可调用，见 WebConfig 白名单）。
     * 成功只返回 code=0；注册后需要再调 login 获取令牌。
     */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest req) {
        userService.register(req);
        return Result.ok();
    }

    /**
     * 登录（匿名可调用）。成功后返回 user + token；
     * 客户端后续请求在 Header 里带 {@code Authorization: Bearer <token>}。
     */
    @PostMapping("/login")
    public Result<LoginResult> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(userService.login(req));
    }

    /**
     * 退出登录：JWT 无状态，服务端不保存会话，
     * 因此"登出" = 客户端自己丢弃令牌（ADR-0007 / api.md §2.5）。
     * 本接口需要认证（会走拦截器），但只是让调用语义完整。
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok();
    }
}
