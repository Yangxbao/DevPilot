package com.devpilot.common.exception;

import com.devpilot.common.api.ApiResponse;
import com.devpilot.common.api.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理：统一转换为 ApiResponse 结构。
 * 对外消息做净化（不泄漏内部堆栈），完整堆栈只进日志。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        return ResponseEntity.status(e.getErrorCode().getHttpStatus())
                .body(ApiResponse.of(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(BindException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getHttpStatus())
                .body(new ApiResponse<>(ErrorCode.BAD_REQUEST.getCode(), "参数校验失败", errors));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getHttpStatus())
                .body(ApiResponse.of(ErrorCode.BAD_REQUEST, "上传文件过大（单包上限 100MB）"));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getHttpStatus())
                .body(ApiResponse.of(ErrorCode.BAD_REQUEST, "请求参数类型不正确: " + e.getName()));
    }

    @ExceptionHandler({DuplicateKeyException.class})
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateKeyException e) {
        return ResponseEntity.status(ErrorCode.CONFLICT.getHttpStatus())
                .body(ApiResponse.of(ErrorCode.CONFLICT, "数据唯一性冲突（名称重复或任务重复提交）"));
    }

    @ExceptionHandler({DataIntegrityViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("数据完整性冲突: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.CONFLICT.getHttpStatus())
                .body(ApiResponse.of(ErrorCode.CONFLICT, "数据冲突，请检查关联数据后重试"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(ApiResponse.of(ErrorCode.FORBIDDEN, "无权限访问该资源"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus())
                .body(ApiResponse.of(ErrorCode.NOT_FOUND, "接口或资源不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(ErrorCode.SYSTEM_ERROR.getHttpStatus())
                .body(ApiResponse.of(ErrorCode.SYSTEM_ERROR, "系统繁忙，请稍后重试"));
    }
}
