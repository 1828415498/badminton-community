package com.badmintoncommunity.module.activity.service;

import com.badmintoncommunity.common.BusinessException;
import com.badmintoncommunity.common.PageResult;
import com.badmintoncommunity.common.ResultCode;
import com.badmintoncommunity.module.activity.dto.ActivityItemVO;
import com.badmintoncommunity.module.activity.dto.ActivityVO;
import com.badmintoncommunity.module.activity.dto.CreateActivityRequest;
import com.badmintoncommunity.module.activity.entity.Activity;
import com.badmintoncommunity.module.activity.mapper.ActivityMapper;
import com.badmintoncommunity.module.registration.entity.Registration;
import com.badmintoncommunity.module.registration.mapper.RegistrationMapper;
import com.badmintoncommunity.module.reservation.entity.Reservation;
import com.badmintoncommunity.module.reservation.mapper.ReservationMapper;
import com.badmintoncommunity.module.reservation.service.TimeRules;
import com.badmintoncommunity.module.venue.entity.Court;
import com.badmintoncommunity.module.venue.mapper.CourtMapper;
import com.badmintoncommunity.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动业务（api.md §5，business-flows 动作 5/6/9/10）。
 *
 * <h2>这个 Service 在做的业务（3 句话版）</h2>
 * <ol>
 *   <li>用户发起活动时，系统一次性办完三件事：建活动（待审核）、按所选球场逐块锁场、把发起人记成第一名报名者；</li>
 *   <li>管理员审核后活动对外开放报名，驳回或取消都要把场地全部还回去、把报名全部作废；</li>
 *   <li>活动占场与个人预约走<b>同一套冲突规则</b>，谁先占谁赢，绝不出现同一块场同一时段被两边同时占用。</li>
 * </ol>
 *
 * <h2>为什么创建活动要和预约抢同一把锁</h2>
 * <p>活动占的场和个人预约的场在 reservation 里是同一批行。如果活动创建时不按球场加锁、
 * 只查一次"有没有冲突"，那么"查完、还没插入"的瞬间，另一个用户就能把同一场地约走 ——
 * 结果是同一块场被重复占用。所以这里必须：<b>先锁球场行 → 再查冲突 → 再插入</b>，
 * 让两个事务真正串行（business-flows §0.4）。</p>
 *
 * <h2>加锁顺序（防死锁的硬规则）</h2>
 * <p>多块球场一律按 {@code court_id} <b>升序</b>加锁。若 A 事务按 1→2 锁、B 事务按 2→1 锁，
 * 两者会各持一把、互等对方释放，形成死锁。固定升序可彻底消除这种交叉等待。</p>
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityMapper activityMapper;
    private final ReservationMapper reservationMapper;
    private final RegistrationMapper registrationMapper;
    private final CourtMapper courtMapper;

    /** 公开可见的状态：1=已发布，4=已结束（api.md §5.2/§5.3 可见性约定） */
    private static final List<Integer> PUBLIC_STATUSES = List.of(1, 4);

    // ==================== 创建 ====================

    /**
     * 创建活动（api.md §5.1，business-flows 动作 5）。
     *
     * <p>整个方法是一个事务：任一块球场冲突 → 全部回滚，绝不允许出现
     * "活动建了但只锁到一半球场"的中间状态。</p>
     */
    @Transactional
    public ActivityVO create(Long userId, CreateActivityRequest req) {
        LocalDateTime start = req.getStartTime();
        LocalDateTime end = req.getEndTime();
        LocalDateTime now = LocalDateTime.now();

        // ① 时间规则与个人预约完全相同：整点、end>start、不跨天、未来 7 天内且未开始
        //    （requirements 规则 17：活动锁场不能绕过预约窗口）
        TimeRules.validateInterval(start, end);
        TimeRules.ensureStartBookable(start, now);

        // ② 球场去重 + 升序 —— 固定加锁顺序，这是防死锁的关键一步
        List<Long> courtIds = req.getCourtIds().stream().distinct().sorted().toList();

        // ③ 逐块球场：锁行 → 校验可用 → 查该场该时段是否已被占（个人与活动来源都算）
        for (Long courtId : courtIds) {
            Court court = courtMapper.findByIdForUpdate(courtId);
            if (court == null) {
                throw new BusinessException(ResultCode.COURT_NOT_FOUND, "球场不存在：" + courtId);
            }
            if (court.getStatus() != 1) {
                throw new BusinessException(ResultCode.COURT_UNAVAILABLE,
                        "球场不可预约：" + court.getName());
            }
            if (reservationMapper.countCourtOverlap(courtId, start, end) > 0) {
                throw new BusinessException(ResultCode.COURT_ALREADY_RESERVED,
                        "球场 " + court.getName() + " 在该时段已被占用");
            }
        }

        // ④ 插入活动主体：创建即待审核，没有草稿态
        Activity activity = new Activity();
        activity.setCreatorId(userId);
        activity.setTitle(req.getTitle());
        activity.setDescription(req.getDescription());
        activity.setStartTime(start);
        activity.setEndTime(end);
        activity.setMaxMembers(req.getMaxMembers());
        activity.setStatus(0);
        activityMapper.insert(activity);

        // ⑤ 为每块球场写一条「活动占场」行（user_id 必须为 NULL，遵守归属不变量）
        //    待审核期间场地即被占用 —— 这是既定业务，文档已明示
        for (Long courtId : courtIds) {
            Reservation occupancy = new Reservation();
            occupancy.setCourtId(courtId);
            occupancy.setUserId(null);                   // 活动占场：user_id 为空
            occupancy.setActivityId(activity.getId());   // activity_id 有值
            occupancy.setStartTime(start);
            occupancy.setEndTime(end);
            occupancy.setStatus(1);
            reservationMapper.insert(occupancy);
        }

        // ⑥ 创建者自动成为第一名报名者（占用 1 个名额，故 max_members ≥ 1）
        Registration creatorRegistration = new Registration();
        creatorRegistration.setUserId(userId);
        creatorRegistration.setActivityId(activity.getId());
        creatorRegistration.setStatus(1);
        registrationMapper.insert(creatorRegistration);

        return loadDetailVO(activity.getId());
    }

    // ==================== 查询 ====================

    /**
     * 活动列表（api.md §5.2）：匿名可浏览"已发布 + 已结束"。
     * 非公开状态（0/2/3）只有管理员能按状态筛选。
     */
    @Transactional(readOnly = true)
    public PageResult<ActivityItemVO> listPublic(Integer status, LocalDateTime startFrom,
                                                 LocalDateTime startTo, LoginUser me,
                                                 int page, int pageSize) {
        List<Integer> statuses;
        if (status == null) {
            statuses = PUBLIC_STATUSES;
        } else if (PUBLIC_STATUSES.contains(status)) {
            statuses = List.of(status);
        } else if (status == 0 || status == 2 || status == 3) {
            // 待审核/已驳回/已取消属于非公开状态：匿名与普通用户不可查询
            if (!isAdmin(me)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "该状态的活动仅管理员可查询");
            }
            statuses = List.of(status);
        } else {
            throw new BusinessException(ResultCode.PARAM_INVALID, "status 取值非法");
        }

        long total = activityMapper.countByStatuses(statuses, startFrom, startTo);
        List<ActivityItemVO> list = activityMapper.listByStatuses(statuses, startFrom, startTo,
                (page - 1) * pageSize, pageSize);
        return new PageResult<>(list, total, page, pageSize);
    }

    /**
     * 活动详情（api.md §5.3）：公开状态人人可看；
     * 待审核/已驳回/已取消仅创建者与管理员可看，其余按"不存在"处理（不泄露存在性）。
     */
    @Transactional(readOnly = true)
    public ActivityVO detail(LoginUser me, Long activityId) {
        ActivityVO vo = activityMapper.findVOById(activityId);
        if (vo == null) {
            throw new BusinessException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        if (!PUBLIC_STATUSES.contains(vo.getStatus())) {
            boolean isCreator = me != null && me.userId().equals(vo.getCreatorId());
            if (!isCreator && !isAdmin(me)) {
                throw new BusinessException(ResultCode.ACTIVITY_NOT_FOUND);
            }
        }
        vo.setCourtIds(reservationMapper.findCourtIdsByActivityId(activityId));
        return vo;
    }

    /** 我创建的活动（api.md §5.4）：含全部状态。 */
    @Transactional(readOnly = true)
    public PageResult<ActivityItemVO> listMine(Long userId, Integer status, int page, int pageSize) {
        long total = activityMapper.countByCreator(userId, status);
        List<ActivityItemVO> list = activityMapper.listByCreator(userId, status,
                (page - 1) * pageSize, pageSize);
        return new PageResult<>(list, total, page, pageSize);
    }

    /** 管理员待审队列（api.md §5.6）：先创建先审。 */
    @Transactional(readOnly = true)
    public PageResult<ActivityItemVO> listPending(LoginUser me, int page, int pageSize) {
        requireAdmin(me);
        long total = activityMapper.countPending();
        List<ActivityItemVO> list = activityMapper.listPending((page - 1) * pageSize, pageSize);
        return new PageResult<>(list, total, page, pageSize);
    }

    // ==================== 审核 ====================

    /**
     * 审核通过（api.md §5.7，business-flows 动作 6 通过分支）。
     *
     * <p>只改活动本身的状态与审核痕迹：场地继续被占用、创建者报名继续生效 —— 一步到位，
     * 不再产生任何写入。</p>
     */
    @Transactional
    public ActivityVO approve(LoginUser me, Long activityId) {
        requireAdmin(me);
        Activity activity = activityMapper.findByIdForUpdate(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        // 条件更新保证并发下只处理一次：已被处理过则影响 0 行
        int affected = activityMapper.approve(activityId, me.userId(), LocalDateTime.now());
        if (affected == 0) {
            throw new BusinessException(ResultCode.ACTIVITY_STATE_INVALID, "该活动已被处理");
        }
        return loadDetailVO(activityId);
    }

    /**
     * 审核驳回（api.md §5.8，business-flows 动作 6 驳回分支）。
     *
     * <p>驳回是"活动的终点"：同一事务内释放其全部占场 + 作废全部报名，
     * 让场地立刻回到可约池。驳回后不可再次送审，想再办需重新创建（Q3 决策）。</p>
     */
    @Transactional
    public ActivityVO reject(LoginUser me, Long activityId, String rejectReason) {
        requireAdmin(me);
        Activity activity = activityMapper.findByIdForUpdate(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        int affected = activityMapper.reject(activityId, me.userId(), LocalDateTime.now(), rejectReason);
        if (affected == 0) {
            throw new BusinessException(ResultCode.ACTIVITY_STATE_INVALID, "该活动已被处理");
        }
        releaseResources(activityId);
        return loadDetailVO(activityId);
    }

    // ==================== 取消 ====================

    /** 创建者取消活动（api.md §5.5，business-flows 动作 9）：仅限未开始。 */
    @Transactional
    public ActivityVO cancelByCreator(Long userId, Long activityId) {
        Activity activity = activityMapper.findByIdForUpdate(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        if (!userId.equals(activity.getCreatorId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有活动创建者可以取消该活动");
        }
        // 只有"待审核 / 已发布"可取消；已驳回、已结束、已取消都不行
        if (activity.getStatus() != 0 && activity.getStatus() != 1) {
            throw new BusinessException(ResultCode.ACTIVITY_STATE_INVALID);
        }
        // 创建者取消受"未开始"约束（管理员取消不受此限，见 cancelByAdmin）
        if (!LocalDateTime.now().isBefore(activity.getStartTime())) {
            throw new BusinessException(ResultCode.ACTIVITY_STATE_INVALID, "活动已开始，不可取消");
        }
        return doCancel(activity);
    }

    /** 管理员取消活动（api.md §5.9，business-flows 动作 10）：不受"未开始"限制。 */
    @Transactional
    public ActivityVO cancelByAdmin(LoginUser me, Long activityId) {
        requireAdmin(me);
        Activity activity = activityMapper.findByIdForUpdate(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        if (activity.getStatus() != 0 && activity.getStatus() != 1) {
            throw new BusinessException(ResultCode.ACTIVITY_STATE_INVALID);
        }
        return doCancel(activity);
    }

    /** 取消的共同动作：置活动状态 + 释放场地 + 作废报名（三者同一事务）。 */
    private ActivityVO doCancel(Activity activity) {
        activityMapper.markCancelled(activity.getId());
        releaseResources(activity.getId());
        return loadDetailVO(activity.getId());
    }

    // ==================== 内部工具 ====================

    /**
     * 释放某活动的场地并作废其全部报名。
     * 驳回与取消共用 —— 两者对"场地/报名"的处置完全一致，只有活动自身状态不同（2 vs 3）。
     */
    private void releaseResources(Long activityId) {
        LocalDateTime now = LocalDateTime.now();
        reservationMapper.cancelByActivityId(activityId, now);   // 场地回到可约池
        registrationMapper.cancelByActivityId(activityId);        // 名额全部作废
    }

    /** 读取详情 VO（不带权限判定，供写操作返回最新状态用；调用方已完成鉴权）。 */
    private ActivityVO loadDetailVO(Long activityId) {
        ActivityVO vo = activityMapper.findVOById(activityId);
        if (vo == null) {
            throw new BusinessException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        vo.setCourtIds(reservationMapper.findCourtIdsByActivityId(activityId));
        return vo;
    }

    private boolean isAdmin(LoginUser me) {
        return me != null && me.role() != null && me.role() == 1;
    }

    /** 管理员专属操作的统一入口校验（认证由拦截器完成，授权由 Service 判定，ADR-0007）。 */
    private void requireAdmin(LoginUser me) {
        if (!isAdmin(me)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }
}
