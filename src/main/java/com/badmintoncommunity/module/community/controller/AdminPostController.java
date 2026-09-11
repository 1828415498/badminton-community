package com.badmintoncommunity.module.community.controller;

import com.badmintoncommunity.common.Result;
import com.badmintoncommunity.module.community.service.PostService;
import com.badmintoncommunity.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区接口 · 管理员侧（api.md §7.5）。
 *
 * <p>管理员删帖 = 处理违规内容（requirements）。与「删除自己的帖子」的区别只有两点：
 * 不受作者归属限制、由 Service 校验管理员角色。</p>
 */
@RestController
@RequestMapping("/api/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

    private final PostService postService;

    /** 删除任意帖子（DELETE /api/admin/posts/{id}）：级联清理评论与点赞。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        postService.deleteByAdmin(UserContext.get(), id);
        return Result.ok(null);
    }
}
