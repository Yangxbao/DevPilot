package com.devpilot.common.api;

import lombok.Getter;

/**
 * 业务错误码。约定：HTTP 状态码与业务 code 同时返回给前端。
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, 200, "ok"),
    BAD_REQUEST(40001, 400, "参数错误"),
    UNAUTHORIZED(40100, 401, "未认证或登录已过期"),
    FORBIDDEN(40300, 403, "无权限访问该资源"),
    NOT_FOUND(40400, 404, "资源不存在"),
    CONFLICT(40900, 409, "状态冲突"),
    TOO_MANY_REQUESTS(42900, 429, "请求过于频繁"),
    SYSTEM_ERROR(50000, 500, "系统内部错误"),
    UPSTREAM_ERROR(50200, 502, "上游 AI 服务不可用");

    private final int code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}
