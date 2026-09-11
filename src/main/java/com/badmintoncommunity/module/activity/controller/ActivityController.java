package com.badmintoncommunity.module.activity.controller;

import com.badmintoncommunity.common.PageResult;
import com.badmintoncommunity.common.Result;
import com.badmintoncommunity.module.activity.dto.ActivityItemVO;
import com.badmintoncommunity.module.activity.dto.ActivityVO;
import com.badmintoncommunity.module.activity.dto.CreateActivityRequest;
import com.badmintoncommunity.module.activity.service.ActivityService;
import com.badmintoncommunity.security.LoginUser;
import com.badmintoncommunity.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 活动接口 · 用户侧（api.md §5.1–§5.5）。
 *
 * <p>注意列表与详情是<b>可匿名访问</b>的（浏览公开活动不需要登录），
 * 由 WebConfig 白名单 + AuthInterceptor 的"可选认证"放行：
 * 带了 token 就解析出身份（用于判断"我是不是创建者"），没带就当匿名继续。
 * 因此这两个方法用 {@code UserContext.get()}（可能为 null），
 * 其余方法用 {@code requireUserId()}（一定不为 null）。</p>
 */
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    /** 创建活动（POST /api/activities）：创建即锁定所选球场并自动报名创建者。 */
    @PostMapping
    public Result<ActivityVO> create(@Valid @RequestBody CreateActivityRequest req) {
        return Result.ok(activityService.create(UserContext.requireUserId(), req));
    }

    /** 活动列表（GET /api/activities?status=&start_from=&start_to=&page=&page_size=）：匿名可浏览。 */
    @GetMapping
    public Result<PageResult<ActivityItemVO>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(name = "start_from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startFrom,
            @RequestParam(name = "start_to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTo,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        LoginUser me = UserContext.get();
        return Result.ok(activityService.listPublic(status, startFrom, startTo, me,
                Math.max(page, 1), clampSize(pageSize)));
    }

    /**
     * 我创建的活动（GET /api/activities/mine）。
     *
     * <p>必须声明在 {@code /{id}} 之前语义上更"具体"——Spring MVC 会优先匹配字面量路径，
     * 所以 {@code /mine} 不会被 {@code /{id}} 抢走。</p>
     */
    @GetMapping("/mine")
    public Result<PageResult<ActivityItemVO>> mine(
            @RequestParam(required = false) Integer status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return Result.ok(activityService.listMine(UserContext.requireUserId(), status,
                Math.max(page, 1), clampSize(pageSize)));
    }

    /** 活动详情（GET /api/activities/{id}）：公开状态人人可看，其余仅创建者与管理员。 */
    @GetMapping("/{id}")
    public Result<ActivityVO> detail(@PathVariable Long id) {
        return Result.ok(activityService.detail(UserContext.get(), id));
    }

    /** 创建者取消活动（POST /api/activities/{id}/cancel）：仅限活动开始前。 */
    @PostMapping("/{id}/cancel")
    public Result<ActivityVO> cancel(@PathVariable Long id) {
        return Result.ok(activityService.cancelByCreator(UserContext.requireUserId(), id));
    }

    /** 分页大小兜底：至少 1、至多 50（与其他模块一致） */
    private int clampSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), 50);
    }
}
