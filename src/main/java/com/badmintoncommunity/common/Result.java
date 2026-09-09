package com.badmintoncommunity.common;

import lombok.Getter;

/**
 * 统一响应体 Result&lt;T&gt;（ADR-0009）。
 *
 * <p>code=0 表示成功；失败使用 {@link ResultCode} 定义的业务错误码。
 * HTTP 状态码策略遵循 api.md 第 10 节。</p>
 */
@Getter
public class Result<T> {

    /** 成功码 */
    public static final String SUCCESS_CODE = "0";

    private final String code;
    private final String message;
    private final T data;

    private Result(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok() {
        return new Result<>(SUCCESS_CODE, "success", null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS_CODE, "success", data);
    }

    public static <T> Result<T> fail(ResultCode rc) {
        return new Result<>(rc.getCode(), rc.getMessage(), null);
    }

    public static <T> Result<T> fail(ResultCode rc, String message) {
        return new Result<>(rc.getCode(), message, null);
    }
}
