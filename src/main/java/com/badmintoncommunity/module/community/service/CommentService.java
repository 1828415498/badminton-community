package com.badmintoncommunity.module.community.service;

import com.badmintoncommunity.common.BusinessException;
import com.badmintoncommunity.common.PageResult;
import com.badmintoncommunity.common.ResultCode;
import com.badmintoncommunity.module.community.dto.CommentVO;
import com.badmintoncommunity.module.community.dto.CreateCommentRequest;
import com.badmintoncommunity.module.community.entity.Comment;
import com.badmintoncommunity.module.community.mapper.CommentMapper;
import com.badmintoncommunity.module.community.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 评论业务（api.md §7.6–§7.7）。
 *
 * <h2>这个 Service 在做的业务（3 句话版）</h2>
 * <ol>
 *   <li>给存在的帖子发表评论，评论只有一级、不能回复、不能编辑；</li>
 *   <li>任何人都能看某帖的评论列表（匿名也行），按发表时间从早到晚排；</li>
 *   <li>评论没有"删除"这个动作 —— 它只随所属帖子被删除而级联消失。</li>
 * </ol>
 *
 * <p>依赖 {@link PostMapper} 只是为了校验帖子存在：否则外键约束会抛出一条
 * 难以理解的数据库异常，而用户需要的是明确的"帖子不存在"。</p>
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;
    private final PostMapper postMapper;

    /** 发表评论（api.md §7.6）。 */
    @Transactional
    public CommentVO create(Long userId, Long postId, CreateCommentRequest req) {
        requirePostExists(postId);
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setContent(req.getContent());
        commentMapper.insert(comment);
        // 重新查出带昵称/头像的视图，直接回显给前端
        return commentMapper.findVOById(comment.getId());
    }

    /** 某帖的评论列表（api.md §7.7）：匿名可访问，按时间升序。 */
    @Transactional(readOnly = true)
    public PageResult<CommentVO> list(Long postId, int page, int pageSize) {
        requirePostExists(postId);
        long total = commentMapper.countByPost(postId);
        List<CommentVO> list = commentMapper.listByPost(postId, (page - 1) * pageSize, pageSize);
        return new PageResult<>(list, total, page, pageSize);
    }

    private void requirePostExists(Long postId) {
        if (postMapper.findById(postId) == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }
    }
}
