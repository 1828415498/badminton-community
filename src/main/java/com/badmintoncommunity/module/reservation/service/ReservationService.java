package com.badmintoncommunity.module.reservation.service;

import com.badmintoncommunity.common.BusinessException;
import com.badmintoncommunity.common.PageResult;
import com.badmintoncommunity.common.ResultCode;
import com.badmintoncommunity.module.reservation.dto.CreateReservationRequest;
import com.badmintoncommunity.module.reservation.dto.ReservationVO;
import com.badmintoncommunity.module.reservation.entity.Reservation;
import com.badmintoncommunity.module.reservation.mapper.ReservationMapper;
import com.badmintoncommunity.module.user.mapper.UserMapper;
import com.badmintoncommunity.module.venue.entity.Court;
import com.badmintoncommunity.module.venue.mapper.CourtMapper;
import com.badmintoncommunity.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 个人球场预约业务（api.md §4，business-flows 动作 3/4）。
 *
 * 这个 Service 在做的业务判断（3 句话版）
 * 1. 创建预约前系统依次把关：时间整点/不跨天 → 在 7 天窗口内且未开始 → 本人重叠时段没超过 2 块场 → 这块场该时段没被占；
 * 2. 通过后插入一条「个人占用行」，该场该时段即对其他所有人（含活动锁场）关闭；
 * 3. 取消只能对自己的、还没开场的预约做，开场前不足 4 小时系统拒绝，取消后场地立即空出。
 *
 * 并发保证（business-flows §0.4）
 * 同一事务内先锁 user 行（串行化同用户的并发预约），再锁 court 行（串行化同一球场的并发写入），
 * 然后再做冲突检查与插入 —— 保证"检查后、插入前"不会有第三方写进同一时段。
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationMapper reservationMapper;
    private final CourtMapper courtMapper;
    private final UserMapper userMapper;

    /** 同一用户重叠时段的有效个人预约上限 */
    private static final int MAX_PERSONAL_OVERLAP = 2;
    /** 同一用户同一天在同一球场的累计占用上限（小时，requirements 规则 18） */
    private static final int MAX_DAILY_COURT_HOURS = 2;

    /**
     * 创建个人预约。流程见类注释；任一步失败抛业务异常并整体回滚。
     */
    @Transactional
    public ReservationVO create(Long userId, CreateReservationRequest req) {
        LocalDateTime start = req.getStartTime();
        LocalDateTime end = req.getEndTime();
        LocalDateTime now = LocalDateTime.now();

        // ① 时段合法性 + 预约窗口（允许当天但须晚于当前时刻，最多未来 7 天）
        TimeRules.validateInterval(start, end);
        TimeRules.ensureStartBookable(start, now);

        // ② 锁本人行，串行化同一用户的并发预约，再检查"重叠 ≤2 场"
        //不然会出现这个人先预约了一个 然后再同时预约两个 如果不进行行锁处理那莪就会出现两个都通过的情况出现
        userMapper.findByIdForUpdate(userId);
        if (reservationMapper.countPersonalOverlap(userId, start, end) >= MAX_PERSONAL_OVERLAP) {
            //检查同一个用户在同一时间段只能有两个预约
            throw new BusinessException(ResultCode.PERSONAL_RESERVATION_LIMIT_EXCEEDED);
        }
        // ②.5 规则 18：同一天同一球场累计占用 ≤2 小时（仍在 user 行锁内，同用户并发已被串行化）
        ensureDailyCourtDurationWithin2h(userId, req.getCourtId(), start, end);

        // ③ 锁球场行（与其他预约/活动锁场互斥），再做统一冲突检查
        Court court = courtMapper.findByIdForUpdate(req.getCourtId());
        if (court == null) {
            throw new BusinessException(ResultCode.COURT_NOT_FOUND);
        }
        if (court.getStatus() != 1) {
            throw new BusinessException(ResultCode.COURT_UNAVAILABLE);
        }
        if (reservationMapper.countCourtOverlap(court.getId(), start, end) > 0) {
            throw new BusinessException(ResultCode.COURT_ALREADY_RESERVED);
        }

        // ④ 写入个人占用行
        Reservation reservation = new Reservation();
        reservation.setCourtId(court.getId());
        reservation.setUserId(userId);          // 个人预约：user_id 有值、activity_id 为空
        reservation.setActivityId(null);
        reservation.setStartTime(start);
        reservation.setEndTime(end);
        reservation.setStatus(1);
        reservationMapper.insert(reservation);
        return reservationMapper.findVOById(reservation.getId());
    }

    /**
     * requirements 规则 18：同一用户同一天在同一球场的有效个人预约，合并「重叠或首尾相接」的
     * 连续占用段后，任一段总跨度不得超过 2 小时 —— 防止单人连续占场。
     *
     * <p>只统计个人行（activity_id IS NULL），活动占场行不参与。</p>
     *
     * <p>必须在本事务已持有 user 行锁时调用：同一用户的并发创建被行锁串行化，
     * 否则两个并发请求可能各自通过校验、合并后超限。</p>
     */
    private void ensureDailyCourtDurationWithin2h(Long userId, Long courtId,
                                                  LocalDateTime start, LocalDateTime end) {
        LocalDateTime dayStart = start.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        List<Reservation> existing = reservationMapper.listPersonalOnCourtAndDay(
                userId, courtId, dayStart, dayEnd);

        // 新时段 + 当天已有行，统一按开始时间排序
        List<LocalDateTime[]> segments = new ArrayList<>();
        segments.add(new LocalDateTime[]{start, end});
        for (Reservation r : existing) {
            segments.add(new LocalDateTime[]{r.getStartTime(), r.getEndTime()});
        }
        segments.sort(Comparator.comparing(s -> s[0]));

        // 贪心合并：重叠或首尾相接（seg.start <= curEnd）并入当前段；有断档则先结算当前段
        LocalDateTime curStart = null;
        LocalDateTime curEnd = null;
        for (LocalDateTime[] seg : segments) {
            if (curStart == null) {
                curStart = seg[0];
                curEnd = seg[1];
            } else if (seg[0].isAfter(curEnd)) {
                rejectIfOverHours(curStart, curEnd);
                curStart = seg[0];
                curEnd = seg[1];
            } else if (seg[1].isAfter(curEnd)) {
                curEnd = seg[1];
            }
        }
        rejectIfOverHours(curStart, curEnd);
    }

    /** 连续段跨度 > 2 小时则拒绝（恰好 2 小时允许）。 */
    private void rejectIfOverHours(LocalDateTime start, LocalDateTime end) {
        if (start != null && Duration.between(start, end).toHours() > MAX_DAILY_COURT_HOURS) {
            throw new BusinessException(ResultCode.COURT_DAILY_DURATION_EXCEEDED);
        }
    }

    /**
     * 取消自己的个人预约：开场前剩余 ≥4 小时才允许；已取消的重复取消按幂等成功处理。
     */
    @Transactional
    public ReservationVO cancel(Long userId, Long reservationId) {
        Reservation reservation = reservationMapper.findById(reservationId);
        // 统一语义：不存在 / 不是本人 / 不是个人行（活动占场）都按"找不到"处理，避免泄露存在性
        if (reservation == null
                || !userId.equals(reservation.getUserId())
                || reservation.getActivityId() != null) {
            throw new BusinessException(ResultCode.RESERVATION_NOT_FOUND);
        }
        // 幂等：已经取消的再取消返回成功（business-flows 动作 4）
        if (reservation.getStatus() == 2) {
            return reservationMapper.findVOById(reservationId);
        }
        // 取消窗口：开场前不足 4 小时普通用户不可自行取消
        LocalDateTime now = LocalDateTime.now();
        if (reservation.getStartTime().isBefore(now.plusHours(4))) {
            throw new BusinessException(ResultCode.CANCEL_WINDOW_EXCEEDED);
        }
        reservationMapper.cancel(reservationId, now);
        return reservationMapper.findVOById(reservationId);
    }

    /**
     * 我的预约列表（api.md §4.2）：只返回个人行，支持按状态/开始时间过滤与分页。
     */
    @Transactional(readOnly = true)
    public PageResult<ReservationVO> listMine(Long userId, Integer status, LocalDateTime startFrom,
                                              int page, int pageSize) {
        long total = reservationMapper.countMine(userId, status, startFrom);
        List<ReservationVO> list = reservationMapper.listMine(
                userId, status, startFrom, (page - 1) * pageSize, pageSize);
        return new PageResult<>(list, total, page, pageSize);
    }

    /**
     * 预约详情（api.md §4.3）：本人可查；管理员可查任意个人预约；活动占场行不对外暴露。
     */
    @Transactional(readOnly = true)
    public ReservationVO detail(LoginUser me, Long reservationId) {
        ReservationVO vo = reservationMapper.findVOById(reservationId);
        if (vo == null || vo.getActivityId() != null) {
            throw new BusinessException(ResultCode.RESERVATION_NOT_FOUND);
        }
        boolean isOwner = me.userId().equals(vo.getUserId());
        boolean isAdmin = me.role() != null && me.role() == 1;
        if (!isOwner && !isAdmin) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return vo;
    }

    /**
     * 管理员强制取消个人预约（api.md 第 8 章，requirements 规则 11）。
     *
     * <p>与用户自助取消（{@link #cancel}）的唯一差别：<b>不受"开场前 4 小时"窗口限制</b>。
     * 用于场地临时维修、用户投诉等特殊情况。</p>
     *
     * <p>只处理个人行：活动占场行的释放属于"取消活动"的职责（会连带作废全部报名），
     * 若从这里单独取消某条占场，会造成"活动还在但场地没了"的不一致。</p>
     */
    @Transactional
    public ReservationVO cancelByAdmin(LoginUser me, Long reservationId) {
        if (me == null || me.role() == null || me.role() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        Reservation reservation = reservationMapper.findById(reservationId);
        // 不存在 / 非个人行，统一按"找不到"处理（与用户侧语义一致，不泄露存在性）
        if (reservation == null || reservation.getActivityId() != null) {
            throw new BusinessException(ResultCode.RESERVATION_NOT_FOUND);
        }
        // 幂等：已取消则直接回显当前状态，不重复写 cancel_time
        if (reservation.getStatus() != 2) {
            reservationMapper.cancel(reservationId, LocalDateTime.now());
        }
        return reservationMapper.findVOById(reservationId);
    }
}
