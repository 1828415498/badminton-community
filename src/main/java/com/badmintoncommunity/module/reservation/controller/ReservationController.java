package com.badmintoncommunity.module.reservation.controller;

import com.badmintoncommunity.common.PageResult;
import com.badmintoncommunity.common.Result;
import com.badmintoncommunity.module.reservation.dto.CreateReservationRequest;
import com.badmintoncommunity.module.reservation.dto.ReservationVO;
import com.badmintoncommunity.module.reservation.service.ReservationService;
import com.badmintoncommunity.security.LoginUser;
import com.badmintoncommunity.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 个人预约接口（api.md §4）。全部需登录（个人预约只操作自己的资源）。
 */
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /** 创建个人球场预约（POST /api/reservations） */
    @PostMapping
    public Result<ReservationVO> create(@Valid @RequestBody CreateReservationRequest req) {
        return Result.ok(reservationService.create(UserContext.requireUserId(), req));
    }

    /** 我的预约（GET /api/reservations/mine?status=&start_from=&page=&page_size=） */
    @GetMapping("/mine")
    public Result<PageResult<ReservationVO>> mine(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startFrom,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(reservationService.listMine(UserContext.requireUserId(), status, startFrom,
                Math.max(page, 1), Math.min(Math.max(pageSize, 1), 50)));
    }

    /** 预约详情（GET /api/reservations/{id}）：本人或管理员 */
    @GetMapping("/{id}")
    public Result<ReservationVO> detail(@PathVariable Long id) {
        LoginUser me = UserContext.get();
        return Result.ok(reservationService.detail(me, id));
    }

    /** 取消个人预约（POST /api/reservations/{id}/cancel） */
    @PostMapping("/{id}/cancel")
    public Result<ReservationVO> cancel(@PathVariable Long id) {
        return Result.ok(reservationService.cancel(UserContext.requireUserId(), id));
    }
}
