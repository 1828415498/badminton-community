package com.badmintoncommunity.module.venue.controller;

import com.badmintoncommunity.common.Result;
import com.badmintoncommunity.module.venue.dto.CourtVO;
import com.badmintoncommunity.module.venue.dto.VenueVO;
import com.badmintoncommunity.module.venue.service.VenueService;
import com.badmintoncommunity.module.venue.service.VenueService.CourtOccupancyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 场馆/球场公开只读接口（api.md §3.1-§3.3）。匿名可访问（见 WebConfig 白名单）。
 *
 * <p>Controller 只做：收参数 → 调 Service → 包 Result。没有业务逻辑。</p>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VenueController {

    private final VenueService venueService;

    /** 场馆列表（GET /api/venues） */
    @GetMapping("/venues")
    public Result<List<VenueVO>> listVenues() {
        return Result.ok(venueService.listVenues());
    }

    /** 球场列表（GET /api/venues/{venueId}/courts?status=1） */
    @GetMapping("/venues/{venueId}/courts")
    public Result<List<CourtVO>> listCourts(@PathVariable Long venueId,
                                            @RequestParam(required = false) Integer status) {
        return Result.ok(venueService.listCourts(venueId, status));
    }

    /** 球场某天预约情况（GET /api/courts/{courtId}/occupancy?date=2026-09-09） */
    @GetMapping("/courts/{courtId}/occupancy")
    public Result<CourtOccupancyVO> occupancy(@PathVariable Long courtId,
                                              @RequestParam String date) {
        return Result.ok(venueService.getOccupancy(courtId, date));
    }
}
