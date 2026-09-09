package com.badmintoncommunity.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理（ADR-0009：Controller 不手工拼装返回对象）。
 *
 * <p>业务失败按 HTTP 状态码策略返回：业务规则类走 200+code，
 * 参数格式 400 / 认证 401 / 授权 403 / 未预期 500。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        ResultCode rc = e.getResultCode();
        return ResponseEntity.status(rc.getHttpStatus())
                .body(Result.fail(rc, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + defaultMessage(f))
                .orElse(ResultCode.PARAM_INVALID.getMessage());
        return ResponseEntity.badRequest().body(Result.fail(ResultCode.PARAM_INVALID, message));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<Result<Void>> handleBadRequest(Exception e) {
        return ResponseEntity.badRequest().body(Result.fail(ResultCode.PARAM_INVALID));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception e) {
        log.error("unhandled exception", e);
        return ResponseEntity.status(500).body(Result.fail(ResultCode.INTERNAL_ERROR));
    }

    private String defaultMessage(FieldError f) {
        return f.getDefaultMessage() != null ? f.getDefaultMessage() : "不合法";
    }
}
