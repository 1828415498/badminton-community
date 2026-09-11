package com.badmintoncommunity.module.community.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 点赞关系（post_like）数据访问。
 *
 * <h2>幂等怎么做：让数据库自己吞掉重复</h2>
 * <p>"先查有没有点过赞，没有再插"在并发下是不可靠的（两个请求可能同时查到"没赞过"）。
 * 真正的防线是复合主键 {@code (post_id, user_id)}，而这里用
 * {@code ON DUPLICATE KEY UPDATE post_id = post_id}（一个不改任何值的空更新）
 * 把"重复点赞"变成<b>影响 0 行的静默成功</b>：</p>
 * <ul>
 *   <li>不抛异常 → 不需要在 Service 里 try/catch；</li>
 *   <li>这一点很关键：在 {@code @Transactional} 方法里捕获数据库异常后继续执行，
 *       事务往往已被标记为 rollback-only，会在提交时炸出 UnexpectedRollbackException。
 *       让 SQL 自己幂等，从根上避开这个坑。</li>
 *   <li>相比 {@code INSERT IGNORE}，它只吞"主键/唯一键冲突"，外键失败等真错误仍会正常抛出。</li>
 * </ul>
 */
@Mapper
public interface PostLikeMapper {

    /**
     * 点赞（幂等）。
     *
     * @return 1=本次新建了点赞关系；0=本来就已点赞（静默成功，绝不抛异常）
     */
    @Insert("INSERT INTO post_like (post_id, user_id) VALUES (#{postId}, #{userId}) "
            + "ON DUPLICATE KEY UPDATE post_id = post_id")
    int insert(@Param("postId") Long postId, @Param("userId") Long userId);

    /**
     * 取消点赞（幂等）：删除关系行，不存在时影响 0 行，同样视为成功。
     */
    @Delete("DELETE FROM post_like WHERE post_id = #{postId} AND user_id = #{userId}")
    int delete(@Param("postId") Long postId, @Param("userId") Long userId);

    /** 某用户是否已点赞该帖（返回 0/1）。 */
    @Select("SELECT COUNT(*) FROM post_like WHERE post_id = #{postId} AND user_id = #{userId}")
    long countByPostAndUser(@Param("postId") Long postId, @Param("userId") Long userId);
}
