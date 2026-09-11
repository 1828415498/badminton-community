package com.badmintoncommunity.module.community.service;

import com.badmintoncommunity.common.BusinessException;
import com.badmintoncommunity.common.PageResult;
import com.badmintoncommunity.common.ResultCode;
import com.badmintoncommunity.module.community.dto.CreatePostRequest;
import com.badmintoncommunity.module.community.dto.LikeResultVO;
import com.badmintoncommunity.module.community.dto.PostItemVO;
import com.badmintoncommunity.module.community.dto.PostVO;
import com.badmintoncommunity.module.community.entity.Post;
import com.badmintoncommunity.module.community.mapper.PostLikeMapper;
import com.badmintoncommunity.module.community.mapper.PostMapper;
import com.badmintoncommunity.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 帖子业务（api.md §7.1–§7.5、§7.8–§7.9）。
 *
 * <h2>这个 Service 在做的业务（3 句话版）</h2>
 * <ol>
 *   <li>发帖、看帖、删自己的帖；管理员能删任何人的帖（违规内容处理）；</li>
 *   <li>点赞和取消点赞都是<b>幂等</b>的：重复点赞、重复取消都不会报错、不会产生脏数据；</li>
 *   <li>删帖是<b>物理删除</b>，评论和点赞由数据库外键级联清理，应用层不手动删子表。</li>
 * </ol>
 *
 * <h2>与预约/活动模块的根本差别</h2>
 * <p>这里<b>没有资源竞争</b>——帖子和点赞谁写谁的，不需要 {@code SELECT ... FOR UPDATE}。
 * 唯一的并发点是"同一用户重复点赞"，靠复合主键 + {@code ON DUPLICATE KEY UPDATE} 兜住，
 * 比加锁更简单也更可靠。</p>
 */
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostMapper postMapper;
    private final PostLikeMapper postLikeMapper;

    // ==================== 写 ====================

    /** 创建帖子（api.md §7.3）。 */
    @Transactional
    public PostVO create(Long userId, CreatePostRequest req) {
        Post post = new Post();
        post.setUserId(userId);
        post.setTitle(req.getTitle());
        post.setContent(req.getContent());
        postMapper.insert(post);
        // 刚创建的帖子必然未被自己点赞，liked 查出来就是 false
        return postMapper.findVOById(post.getId(), userId);
    }

    /**
     * 删除自己的帖子（api.md §7.4）。
     * 删除是物理删除；评论与点赞随外键 `ON DELETE CASCADE` 一并消失。
     */
    @Transactional
    public void deleteByAuthor(Long userId, Long postId) {
        Post post = postMapper.findById(postId);
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }
        if (!userId.equals(post.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能删除自己发布的帖子");
        }
        postMapper.deleteById(postId);
    }

    /** 管理员删除任意帖子（api.md §7.5）：处理违规内容，不受作者归属限制。 */
    @Transactional
    public void deleteByAdmin(LoginUser me, Long postId) {
        requireAdmin(me);
        Post post = postMapper.findById(postId);
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }
        postMapper.deleteById(postId);
    }

    // ==================== 读 ====================

    /** 帖子列表（api.md §7.1）：匿名可访问，按发布时间倒序。 */
    @Transactional(readOnly = true)
    public PageResult<PostItemVO> list(int page, int pageSize) {
        long total = postMapper.count();
        List<PostItemVO> list = postMapper.list((page - 1) * pageSize, pageSize);
        return new PageResult<>(list, total, page, pageSize);
    }

    /**
     * 帖子详情（api.md §7.2）：匿名可访问，但 {@code liked} 只有登录后才有意义。
     *
     * <p>未登录时把 userId 传 null 即可 —— SQL 里 {@code pl.user_id = NULL} 恒不成立，
     * liked 自然是 false，不需要额外分支。</p>
     */
    @Transactional(readOnly = true)
    public PostVO detail(LoginUser me, Long postId) {
        Long currentUserId = me == null ? null : me.userId();
        PostVO vo = postMapper.findVOById(postId, currentUserId);
        if (vo == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }
        return vo;
    }

    // ==================== 点赞 ====================

    /**
     * 点赞（api.md §7.8，business-flows 动作 15）：幂等。
     * 已点赞再点一次 → 静默成功，不产生第二行、不报错。
     */
    @Transactional
    public LikeResultVO like(Long userId, Long postId) {
        // 先确认帖子存在：否则外键失败会抛出难懂的数据库异常
        if (postMapper.findById(postId) == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }
        postLikeMapper.insert(postId, userId);
        return new LikeResultVO(true);
    }

    /** 取消点赞（api.md §7.9）：同样幂等 —— 本来就没点赞，取消也算成功。 */
    @Transactional
    public LikeResultVO unlike(Long userId, Long postId) {
        if (postMapper.findById(postId) == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }
        postLikeMapper.delete(postId, userId);
        return new LikeResultVO(false);
    }

    // ==================== 内部工具 ====================

    private void requireAdmin(LoginUser me) {
        if (me == null || me.role() == null || me.role() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }
}
