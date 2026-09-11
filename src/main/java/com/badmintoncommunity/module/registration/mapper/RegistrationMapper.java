package com.badmintoncommunity.module.registration.mapper;

import com.badmintoncommunity.module.registration.dto.ActivityMemberVO;
import com.badmintoncommunity.module.registration.dto.MyRegistrationRow;
import com.badmintoncommunity.module.registration.entity.Registration;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 活动报名（registration）数据访问。
 *
 * <h2>本 Mapper 的三条约定</h2>
 * <ol>
 *   <li><b>绝不物理删除</b>：取消报名 = 把 status 置 2。这既保留"谁曾报名"的痕迹，
 *       也让重新报名只需把状态置回 1（不新增第二行，符合唯一约束）。</li>
 *   <li><b>满员判定用 count + activity 行锁</b>：{@link #countActive} 必须在调用方
 *       持有 activity 行锁的事务里执行，否则并发抢名额会超卖（business-flows 动作 7）。</li>
 *   <li><b>级联取消由活动侧驱动</b>：活动被驳回/取消时调用 {@link #cancelByActivityId}，
 *       一次性作废该活动全部有效报名。</li>
 * </ol>
 */
@Mapper
public interface RegistrationMapper {

    // ---------- 插入 / 单条读取 ----------

    @Insert("INSERT INTO registration (user_id, activity_id, status) "
            + "VALUES (#{userId}, #{activityId}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Registration registration);

    @Select("SELECT id, user_id, activity_id, status, create_time, update_time "
            + "FROM registration WHERE id = #{id}")
    Registration findById(Long id);

    /**
     * 查「某用户在某活动」的报名行（含已取消的历史行）。
     * Service 靠它区分三种情况：已报名（幂等返回）/ 曾取消（置回 1）/ 从未报名（新建）。
     * 唯一约束 uk_registration_user_activity 保证最多一条，故不需要 LIMIT。
     */
    @Select("SELECT id, user_id, activity_id, status, create_time, update_time "
            + "FROM registration WHERE user_id = #{userId} AND activity_id = #{activityId}")
    Registration findByUserAndActivity(@Param("userId") Long userId,
                                       @Param("activityId") Long activityId);

    // ---------- 报名状态迁移 ----------

    /** 重新报名：把历史行从"已取消(2)"置回"已报名(1)"，不新增行。 */
    @Update("UPDATE registration SET status = 1 WHERE id = #{id}")
    int reactivate(Long id);

    /**
     * 取消报名：条件更新，只在"当前有效"时生效。
     *
     * @return 影响行数：1=取消成功，0=未报名或已是取消态（由 Service 判定为 NOT_REGISTERED）
     */
    @Update("UPDATE registration SET status = 2 "
            + "WHERE user_id = #{userId} AND activity_id = #{activityId} AND status = 1")
    int cancel(@Param("userId") Long userId, @Param("activityId") Long activityId);

    /** 级联作废：活动被驳回/取消时，把该活动全部有效报名置为已取消。 */
    @Update("UPDATE registration SET status = 2 "
            + "WHERE activity_id = #{activityId} AND status = 1")
    int cancelByActivityId(Long activityId);

    // ---------- 计数与名册 ----------

    /** 当前有效报名数（满员判定的事实来源；须在 activity 行锁内调用）。 */
    @Select("SELECT COUNT(*) FROM registration WHERE activity_id = #{activityId} AND status = 1")
    long countActive(Long activityId);

    /** 活动成员列表（api.md §6.3）：只列有效报名，按报名先后排序。 */
    @Select("SELECT r.id, r.user_id, u.nickname, u.avatar_url, r.create_time "
            + "FROM registration r JOIN `user` u ON u.id = r.user_id "
            + "WHERE r.activity_id = #{activityId} AND r.status = 1 "
            + "ORDER BY r.create_time ASC "
            + "LIMIT #{offset}, #{pageSize}")
    List<ActivityMemberVO> listActiveMembers(@Param("activityId") Long activityId,
                                             @Param("offset") int offset,
                                             @Param("pageSize") int pageSize);

    // ---------- 我参加的活动（api.md §6.4） ----------

    /** 一次 join 出「报名 + 活动 + 创建者昵称 + 实时报名数」，Service 再组装成嵌套结构。 */
    @Select("<script>"
            + "SELECT r.id AS registration_id, r.status AS registration_status, "
            + "r.create_time AS registration_create_time, "
            + "a.id AS activity_id, a.title, a.creator_id, u.nickname AS creator_nickname, "
            + "a.start_time, a.end_time, a.max_members, a.status AS activity_status, "
            + "(SELECT COUNT(*) FROM registration r2 WHERE r2.activity_id = a.id AND r2.status = 1) "
            + "  AS joined_count "
            + "FROM registration r "
            + "JOIN activity a ON a.id = r.activity_id "
            + "JOIN `user` u ON u.id = a.creator_id "
            + "WHERE r.user_id = #{userId} "
            + "<if test='status != null'>AND r.status = #{status} </if>"
            + "ORDER BY a.start_time DESC "
            + "LIMIT #{offset}, #{pageSize}"
            + "</script>")
    List<MyRegistrationRow> listMine(@Param("userId") Long userId,
                                     @Param("status") Integer status,
                                     @Param("offset") int offset,
                                     @Param("pageSize") int pageSize);

    @Select("<script>"
            + "SELECT COUNT(*) FROM registration r WHERE r.user_id = #{userId} "
            + "<if test='status != null'>AND r.status = #{status} </if>"
            + "</script>")
    long countMine(@Param("userId") Long userId, @Param("status") Integer status);
}
