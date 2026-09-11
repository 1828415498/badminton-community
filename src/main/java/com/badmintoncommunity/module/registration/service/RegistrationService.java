package com.badmintoncommunity.module.registration.service;

import com.badmintoncommunity.common.BusinessException;
import com.badmintoncommunity.common.PageResult;
import com.badmintoncommunity.common.ResultCode;
import com.badmintoncommunity.module.activity.dto.ActivityItemVO;
import com.badmintoncommunity.module.activity.dto.ActivityVO;
import com.badmintoncommunity.module.activity.entity.Activity;
import com.badmintoncommunity.module.activity.mapper.ActivityMapper;
import com.badmintoncommunity.module.registration.dto.ActivityMemberVO;
import com.badmintoncommunity.module.registration.dto.MyRegistrationRow;
import com.badmintoncommunity.module.registration.dto.MyRegistrationVO;
import com.badmintoncommunity.module.registration.dto.RegistrationVO;
import com.badmintoncommunity.module.registration.entity.Registration;
import com.badmintoncommunity.module.registration.mapper.RegistrationMapper;
import com.badmintoncommunity.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动报名业务（api.md §6，business-flows 动作 7/8）。
 *
 * <h2>这个 Service 在做的业务（3 句话版）</h2>
 * <ol>
 *   <li>用户报名已发布的活动，系统要拦住三种情况：活动没发布、活动已开始、名额已满；</li>
 *   <li>报名是"一人一条记录"：取消过再报名，是把老记录恢复，绝不新增第二行；</li>
 *   <li>抢最后一个名额时，靠锁活动行保证只有一个人成功，其余全部收到"已满员"。</li>
 * </ol>
 *
 * <h2>为什么报名必须锁 activity 行</h2>
 * <p>"还剩 1 个名额"是一个<b>读出来的数字</b>（count status=1 的行）。如果两个用户同时读到
 * "还剩 1 个"，各自都判定"我能报"，然后双双插入 —— 实际报名数就超了 max_members。
 * 加上 activity 行锁后，两个报名被强行排队：先到的插完提交，后到的重新计数发现已满 → 拒绝。
 * <b>锁 → 计数 → 插入</b> 三步在同一事务里，是防超卖的唯一正确姿势。</p>
 */
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final RegistrationMapper registrationMapper;
    private final ActivityMapper activityMapper;

    /** 公开可见的活动状态：1=已发布，4=已结束（与 ActivityService 一致） */
    private static final List<Integer> PUBLIC_STATUSES = List.of(1, 4);

    // ==================== 报名 / 取消报名 ====================

    /**
     * 报名活动（api.md §6.1，business-flows 动作 7）。幂等：
     * 已报名再点一次返回原记录；取消过的用户重新报名则恢复老记录。
     */
    @Transactional
    public RegistrationVO register(Long userId, Long activityId) {
        // ① 锁活动行 —— 本方法并发正确性的全部来源
        Activity activity = activityMapper.findByIdForUpdate(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.ACTIVITY_NOT_FOUND);
        }

        // ② 状态与报名窗口校验
        if (activity.getStatus() == 4) {
            throw new BusinessException(ResultCode.REGISTRATION_WINDOW_CLOSED, "活动已结束");
        }
        if (activity.getStatus() != 1) {
            // 待审核 / 已驳回 / 已取消都不允许报名
            throw new BusinessException(ResultCode.ACTIVITY_STATE_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(activity.getStartTime())) {
            throw new BusinessException(ResultCode.REGISTRATION_WINDOW_CLOSED, "活动已开始，报名通道关闭");
        }

        // ③ 已有有效报名 → 幂等成功，不重复占名额
        Registration existing = registrationMapper.findByUserAndActivity(userId, activityId);
        if (existing != null && existing.getStatus() == 1) {
            return toVO(existing);
        }

        // ④ 满员校验：必须在 activity 行锁内计数（此时并发的报名已被串行化）
        long active = registrationMapper.countActive(activityId);
        if (active >= activity.getMaxMembers()) {
            throw new BusinessException(ResultCode.ACTIVITY_FULL);
        }

        // ⑤ 曾取消过 → 恢复老行；从未报名 → 新建（唯一约束保证不会出现第二行）
        if (existing != null) {
            registrationMapper.reactivate(existing.getId());
            existing.setStatus(1);
            return toVO(existing);
        }
        Registration registration = new Registration();
        registration.setUserId(userId);
        registration.setActivityId(activityId);
        registration.setStatus(1);
        registrationMapper.insert(registration);
        return toVO(registration);
    }

    /**
     * 取消报名（api.md §6.2，business-flows 动作 8）。
     *
     * <p>此处<b>刻意不加 activity 行锁</b>：取消只会让名额变多，不会造成超卖。
     * 最坏情况是"取消与抢最后名额并发"时，新报名读到取消前的计数而被保守拒绝 ——
     * 这是一个安全方向的取舍（宁可少卖，不可超卖）。</p>
     */
    @Transactional
    public RegistrationVO cancelRegistration(Long userId, Long activityId) {
        Activity activity = activityMapper.findById(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        // 活动已结束 / 已取消 / 已驳回时，报名已由系统级联作废，本接口不适用
        if (activity.getStatus() == 4) {
            throw new BusinessException(ResultCode.ACTIVITY_STATE_INVALID, "活动已结束，无法取消报名");
        }
        if (activity.getStatus() == 2 || activity.getStatus() == 3) {
            throw new BusinessException(ResultCode.ACTIVITY_STATE_INVALID, "活动已被驳回或取消");
        }

        int affected = registrationMapper.cancel(userId, activityId);
        if (affected == 0) {
            // 未报名 / 已取消 / 从未报名，统一按"没报上名"处理
            throw new BusinessException(ResultCode.NOT_REGISTERED);
        }
        return toVO(registrationMapper.findByUserAndActivity(userId, activityId));
    }

    // ==================== 查询 ====================

    /**
     * 活动成员列表（api.md §6.3）。可见性跟随活动状态：
     * 公开状态（已发布/已结束）所有登录用户可看，其余仅创建者与管理员。
     */
    @Transactional(readOnly = true)
    public PageResult<ActivityMemberVO> listMembers(LoginUser me, Long activityId,
                                                    int page, int pageSize) {
        ActivityVO activity = activityMapper.findVOById(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        if (!PUBLIC_STATUSES.contains(activity.getStatus())) {
            boolean isCreator = me != null && me.userId().equals(activity.getCreatorId());
            if (!isCreator && !isAdmin(me)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该活动的成员");
            }
        }
        long total = registrationMapper.countActive(activityId);
        List<ActivityMemberVO> list = registrationMapper.listActiveMembers(activityId,
                (page - 1) * pageSize, pageSize);
        return new PageResult<>(list, total, page, pageSize);
    }

    /** 我参加的活动（api.md §6.4）：默认只看有效报名，可指定 status=2 查看已取消的。 */
    @Transactional(readOnly = true)
    public PageResult<MyRegistrationVO> listMine(Long userId, Integer status,
                                                 int page, int pageSize) {
        long total = registrationMapper.countMine(userId, status);
        List<MyRegistrationRow> rows = registrationMapper.listMine(userId, status,
                (page - 1) * pageSize, pageSize);
        List<MyRegistrationVO> list = rows.stream().map(this::toMyVO).toList();
        return new PageResult<>(list, total, page, pageSize);
    }

    // ==================== 内部工具 ====================

    /** 平铺行 → api.md §6.4 要求的「报名 + 活动」嵌套结构。 */
    private MyRegistrationVO toMyVO(MyRegistrationRow row) {
        MyRegistrationVO vo = new MyRegistrationVO();

        MyRegistrationVO.RegInfo reg = new MyRegistrationVO.RegInfo();
        reg.setId(row.getRegistrationId());
        reg.setStatus(row.getRegistrationStatus());
        reg.setCreateTime(row.getRegistrationCreateTime());
        vo.setRegistration(reg);

        ActivityItemVO activity = new ActivityItemVO();
        activity.setId(row.getActivityId());
        activity.setTitle(row.getTitle());
        activity.setCreatorId(row.getCreatorId());
        activity.setCreatorNickname(row.getCreatorNickname());
        activity.setStartTime(row.getStartTime());
        activity.setEndTime(row.getEndTime());
        activity.setMaxMembers(row.getMaxMembers());
        activity.setJoinedCount(row.getJoinedCount());
        activity.setStatus(row.getActivityStatus());
        vo.setActivity(activity);

        return vo;
    }

    private RegistrationVO toVO(Registration registration) {
        RegistrationVO vo = new RegistrationVO();
        vo.setId(registration.getId());
        vo.setUserId(registration.getUserId());
        vo.setActivityId(registration.getActivityId());
        vo.setStatus(registration.getStatus());
        return vo;
    }

    private boolean isAdmin(LoginUser me) {
        return me != null && me.role() != null && me.role() == 1;
    }
}
