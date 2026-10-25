package com.devpilot.module.ai;

import com.devpilot.common.api.ApiResponse;
import com.devpilot.common.api.PageResult;
import com.devpilot.common.exception.BizException;
import com.devpilot.common.api.ErrorCode;
import com.devpilot.module.ai.dto.AnalysisVO;
import com.devpilot.module.ai.dto.BugAnalysisRequest;
import com.devpilot.module.ai.dto.QaRequest;
import com.devpilot.module.defect.Defect;
import com.devpilot.module.defect.DefectService;
import com.devpilot.module.project.ProjectService;
import com.devpilot.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 分析接口：问答、Bug 分析、记录查询、关联缺陷。
 */
@Tag(name = "ai", description = "AI 分析")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AiController {

    private final AiAnalysisService analysisService;
    private final ProjectService projectService;
    private final DefectService defectService;

    @Operation(summary = "代码库问答（同步，最长 90s）")
    @PostMapping("/projects/{id}/ai/qa")
    public ApiResponse<AnalysisVO> qa(@PathVariable Long id, @Valid @RequestBody QaRequest request) {
        return ApiResponse.ok(analysisService.qa(SecurityUtils.current(), id, request));
    }

    @Operation(summary = "Bug 根因辅助分析（同步）")
    @PostMapping("/projects/{id}/ai/bug-analysis")
    public ApiResponse<AnalysisVO> bugAnalysis(@PathVariable Long id, @Valid @RequestBody BugAnalysisRequest request) {
        return ApiResponse.ok(analysisService.bugAnalysis(SecurityUtils.current(), id, request));
    }

    @Operation(summary = "项目下的分析记录列表")
    @GetMapping("/projects/{id}/ai/analyses")
    public ApiResponse<PageResult<AnalysisVO>> list(@PathVariable Long id,
                                                    @RequestParam(required = false) String type,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(analysisService.pageOf(SecurityUtils.current(), id, type, page, Math.min(size, 50)));
    }

    @Operation(summary = "分析记录详情（含证据与执行轨迹）")
    @GetMapping("/ai/analyses/{id}")
    public ApiResponse<AnalysisVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(analysisService.detail(SecurityUtils.current(), id));
    }

    @Operation(summary = "将分析结果关联到缺陷任务")
    @PostMapping("/ai/analyses/{id}/link-defect")
    public ApiResponse<Void> linkDefect(@PathVariable Long id, @org.springframework.web.bind.annotation.RequestBody Map<String, Long> body) {
        Long defectId = body.get("defectId");
        if (defectId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "缺少 defectId");
        }
        AiAnalysis analysis = analysisService.requireById(id);
        projectService.requireAccessible(analysis.getProjectId(), SecurityUtils.current());
        Defect defect = defectService.requireById(defectId);
        if (!analysis.getProjectId().equals(defect.getProjectId())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "分析记录与缺陷不属于同一项目");
        }
        defect.setAnalysisId(analysis.getId());
        defectService.updateEntity(defect);
        return ApiResponse.ok();
    }
}
