package com.materin.tech.common.exception;

import com.materin.tech.common.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常兜底：业务异常返回明确错误信息，
 * 未知异常只记录详细日志、对外不泄露堆栈。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        log.warn("biz error: code={}, message={}", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null ? "参数校验失败" : fieldError.getField() + " " + fieldError.getDefaultMessage();
        return R.fail(R.Code.BAD_REQUEST, message);
    }

    /** 非法参数（如聚合函数/窗口越界）属客户端错误，返回 400 而非 500。 */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("illegal argument: {}", e.getMessage());
        return R.fail(R.Code.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleUnknown(Exception e) {
        log.error("unexpected error", e);
        return R.fail("系统繁忙，请稍后重试");
    }
}
