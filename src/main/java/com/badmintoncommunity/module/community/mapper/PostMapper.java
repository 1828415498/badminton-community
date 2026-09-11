package com.badmintoncommunity.module.community.mapper;

import com.badmintoncommunity.module.community.dto.PostItemVO;
import com.badmintoncommunity.module.community.dto.PostVO;
import com.badmintoncommunity.module.community.entity.Post;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 帖子（post）数据访问。
 *
 * <h2>两条约定</h2>
 * <ol>
 *   <li><b>like_count / comment_count 永远是实时子查询</b>，不落冗余列 ——
 *       与活动报名数同一思路，避免"计数与明细对不上"。</li>
 *   <li><b>liked 用 EXISTS 子查询</b>：未登录时传 {@code null}，条件
 *       {@code pl.user_id = NULL} 恒为 UNKNOWN，EXISTS 自然为 false ——
 *       无需在 Service 里写 if-else 分支。</li>
 * </ol>
 */
@Mapper
public interface PostMapper {

    // ---------- 写入 ----------

    @Insert("INSERT INTO post (user_id, title, content) VALUES (#{userId}, #{title}, #{content})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Post post);

    /**
     * 物理删除帖子。评论与点赞由数据库外键 `ON DELETE CASCADE` 自动清理 ——
     * 应用层<b>不需要</b>也不应该手动去删子表，否则一旦漏删就会留下孤儿数据。
     */
    @Delete("DELETE FROM post WHERE id = #{id}")
    int deleteById(Long id);

    // ---------- 单条读取 ----------

    @Select("SELECT id, user_id, title, content, create_time, update_time "
            + "FROM post WHERE id = #{id}")
    Post findById(Long id);

    /**
     * 帖子详情（api.md §7.2）。
     *
     * @param currentUserId 当前登录用户 id；<b>匿名访问时传 null</b>，liked 会是 false
     */
    @Select("SELECT p.id, p.user_id, u.nickname AS author_nickname, "
            + "u.avatar_url AS author_avatar_url, p.title, p.content, p.create_time, "
            + "(SELECT COUNT(*) FROM post_like pl WHERE pl.post_id = p.id) AS like_count, "
            + "(SELECT COUNT(*) FROM comment c WHERE c.post_id = p.id) AS comment_count, "
            + "EXISTS(SELECT 1 FROM post_like pl2 "
            + "       WHERE pl2.post_id = p.id AND pl2.user_id = #{currentUserId}) AS liked "
            + "FROM post p JOIN `user` u ON u.id = p.user_id "
            + "WHERE p.id = #{id}")
    PostVO findVOById(@Param("id") Long id, @Param("currentUserId") Long currentUserId);

    // ---------- 列表 ----------

    /** 帖子列表（api.md §7.1）：按发布时间倒序。 */
    @Select("SELECT p.id, p.user_id, u.nickname AS author_nickname, "
            + "u.avatar_url AS author_avatar_url, p.title, p.content, p.create_time, "
            + "(SELECT COUNT(*) FROM post_like pl WHERE pl.post_id = p.id) AS like_count, "
            + "(SELECT COUNT(*) FROM comment c WHERE c.post_id = p.id) AS comment_count "
            + "FROM post p JOIN `user` u ON u.id = p.user_id "
            + "ORDER BY p.create_time DESC "
            + "LIMIT #{offset}, #{pageSize}")
    List<PostItemVO> list(@Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("SELECT COUNT(*) FROM post")
    long count();
}
