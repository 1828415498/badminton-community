package com.badmintoncommunity.module.reservation.mapper;

import com.badmintoncommunity.module.reservation.dto.OccupancySlot;
import com.badmintoncommunity.module.reservation.dto.ReservationVO;
import com.badmintoncommunity.module.reservation.entity.Reservation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 场地占用账本（reservation）数据访问。
 *
 * <p>个人预约与活动占场都在这张表里（ADR-0005 统一账本）。占用冲突检查是唯一事实源。</p>
 */
@Mapper
public interface ReservationMapper {

    // ---------- 插入 / 查询 ----------

    @Insert("INSERT INTO reservation (court_id, user_id, activity_id, start_time, end_time, status) "
            + "VALUES (#{courtId}, #{userId}, #{activityId}, #{startTime}, #{endTime}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Reservation reservation);

    @Select("SELECT id, court_id, user_id, activity_id, start_time, end_time, status, "
            + "cancel_time, create_time, update_time FROM reservation WHERE id = #{id}")
    Reservation findById(Long id);

    /** 带球场名的单条详情（join court） */
    @Select("SELECT r.id, r.court_id, c.name AS court_name, r.user_id, r.activity_id, "
            + "r.start_time, r.end_time, r.status, r.cancel_time, r.create_time "
            + "FROM reservation r JOIN court c ON c.id = r.court_id WHERE r.id = #{id}")
    ReservationVO findVOById(Long id);

    // ---------- 冲突与上限检查（Service 层先加行锁再调用） ----------

    /** 同一球场与 [start,end) 相交的有效行数（个人与活动来源都算） */
    @Select("SELECT COUNT(*) FROM reservation "
            + "WHERE court_id = #{courtId} AND status = 1 "
            + "  AND start_time < #{end} AND end_time > #{start}")
    long countCourtOverlap(@Param("courtId") Long courtId,
                           @Param("start") LocalDateTime start,
                           @Param("end") LocalDateTime end);

    /** 同一用户与 [start,end) 相交的有效「个人预约」数（活动占场不计入） */
    @Select("SELECT COUNT(*) FROM reservation "
            + "WHERE user_id = #{userId} AND activity_id IS NULL AND status = 1 "
            + "  AND start_time < #{end} AND end_time > #{start}")
    long countPersonalOverlap(@Param("userId") Long userId,
                              @Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);

    /**
     * 同一用户「某天」在「某球场」的全部有效个人预约行（requirements 规则 18 用）。
     * 返回行仅用于合并连续占用段判断（Service 层已在 user 行锁内调用）。
     */
    @Select("SELECT id, start_time, end_time FROM reservation "
            + "WHERE user_id = #{userId} AND court_id = #{courtId} "
            + "  AND activity_id IS NULL AND status = 1 "
            + "  AND start_time < #{dayEnd} AND end_time > #{dayStart} "
            + "ORDER BY start_time")
    List<Reservation> listPersonalOnCourtAndDay(@Param("userId") Long userId,
                                                @Param("courtId") Long courtId,
                                                @Param("dayStart") LocalDateTime dayStart,
                                                @Param("dayEnd") LocalDateTime dayEnd);

    // ---------- 我的预约（个人行） ----------

    /** 我的预约列表（仅 activity_id IS NULL 的个人行；若需活动占场信息见活动模块） */
    @Select("<script>"
            + "SELECT r.id, r.court_id, c.name AS court_name, r.user_id, r.activity_id, "
            + "r.start_time, r.end_time, r.status, r.cancel_time, r.create_time "
            + "FROM reservation r JOIN court c ON c.id = r.court_id "
            + "WHERE r.user_id = #{userId} AND r.activity_id IS NULL "
            + "<if test='status != null'>AND r.status = #{status}</if> "
            + "<if test='startFrom != null'>AND r.start_time &gt;= #{startFrom}</if> "
            + "ORDER BY r.start_time DESC "
            + "LIMIT #{offset}, #{pageSize}"
            + "</script>")
    List<ReservationVO> listMine(@Param("userId") Long userId,
                                 @Param("status") Integer status,
                                 @Param("startFrom") LocalDateTime startFrom,
                                 @Param("offset") int offset,
                                 @Param("pageSize") int pageSize);

    @Select("<script>"
            + "SELECT COUNT(*) FROM reservation "
            + "WHERE user_id = #{userId} AND activity_id IS NULL "
            + "<if test='status != null'>AND status = #{status}</if> "
            + "<if test='startFrom != null'>AND start_time &gt;= #{startFrom}</if>"
            + "</script>")
    long countMine(@Param("userId") Long userId,
                   @Param("status") Integer status,
                   @Param("startFrom") LocalDateTime startFrom);

    // ---------- 取消 ----------

    /** 条件更新取消：只在「有效」时生效，保证重复取消幂等 */
    @Update("UPDATE reservation SET status = 2, cancel_time = #{now} "
            + "WHERE id = #{id} AND status = 1")
    int cancel(@Param("id") Long id, @Param("now") LocalDateTime now);

    // ---------- 占用查询（api.md §3.3） ----------

    /**
     * 查询某球场在 [dayStart, dayEnd) 范围内的「有效占用」。
     * 只数 status=1 的行；kind 由 user_id 是否为空推导。
     */
    @Select("SELECT start_time, end_time, "
            + "CASE WHEN user_id IS NOT NULL THEN 'PERSONAL' ELSE 'ACTIVITY' END AS kind, "
            + "activity_id "
            + "FROM reservation "
            + "WHERE court_id = #{courtId} AND status = 1 "
            + "  AND start_time < #{dayEnd} AND end_time > #{dayStart} "
            + "ORDER BY start_time")
    List<OccupancySlot> findEffectiveByCourtAndRange(@Param("courtId") Long courtId,
                                                     @Param("dayStart") LocalDateTime dayStart,
                                                     @Param("dayEnd") LocalDateTime dayEnd);

    /** 某球场是否还有「未来有效占用」（停用球场前检查，business-flows 动作 12） */
    @Select("SELECT COUNT(*) FROM reservation "
            + "WHERE court_id = #{courtId} AND status = 1 AND end_time > #{now}")
    long countFutureEffective(@Param("courtId") Long courtId, @Param("now") LocalDateTime now);

    // ---------- 活动占场（activity_id 归属的行） ----------

    /**
     * 某活动占用的球场 id 列表（activity.md §5.3 详情回显 court_ids）。
     *
     * <p>不过滤 status：活动被驳回/取消后占场行虽然已置为「已取消」，但"这个活动原本占哪几块场"
     * 仍应能展示给创建者与管理员，因此保留全部历史行。</p>
     */
    @Select("SELECT DISTINCT court_id FROM reservation "
            + "WHERE activity_id = #{activityId} ORDER BY court_id")
    List<Long> findCourtIdsByActivityId(Long activityId);

    /**
     * 释放某活动名下全部「有效」占场（activity 取消 / 驳回时级联调用）。
     *
     * <p>只更新 status=1 的行，已取消的行不重复写 cancel_time，保证幂等语义正确。</p>
     */
    @Update("UPDATE reservation SET status = 2, cancel_time = #{now} "
            + "WHERE activity_id = #{activityId} AND status = 1")
    int cancelByActivityId(@Param("activityId") Long activityId,
                           @Param("now") LocalDateTime now);
}
