package com.badmintoncommunity.module.venue.service;

import com.badmintoncommunity.common.BusinessException;
import com.badmintoncommunity.common.ResultCode;
import com.badmintoncommunity.module.reservation.dto.OccupancySlot;
import com.badmintoncommunity.module.reservation.mapper.ReservationMapper;
import com.badmintoncommunity.module.venue.dto.CourtCreateRequest;
import com.badmintoncommunity.module.venue.dto.CourtUpdateRequest;
import com.badmintoncommunity.module.venue.dto.CourtVO;
import com.badmintoncommunity.module.venue.dto.VenueVO;
import com.badmintoncommunity.module.venue.entity.Court;
import com.badmintoncommunity.module.venue.entity.Venue;
import com.badmintoncommunity.module.venue.mapper.CourtMapper;
import com.badmintoncommunity.module.venue.mapper.VenueMapper;
import com.badmintoncommunity.security.LoginUser;
import com.badmintoncommunity.security.UserContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 场馆与球场业务（对应 api.md §3，business-flows 动作 12 的球场管理）。
 *
 * <h2>这个 Service 在做的业务判断（3 句话版）</h2>
 * 1. 普通公开查询：看场馆列表、看某场馆的球场、看某球场某天被谁占用了哪些时段；
 * 2. 管理员才能操作：新增/改名球场、把球场置为"不可预约"；
 * 3. 置为不可预约前必须先锁球场行并检查"未来还有没有有效预约"——有就拒绝，防止把别人已约的场停掉。
 */
@Service
@RequiredArgsConstructor
public class VenueService {

    private final VenueMapper venueMapper;
    private final CourtMapper courtMapper;
    private final ReservationMapper reservationMapper;

    // ---------- 公开只读 ----------

    /** 场馆列表（V1 只有一行，api.md §3.1） */
    @Transactional(readOnly = true)
    public List<VenueVO> listVenues() {
        return venueMapper.findAll().stream()
                .map(VenueVO::from)
                .collect(Collectors.toList());
    }

    /** 某场馆下的球场列表，可选 status 过滤（api.md §3.2） */
    @Transactional(readOnly = true)
    public List<CourtVO> listCourts(Long venueId, Integer status) {
        if (venueMapper.findById(venueId) == null) {
            throw new BusinessException(ResultCode.VENUE_NOT_FOUND);
        }
        //这一段是 返回一个球场列表然后对这个列表中的每一个元素进行处理
        //先变成Stream 为了对里面的每一个元素进行处理 接着对里面的每一个元素进行from方法 最后这个collect是为了把他们从Stream重新
        //变回List类型
        return courtMapper.findByVenue(venueId, status).stream()
                .map(CourtVO::from)
                .collect(Collectors.toList());
    }
    /** 普通写法
     * @Transactional(readOnly = true)
     * public List<VenueVO> listVenues() {
     *
     *     List<Venue> venues = venueMapper.findAll();
     *
     *     List<VenueVO> result = new ArrayList<>();
     *
     *     for (Venue venue : venues) {
     *         VenueVO vo = VenueVO.from(venue);
     *         result.add(vo);
     *     }
     *
     *     return result;
     * }
     */

    /** 某球场某天的占用事实（api.md §3.3）。只回"事实"，可约性判定在预约创建处。 */
    @Transactional(readOnly = true)
    public CourtOccupancyVO getOccupancy(Long courtId, String dateStr) {
        LocalDate date;//这里因为用户传进来的是字符串形式 所以要解析成日期形式
        try {//因为传进来是字符串 所以有可能解析不出来 这时候转换成业务错误
            date = LocalDate.parse(dateStr);
        } catch (RuntimeException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "date 格式应为 yyyy-MM-dd");
        }
        if (courtMapper.findById(courtId) == null) {
            throw new BusinessException(ResultCode.COURT_NOT_FOUND);
        }
        List<OccupancySlot> occupied = reservationMapper.findEffectiveByCourtAndRange(
                courtId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());

        CourtOccupancyVO vo = new CourtOccupancyVO();
        vo.setCourtId(courtId);
        vo.setDate(date);
        vo.setOccupied(occupied);
        return vo;
    }

    // ---------- 管理员操作 ----------

    /** 新增球场（默认可预约，api.md §3.4） */
    @Transactional
    public CourtVO createCourt(CourtCreateRequest req) {
        requireAdmin();//权限检验操作
        if (venueMapper.findById(req.getVenueId()) == null) {
            throw new BusinessException(ResultCode.VENUE_NOT_FOUND);
        }
        Court court = new Court();
        court.setVenueId(req.getVenueId());
        court.setName(req.getName());
        court.setStatus(1);
        try {
            courtMapper.insert(court);
        } catch (DuplicateKeyException e) {
            // 同场馆球场名重复由唯一索引兜底
            throw new BusinessException(ResultCode.COURT_NAME_DUPLICATED);
        }
        return CourtVO.from(court);
    }

    /** 修改球场名（api.md §3.5） */
    @Transactional
    public CourtVO renameCourt(Long courtId, CourtUpdateRequest req) {
        requireAdmin();
        if (courtMapper.findById(courtId) == null) {
            throw new BusinessException(ResultCode.COURT_NOT_FOUND);
        }
        try {
            courtMapper.rename(courtId, req.getName());
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.COURT_NAME_DUPLICATED);
        }
        return CourtVO.from(courtMapper.findById(courtId));
    }

    /**
     * 停用球场（api.md §3.6）。
     * 事务内含行锁：先锁 court 行，再查未来有效占用，最后改状态——锁内检查保证并发正确。
     */
    @Transactional
    public CourtVO disableCourt(Long courtId) {
        requireAdmin();
        Court court = courtMapper.findByIdForUpdate(courtId); // 排他行锁
        if (court == null) {
            throw new BusinessException(ResultCode.COURT_NOT_FOUND);
        }
        // 存在未来有效占用（个人或活动来源）时禁止停用
        if (reservationMapper.countFutureEffective(courtId, LocalDateTime.now()) > 0) {
            throw new BusinessException(ResultCode.ACTIVE_RESERVATION_EXISTS);
        }
        courtMapper.updateStatus(courtId, 2);
        return CourtVO.from(courtMapper.findById(courtId));
    }

    /** 恢复球场可预约（api.md §3.7） */
    @Transactional
    public CourtVO enableCourt(Long courtId) {
        requireAdmin();
        if (courtMapper.findById(courtId) == null) {
            throw new BusinessException(ResultCode.COURT_NOT_FOUND);
        }
        courtMapper.updateStatus(courtId, 1);
        return CourtVO.from(courtMapper.findById(courtId));
    }

    // ---------- 内部工具 ----------

    /** 管理员权限校验（认证与授权分离：认证看 token，授权在这里看角色） */
    private void requireAdmin() {
        LoginUser user = UserContext.get();
        if (user == null || user.role() == null || user.role() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    /** 球场占用查询的响应结构（api.md §3.3） */
    @Data
    public static class CourtOccupancyVO {
        private Long courtId;
        private LocalDate date;
        private List<OccupancySlot> occupied;
    }
}
