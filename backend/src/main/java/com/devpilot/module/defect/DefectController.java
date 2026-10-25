package com.devpilot.module.defect;

import com.devpilot.common.api.ApiResponse;
import com.devpilot.common.api.PageResult;
import com.devpilot.module.defect.dto.DefectCommentVO;
import com.devpilot.module.defect.dto.DefectCreateRequest;
import com.devpilot.module.defect.dto.DefectUpdateRequest;
import com.devpilot.module.defect.dto.DefectVO;
import com.devpilot.module.defect.dto.StatusChangeRequest;
import com.devpilot.module.defect.dto.StatusChangeResponse;
import com.devpilot.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 缺陷任务接口。
 */
@Tag(name = "defect", description = "缺陷任务")
@RestController
@RequiredArgsConstructor
public class DefectController {

    private final DefectService defectService;

    @Operation(summary = "创建缺陷")
    @PostMapping("/api/v1/projects/{id}/defects")
    public ApiResponse<DefectVO> create(@PathVariable Long id, @Valid @RequestBody DefectCreateRequest request) {
        return ApiResponse.ok(defectService.create(SecurityUtils.current(), id, request));
    }

    @Operation(summary = "缺陷分页列表")
    @GetMapping("/api/v1/projects/{id}/defects")
    public ApiResponse<PageResult<DefectVO>> list(@PathVariable Long id,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String severity,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(defectService.pageOf(SecurityUtils.current(), id, status, severity, keyword,
                page, Math.min(size, 100)));
    }

    @Operation(summary = "缺陷详情")
    @GetMapping("/api/v1/defects/{id}")
    public ApiResponse<DefectVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(defectService.detail(SecurityUtils.current(), id));
    }

    @Operation(summary = "更新缺陷字段")
    @PatchMapping("/api/v1/defects/{id}")
    public ApiResponse<DefectVO> update(@PathVariable Long id, @Valid @RequestBody DefectUpdateRequest request) {
        return ApiResponse.ok(defectService.update(SecurityUtils.current(), id, request));
    }

    @Operation(summary = "状态流转（服务端状态机校验）")
    @PostMapping("/api/v1/defects/{id}/status")
    public ApiResponse<StatusChangeResponse> changeStatus(@PathVariable Long id,
                                                          @Valid @RequestBody StatusChangeRequest request) {
        DefectVO defect = defectService.changeStatus(SecurityUtils.current(), id, request.status());
        List<String> allowed = switch (defect.status()) {
            case "OPEN" -> List.of("IN_PROGRESS");
            case "IN_PROGRESS" -> List.of("RESOLVED", "OPEN");
            case "RESOLVED" -> List.of("CLOSED", "OPEN");
            default -> List.of();
        };
        return ApiResponse.ok(new StatusChangeResponse(defect.id(), defect.status(), allowed));
    }

    @Operation(summary = "缺陷处理记录列表")
    @GetMapping("/api/v1/defects/{id}/comments")
    public ApiResponse<List<DefectCommentVO>> comments(@PathVariable Long id) {
        return ApiResponse.ok(defectService.comments(SecurityUtils.current(), id));
    }

    @Operation(summary = "添加处理记录")
    @PostMapping("/api/v1/defects/{id}/comments")
    public ApiResponse<DefectCommentVO> addComment(@PathVariable Long id,
                                                   @Valid @RequestBody CommentRequest request) {
        return ApiResponse.ok(defectService.addComment(SecurityUtils.current(), id, request.getContent()));
    }

    @Data
    public static class CommentRequest {
        @NotBlank
        @Size(max = 2000, message = "内容长度不超过 2000")
        private String content;
    }
}
