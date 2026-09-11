package com.badmintoncommunity.module.activity.mapper;

import com.badmintoncommunity.module.activity.dto.ActivityItemVO;
import com.badmintoncommunity.module.activity.dto.ActivityVO;
import com.badmintoncommunity.module.activity.entity.Activity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动（activity）数据访问。
 *
 * <p>两条约定贯穿本 Mapper：</p>
 * <ol>
 *   <li><b>joined_count 永远是实时子查询</b>，不落冗余列 —— 报名数由 registration 推导，
 *       存两份必然会出现"计数与名册不一致"（database.md §3 activity 职责边界）。</li>
 *   <li><b>状态迁移用条件更新</b>（{@code WHERE status = 0}）—— 审核类操作天然幂等安全：
 *       并发重复审核时只有一条 UPDATE 能命中，另一条影响 0 行，由 Service 判定为状态非法。</li>
 * </ol>
 */
@Mapper
public interface ActivityMapper {

    // ---------- 插入 / 单条读取 ----------

    @Insert("INSERT INTO activity (creator_id, title, description, start_time, end_time, "
            + "max_members, status) "
            + "VALUES (#{creatorId}, #{title}, #{description}, #{startTime}, #{endTime}, "
            + "#{maxMembers}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Activity activity);

    @Select("SELECT id, creator_id, title, description, start_time, end_time, max_members, status, "
            + "reviewer_id, review_time, reject_reason, create_time, update_time "
            + "FROM activity WHERE id = #{id}")
    Activity findById(Long id);

    /**
     * 加排他行锁的读取：取消 / 驳回 / 审核 / 报名前锁住活动行。
     *
     * <p>作用是把"同一活动的并发写操作"串行化（business-flows §0.4 第 3/4 条：
     * 涉及满员或状态迁移的操作先锁 activity 行）。</p>
     */
    @Select("SELECT id, creator_id, title, description, start_time, end_time, max_members, status, "
            + "reviewer_id, review_time, reject_reason, create_time, update_time "
            + "FROM activity WHERE id = #{id} FOR UPDATE")
    Activity findByIdForUpdate(Long id);

    /** 详情（含创建者昵称与实时报名数）。court_ids 由 ReservationMapper 单独查。 */
    @Select("SELECT a.id, a.creator_id, u.nickname AS creator_nickname, a.title, a.description, "
            + "a.start_time, a.end_time, a.max_members, a.status, a.reviewer_id, a.review_time, "
            + "a.reject_reason, a.create_time, "
            + "(SELECT COUNT(*) FROM registration r WHERE r.activity_id = a.id AND r.status = 1) "
            + "  AS joined_count "
            + "FROM activity a JOIN `user` u ON u.id = a.creator_id WHERE a.id = #{id}")
    ActivityVO findVOById(Long id);

    // ---------- 列表（公开 / 我创建的 / 待审核） ----------

    /**
     * 按状态集合分页查询（api.md §5.2 公开列表：statuses=[1,4] 或其中一者）。
     * startFrom / startTo 用于"只看未来活动"等时间窗过滤。
     */
    @Select("<script>"
            + "SELECT a.id, a.title, a.creator_id, u.nickname AS creator_nickname, "
            + "a.start_time, a.end_time, a.max_members, a.status, "
            + "(SELECT COUNT(*) FROM registration r WHERE r.activity_id = a.id AND r.status = 1) "
            + "  AS joined_count "
            + "FROM activity a JOIN `user` u ON u.id = a.creator_id "
            + "WHERE a.status IN "
            + "<foreach collection='statuses' item='s' open='(' separator=',' close=')'>#{s}</foreach> "
            + "<if test='startFrom != null'>AND a.start_time &gt;= #{startFrom} </if>"
            + "<if test='startTo != null'>AND a.start_time &lt;= #{startTo} </if>"
            + "ORDER BY a.start_time DESC "
            + "LIMIT #{offset}, #{pageSize}"
            + "</script>")
    List<ActivityItemVO> listByStatuses(@Param("statuses") List<Integer> statuses,
                                        @Param("startFrom") LocalDateTime startFrom,
                                        @Param("startTo") LocalDateTime startTo,
                                        @Param("offset") int offset,
                                        @Param("pageSize") int pageSize);

    @Select("<script>"
            + "SELECT COUNT(*) FROM activity a "
            + "WHERE a.status IN "
            + "<foreach collection='statuses' item='s' open='(' separator=',' close=')'>#{s}</foreach> "
            + "<if test='startFrom != null'>AND a.start_time &gt;= #{startFrom} </if>"
            + "<if test='startTo != null'>AND a.start_time &lt;= #{startTo} </if>"
            + "</script>")
    long countByStatuses(@Param("statuses") List<Integer> statuses,
                         @Param("startFrom") LocalDateTime startFrom,
                         @Param("startTo") LocalDateTime startTo);

    /** 我创建的活动（api.md §5.4）：含全部状态，可选按单个状态过滤。 */
    @Select("<script>"
            + "SELECT a.id, a.title, a.creator_id, u.nickname AS creator_nickname, "
            + "a.start_time, a.end_time, a.max_members, a.status, "
            + "(SELECT COUNT(*) FROM registration r WHERE r.activity_id = a.id AND r.status = 1) "
            + "  AS joined_count "
            + "FROM activity a JOIN `user` u ON u.id = a.creator_id "
            + "WHERE a.creator_id = #{creatorId} "
            + "<if test='status != null'>AND a.status = #{status} </if>"
            + "ORDER BY a.start_time DESC "
            + "LIMIT #{offset}, #{pageSize}"
            + "</script>")
    List<ActivityItemVO> listByCreator(@Param("creatorId") Long creatorId,
                                       @Param("status") Integer status,
                                       @Param("offset") int offset,
                                       @Param("pageSize") int pageSize);

    @Select("<script>"
            + "SELECT COUNT(*) FROM activity a WHERE a.creator_id = #{creatorId} "
            + "<if test='status != null'>AND a.status = #{status} </if>"
            + "</script>")
    long countByCreator(@Param("creatorId") Long creatorId, @Param("status") Integer status);

    /** 管理员待审队列（api.md §5.6）：status=0，按创建时间升序（先创建先审）。 */
    @Select("SELECT a.id, a.title, a.creator_id, u.nickname AS creator_nickname, "
            + "a.start_time, a.end_time, a.max_members, a.status, "
            + "(SELECT COUNT(*) FROM registration r WHERE r.activity_id = a.id AND r.status = 1) "
            + "  AS joined_count "
            + "FROM activity a JOIN `user` u ON u.id = a.creator_id "
            + "WHERE a.status = 0 "
            + "ORDER BY a.create_time ASC "
            + "LIMIT #{offset}, #{pageSize}")
    List<ActivityItemVO> listPending(@Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("SELECT COUNT(*) FROM activity WHERE status = 0")
    long countPending();

    // ---------- 状态迁移 ----------

    /**
     * 审核通过：仅当仍处于"待审核"时生效（条件更新，天然防重复审核）。
     *
     * @return 影响行数：1=成功，0=活动已被他人处理/不存在
     */
    @Update("UPDATE activity SET status = 1, reviewer_id = #{reviewerId}, review_time = #{now} "
            + "WHERE id = #{id} AND status = 0")
    int approve(@Param("id") Long id,
                @Param("reviewerId") Long reviewerId,
                @Param("now") LocalDateTime now);

    /** 审核驳回：同上，条件更新保证只处理一次。 */
    @Update("UPDATE activity SET status = 2, reviewer_id = #{reviewerId}, review_time = #{now}, "
            + "reject_reason = #{reason} "
            + "WHERE id = #{id} AND status = 0")
    int reject(@Param("id") Long id,
               @Param("reviewerId") Long reviewerId,
               @Param("now") LocalDateTime now,
               @Param("reason") String reason);

    /**
     * 取消活动（创建者或管理员）：直接置状态。
     * 调用方已持有该 activity 行锁，故此处不再带状态条件 —— 状态合法性在锁内已判定。
     */
    @Update("UPDATE activity SET status = 3 WHERE id = #{id}")
    int markCancelled(@Param("id") Long id);

    /**
     * 定时任务用：把"已发布且已结束"的活动批量置为"已结束"（Q1=A）。
     *
     * <p><b>注意</b>：本方法没有 {@code <script>} 包裹，因此 SQL 里的"小于等于"必须直接写
     * {@code <=}，<b>不能</b>写成 XML 转义的 {@code &lt;=} —— 后者只在被 XML 解析器处理的
     * 场合（XML mapper、或被 {@code <script>} 包裹的注解 SQL）才会还原，否则会被原样发给
     * MySQL 造成语法错误。</p>
     *
     * @return 本次置位的行数（0 表示无过期活动）
     */
    @Update("UPDATE activity SET status = 4 WHERE status = 1 AND end_time <= #{now}")
    int finishExpired(@Param("now") LocalDateTime now);
}
