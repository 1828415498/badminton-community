package com.badmintoncommunity.module.venue.controller;

import com.badmintoncommunity.common.Result;
import com.badmintoncommunity.module.venue.dto.CourtCreateRequest;
import com.badmintoncommunity.module.venue.dto.CourtUpdateRequest;
import com.badmintoncommunity.module.venue.dto.CourtVO;
import com.badmintoncommunity.module.venue.service.VenueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员球场管理接口（api.md §8 管理端，详情见 §3.4-§3.7）。
 *
 * <p>路径在 /api/admin/* 下，需要登录；"是不是管理员"由 Service 校验（FORBIDDEN）。</p>
 */
@RestController
@RequestMapping("/api/admin/courts")
@RequiredArgsConstructor
public class AdminCourtController {

    private final VenueService venueService;

    /** 新增球场（POST /api/admin/courts），默认 status=1 可预约 */
    @PostMapping
    public Result<CourtVO> createCourt(@Valid @RequestBody CourtCreateRequest req) {
        return Result.ok(venueService.createCourt(req));
    }

    /** 改名（PUT /api/admin/courts/{courtId}） */
    @PutMapping("/{courtId}")
    public Result<CourtVO> renameCourt(@PathVariable Long courtId,
                                       @Valid @RequestBody CourtUpdateRequest req) {
        return Result.ok(venueService.renameCourt(courtId, req));
    }

    /** 停用（PUT /api/admin/courts/{courtId}/disable），有未来有效预约则拒绝 */
    @PutMapping("/{courtId}/disable")
    public Result<CourtVO> disableCourt(@PathVariable Long courtId) {
        return Result.ok(venueService.disableCourt(courtId));
    }

    /** 恢复可预约（PUT /api/admin/courts/{courtId}/enable） */
    @PutMapping("/{courtId}/enable")
    public Result<CourtVO> enableCourt(@PathVariable Long courtId) {
        return Result.ok(venueService.enableCourt(courtId));
    }
}
