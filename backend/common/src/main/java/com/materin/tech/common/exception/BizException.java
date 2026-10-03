package com.materin.tech.common.exception;

import lombok.Getter;

/**
 * 业务异常：可预期的失败（参数不合法、资源不存在、状态冲突等），
 * 由 GlobalExceptionHandler 统一转换为 R.fail 响应。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(409, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException notFound(String message) {
        return new BizException(404, message);
    }

    public static BizException badRequest(String message) {
        return new BizException(400, message);
    }
}
