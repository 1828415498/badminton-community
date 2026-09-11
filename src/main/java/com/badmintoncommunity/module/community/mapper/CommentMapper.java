package com.badmintoncommunity.module.community.mapper;

import com.badmintoncommunity.module.community.dto.CommentVO;
import com.badmintoncommunity.module.community.entity.Comment;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 评论（comment）数据访问。
 *
 * <p>没有 update / delete 方法 —— 评论不支持编辑，也不单独删除，
 * 只在所属帖子被删除时随外键级联消失。这正是"接口只暴露必要能力"的体现。</p>
 */
@Mapper
public interface CommentMapper {

    @Insert("INSERT INTO comment (post_id, user_id, content) "
            + "VALUES (#{postId}, #{userId}, #{content})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Comment comment);

    @Select("SELECT id, post_id, user_id, content, create_time FROM comment WHERE id = #{id}")
    Comment findById(Long id);

    /** 单条评论的对外视图（含评论者昵称与头像），供"发表评论"接口直接回显。 */
    @Select("SELECT c.id, c.user_id, u.nickname, u.avatar_url, c.content, c.create_time "
            + "FROM comment c JOIN `user` u ON u.id = c.user_id WHERE c.id = #{id}")
    CommentVO findVOById(Long id);

    /** 某帖子的评论列表（api.md §7.7）：按发表时间<b>升序</b>（先评的在上面）。 */
    @Select("SELECT c.id, c.user_id, u.nickname, u.avatar_url, c.content, c.create_time "
            + "FROM comment c JOIN `user` u ON u.id = c.user_id "
            + "WHERE c.post_id = #{postId} "
            + "ORDER BY c.create_time ASC "
            + "LIMIT #{offset}, #{pageSize}")
    List<CommentVO> listByPost(@Param("postId") Long postId,
                               @Param("offset") int offset,
                               @Param("pageSize") int pageSize);

    @Select("SELECT COUNT(*) FROM comment WHERE post_id = #{postId}")
    long countByPost(Long postId);
}
