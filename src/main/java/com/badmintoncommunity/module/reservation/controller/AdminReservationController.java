package com.badmintoncommunity.module.reservation.controller;

import com.badmintoncommunity.common.Result;
import com.badmintoncommunity.module.reservation.dto.ReservationVO;
import com.badmintoncommunity.module.reservation.service.ReservationService;
import com.badmintoncommunity.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人预约接口 · 管理员侧（api.md 第 8 章，requirements 规则 11）。
 *
 * <p>为什么单独一个 Controller 而不是塞进 {@code ReservationController}：
 * 路径前缀不同（{@code /api/admin/reservations} vs {@code /api/reservations}），
 * 且管理端接口的授权要求完全不同 —— 分开更不容易搞混。</p>
 */
@RestController
@RequestMapping("/api/admin/reservations")
@RequiredArgsConstructor
public class AdminReservationController {

    private final ReservationService reservationService;

    /**
     * 强制取消个人预约（POST /api/admin/reservations/{id}/cancel）。
     * 不受"开场前 4 小时"限制；活动占场行不走此接口。
     */
    @PostMapping("/{id}/cancel")
    public Result<ReservationVO> cancel(@PathVariable Long id) {
        return Result.ok(reservationService.cancelByAdmin(UserContext.get(), id));
    }
}
