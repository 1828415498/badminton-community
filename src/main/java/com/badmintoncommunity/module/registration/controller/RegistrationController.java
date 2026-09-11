package com.badmintoncommunity.module.registration.controller;

import com.badmintoncommunity.common.PageResult;
import com.badmintoncommunity.common.Result;
import com.badmintoncommunity.module.registration.dto.ActivityMemberVO;
import com.badmintoncommunity.module.registration.dto.MyRegistrationVO;
import com.badmintoncommunity.module.registration.dto.RegistrationVO;
import com.badmintoncommunity.module.registration.service.RegistrationService;
import com.badmintoncommunity.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 活动报名接口（api.md §6.1–§6.4）。
 *
 * <p>路径人为分两类：</p>
 * <ul>
 *   <li>{@code /api/activities/{id}/registration(s)} —— 报名是"活动的子资源"，挂在活动路径下；</li>
 *   <li>{@code /api/registrations/mine} —— "我的报名"是跨活动的个人视图，单独一条路径更自然。</li>
 * </ul>
 * <p>全部要求登录：报名/取消作用于自己的报名行，"我参加的活动"只返回自己的。</p>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    /** 报名活动（POST /api/activities/{activityId}/registration）：幂等，满员则拒绝。 */
    @PostMapping("/activities/{activityId}/registration")
    public Result<RegistrationVO> register(@PathVariable Long activityId) {
        return Result.ok(registrationService.register(UserContext.requireUserId(), activityId));
    }

    /** 取消报名（DELETE /api/activities/{activityId}/registration）：释放名额。 */
    @DeleteMapping("/activities/{activityId}/registration")
    public Result<RegistrationVO> cancel(@PathVariable Long activityId) {
        return Result.ok(registrationService.cancelRegistration(
                UserContext.requireUserId(), activityId));
    }

    /** 活动成员列表（GET /api/activities/{activityId}/registrations）。 */
    @GetMapping("/activities/{activityId}/registrations")
    public Result<PageResult<ActivityMemberVO>> members(
            @PathVariable Long activityId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return Result.ok(registrationService.listMembers(UserContext.get(), activityId,
                Math.max(page, 1), Math.min(Math.max(pageSize, 1), 50)));
    }

    /** 我参加的活动（GET /api/registrations/mine?status=&page=&page_size=）。 */
    @GetMapping("/registrations/mine")
    public Result<PageResult<MyRegistrationVO>> mine(
            @RequestParam(required = false) Integer status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return Result.ok(registrationService.listMine(UserContext.requireUserId(), status,
                Math.max(page, 1), Math.min(Math.max(pageSize, 1), 50)));
    }
}
