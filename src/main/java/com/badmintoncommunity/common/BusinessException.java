package com.badmintoncommunity.common;

import lombok.Getter;

/**
 * 业务异常：由 Service 层抛出，GlobalExceptionHandler 统一转换为 Result。
 *
 * <p>规则判定（时间窗口 / 冲突 / 状态机 / 满员 / 权限归属）全部在 Service 层完成并
 * 通过本异常携带错误码返回（business-flows.md / api.md §12）。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ResultCode resultCode;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }
}
