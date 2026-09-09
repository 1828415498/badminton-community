package com.badmintoncommunity.module.user.controller;

import com.badmintoncommunity.common.Result;
import com.badmintoncommunity.module.user.dto.UserUpdateRequest;
import com.badmintoncommunity.module.user.dto.UserVO;
import com.badmintoncommunity.module.user.service.UserService;
import com.badmintoncommunity.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人信息接口（api.md §2.3 获取 / §2.4 修改）。
 *
 * <h2>「当前用户」从哪来？</h2>
 * <pre>
 * 请求带 Authorization: Bearer token
 *      ↓ AuthInterceptor（认证拦截器，见 security 包）
 *      ↓ 解析 JWT → 把用户信息放进 ThreadLocal（UserContext）
 *      ↓ 本类里 UserContext.requireUserId()  取回"我是谁"
 * </pre>
 * 这样每个接口都不需要自己解析 token，拿到 userId 直接调 Service。
 */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 获取我的资料（GET /api/users/me） */
    @GetMapping
    public Result<UserVO> getMe() {
        // 先从「请求级上下文」取当前登录用户 id，再交给 Service 查资料
        return Result.ok(userService.getCurrentUser(UserContext.requireUserId()));
    }

    /** 修改我的资料（PUT /api/users/me），只允许 nickname / avatar_url */
    @PutMapping
    public Result<UserVO> updateMe(@Valid @RequestBody UserUpdateRequest req) {
        return Result.ok(userService.updateCurrentUser(UserContext.requireUserId(), req));
    }
}
