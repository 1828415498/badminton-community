package com.badmintoncommunity.module.community.controller;

import com.badmintoncommunity.common.PageResult;
import com.badmintoncommunity.common.Result;
import com.badmintoncommunity.module.community.dto.CommentVO;
import com.badmintoncommunity.module.community.dto.CreateCommentRequest;
import com.badmintoncommunity.module.community.dto.CreatePostRequest;
import com.badmintoncommunity.module.community.dto.LikeResultVO;
import com.badmintoncommunity.module.community.dto.PostItemVO;
import com.badmintoncommunity.module.community.dto.PostVO;
import com.badmintoncommunity.module.community.service.CommentService;
import com.badmintoncommunity.module.community.service.PostService;
import com.badmintoncommunity.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区接口（api.md §7.1–§7.4、§7.6–§7.9）。
 *
 * <p>这里混着两种访问级别，靠方法区分而不是靠路径：</p>
 * <ul>
 *   <li><b>可匿名</b>：GET 列表、GET 详情、GET 评论列表 —— 由 AuthInterceptor 的
 *       "可选认证"放行；带了 token 就解析身份（详情要判断 {@code liked}）。</li>
 *   <li><b>必须登录</b>：发帖、删帖、评论、点赞、取消点赞 —— 都是写操作，
 *       通过 {@code UserContext.requireUserId()} 取当前用户。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final CommentService commentService;

    /** 帖子列表（GET /api/posts?page=&page_size=）：匿名可浏览。 */
    @GetMapping
    public Result<PageResult<PostItemVO>> list(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return Result.ok(postService.list(Math.max(page, 1), clampSize(pageSize)));
    }

    /** 创建帖子（POST /api/posts）。 */
    @PostMapping
    public Result<PostVO> create(@Valid @RequestBody CreatePostRequest req) {
        return Result.ok(postService.create(UserContext.requireUserId(), req));
    }

    /** 帖子详情（GET /api/posts/{id}）：匿名可看，登录后 liked 才有意义。 */
    @GetMapping("/{id}")
    public Result<PostVO> detail(@PathVariable Long id) {
        return Result.ok(postService.detail(UserContext.get(), id));
    }

    /** 删除自己的帖子（DELETE /api/posts/{id}）：物理删除，评论与点赞级联清理。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        postService.deleteByAuthor(UserContext.requireUserId(), id);
        return Result.ok(null);
    }

    /** 发表评论（POST /api/posts/{id}/comments）。 */
    @PostMapping("/{id}/comments")
    public Result<CommentVO> comment(@PathVariable Long id,
                                     @Valid @RequestBody CreateCommentRequest req) {
        return Result.ok(commentService.create(UserContext.requireUserId(), id, req));
    }

    /** 评论列表（GET /api/posts/{id}/comments）：匿名可浏览，按时间升序。 */
    @GetMapping("/{id}/comments")
    public Result<PageResult<CommentVO>> comments(
            @PathVariable Long id,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return Result.ok(commentService.list(id, Math.max(page, 1), clampSize(pageSize)));
    }

    /** 点赞（POST /api/posts/{id}/like）：幂等。 */
    @PostMapping("/{id}/like")
    public Result<LikeResultVO> like(@PathVariable Long id) {
        return Result.ok(postService.like(UserContext.requireUserId(), id));
    }

    /** 取消点赞（DELETE /api/posts/{id}/like）：幂等。 */
    @DeleteMapping("/{id}/like")
    public Result<LikeResultVO> unlike(@PathVariable Long id) {
        return Result.ok(postService.unlike(UserContext.requireUserId(), id));
    }

    /** 分页大小兜底：至少 1、至多 50（与其他模块一致） */
    private int clampSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), 50);
    }
}
