package com.badmintoncommunity.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码总表（与 api.md 第 10 节一致）。
 *
 * <p>code 为业务码；httpStatus 表示该错误对应到 HTTP 层的状态码（200 承载业务失败，
 * 400/401/403 用于参数格式 / 认证 / 授权）。</p>
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // ---------- 业务失败（HTTP 200 + code，由 api.md §10 策略承载） ----------
    USERNAME_TAKEN(200, "USERNAME_TAKEN", "用户名已存在"),
    INVALID_CREDENTIALS(200, "INVALID_CREDENTIALS", "用户名或密码错误"),
    INVALID_TIME(200, "INVALID_TIME", "时间不合法"),
    BOOKING_WINDOW_EXCEEDED(200, "BOOKING_WINDOW_EXCEEDED", "超出未来 7 个自然日预约窗口"),
    COURT_NOT_FOUND(200, "COURT_NOT_FOUND", "球场不存在"),
    COURT_UNAVAILABLE(200, "COURT_UNAVAILABLE", "球场不可预约"),
    COURT_ALREADY_RESERVED(200, "COURT_ALREADY_RESERVED", "该球场该时段已被预约"),
    PERSONAL_RESERVATION_LIMIT_EXCEEDED(200, "PERSONAL_RESERVATION_LIMIT_EXCEEDED",
            "同一用户重叠时段的有效个人预约已达上限"),
    RESERVATION_NOT_FOUND(200, "RESERVATION_NOT_FOUND", "预约不存在或不是本人预约"),
    CANCEL_WINDOW_EXCEEDED(200, "CANCEL_WINDOW_EXCEEDED", "开场前不足 4 小时，不可自行取消"),
    ACTIVE_RESERVATION_EXISTS(200, "ACTIVE_RESERVATION_EXISTS", "球场存在未来有效预约，禁止停用"),
    COURT_NAME_DUPLICATED(200, "COURT_NAME_DUPLICATED", "同场馆球场名已存在"),
    VENUE_NOT_FOUND(200, "VENUE_NOT_FOUND", "场馆不存在"),
    ACTIVITY_NOT_FOUND(200, "ACTIVITY_NOT_FOUND", "活动不存在或无权查看"),
    ACTIVITY_STATE_INVALID(200, "ACTIVITY_STATE_INVALID", "活动状态不允许该操作"),
    ACTIVITY_FULL(200, "ACTIVITY_FULL", "活动已满员"),
    REGISTRATION_WINDOW_CLOSED(200, "REGISTRATION_WINDOW_CLOSED", "报名窗口已关闭"),
    NOT_REGISTERED(200, "NOT_REGISTERED", "未报名该活动或已取消"),
    POST_NOT_FOUND(200, "POST_NOT_FOUND", "帖子不存在"),

    // ---------- 传输/格式/认证/授权层 ----------
    PARAM_INVALID(400, "PARAM_INVALID", "参数不合法"),
    UNAUTHORIZED(401, "UNAUTHORIZED", "未认证或登录已失效"),
    FORBIDDEN(403, "FORBIDDEN", "无权限执行该操作"),

    // ---------- 未预期异常 ----------
    INTERNAL_ERROR(500, "INTERNAL_ERROR", "系统繁忙，请稍后重试");

    /** HTTP 状态码 */
    private final int httpStatus;
    /** 业务码（响应体 code） */
    private final String code;
    /** 默认提示文案 */
    private final String message;
}
