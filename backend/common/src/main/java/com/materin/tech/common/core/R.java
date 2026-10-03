package com.materin.tech.common.core;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 统一 API 响应体。
 *
 * @param <T> 数据载荷类型
 */
@Schema(description = "统一响应包装：code=0 成功，非 0 为业务错误码")
@Getter
public class R<T> {

    @Schema(description = "业务状态码：0-成功 4xx-请求错误 5xx-服务错误", example = "0")
    private final int code;

    @Schema(description = "提示信息：成功为 success，失败为错误描述")
    private final String message;

    @Schema(description = "业务数据载荷（失败时为 null）")
    private final T data;

    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(Code.SUCCESS, "success", data);
    }

    public static <T> R<T> fail(String message) {
        return new R<>(Code.FAIL, message, null);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    /** 业务错误码常量，后续可按模块拆分错误码段。 */
    public interface Code {
        int SUCCESS = 0;
        int FAIL = 500;
        int NOT_FOUND = 404;
        int BAD_REQUEST = 400;
    }
}
