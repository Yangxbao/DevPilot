package com.devpilot.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一响应结构：{ code, message, data }。
 * code=0 表示成功；非 0 见 {@link ErrorCode}。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    public static <T> ApiResponse<T> of(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null);
    }
}
