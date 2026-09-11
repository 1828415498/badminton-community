package com.badmintoncommunity.module.activity.controller;

import com.badmintoncommunity.common.PageResult;
import com.badmintoncommunity.common.Result;
import com.badmintoncommunity.module.activity.dto.ActivityItemVO;
import com.badmintoncommunity.module.activity.dto.ActivityRejectRequest;
import com.badmintoncommunity.module.activity.dto.ActivityVO;
import com.badmintoncommunity.module.activity.service.ActivityService;
import com.badmintoncommunity.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 活动接口 · 管理员侧（api.md §5.6–§5.9）。
 *
 * <p>这组接口全部要求登录 + 管理员角色。拦截器只保证"已登录"；
 * "是不是管理员"由 ActivityService 的 requireAdmin 判定
 * （认证与授权分离，ADR-0007）。</p>
 */
@RestController
@RequestMapping("/api/admin/activities")
@RequiredArgsConstructor
public class AdminActivityController {

    private final ActivityService activityService;

    /** 待审核队列（GET /api/admin/activities/pending）：先创建先审。 */
    @GetMapping("/pending")
    public Result<PageResult<ActivityItemVO>> pending(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return Result.ok(activityService.listPending(UserContext.get(),
                Math.max(page, 1), Math.min(Math.max(pageSize, 1), 50)));
    }

    /** 审核通过（POST /api/admin/activities/{id}/approve）：场地与报名保持不变。 */
    @PostMapping("/{id}/approve")
    public Result<ActivityVO> approve(@PathVariable Long id) {
        return Result.ok(activityService.approve(UserContext.get(), id));
    }

    /** 审核驳回（POST /api/admin/activities/{id}/reject）：释放场地 + 作废报名。 */
    @PostMapping("/{id}/reject")
    public Result<ActivityVO> reject(@PathVariable Long id,
                                     @Valid @RequestBody ActivityRejectRequest req) {
        return Result.ok(activityService.reject(UserContext.get(), id, req.getRejectReason()));
    }

    /** 管理员取消活动（POST /api/admin/activities/{id}/cancel）：不受"未开始"限制。 */
    @PostMapping("/{id}/cancel")
    public Result<ActivityVO> cancel(@PathVariable Long id) {
        return Result.ok(activityService.cancelByAdmin(UserContext.get(), id));
    }
}
