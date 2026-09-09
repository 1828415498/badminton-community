package com.badmintoncommunity.module.reservation.mapper;

import com.badmintoncommunity.module.reservation.dto.OccupancySlot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 场地占用账本（reservation）数据访问。
 *
 * <p>个人预约与活动占场都在这张表里（ADR-0005 统一账本）。
 * 目前只实现球场占用/冲突查询所需的方法；创建预约等写入方法在个人预约模块补齐。</p>
 */
@Mapper
public interface ReservationMapper {

    /**
     * 查询某球场在 [dayStart, dayEnd) 范围内的「有效占用」（api.md §3.3 球场预约情况）。
     * 只数 status=1（有效）的行；kind 由 user_id 是否为空推导。
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

    /**
     * 某球场是否还有「未来有效占用」（停用球场前检查，business-flows 动作 12）。
     * 个人与活动来源都算。
     */
    @Select("SELECT COUNT(*) FROM reservation "
            + "WHERE court_id = #{courtId} AND status = 1 AND end_time > #{now}")
    long countFutureEffective(@Param("courtId") Long courtId, @Param("now") LocalDateTime now);
}
