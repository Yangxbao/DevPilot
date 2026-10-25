package com.devpilot.module.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devpilot.common.api.ErrorCode;
import com.devpilot.common.exception.BizException;
import com.devpilot.module.ai.dto.AgentEvidenceDto;
import com.devpilot.module.ai.dto.AgentResultDto;
import com.devpilot.module.ai.dto.AgentStepDto;
import com.devpilot.module.ai.dto.AnalysisVO;
import com.devpilot.module.ai.dto.BugAnalysisRequest;
import com.devpilot.module.ai.dto.QaRequest;
import com.devpilot.module.ai.dto.StoredPayload;
import com.devpilot.module.ai.mapper.AiAnalysisMapper;
import com.devpilot.module.ai.mapper.AnalysisStepMapper;
import com.devpilot.module.project.CodeRepository;
import com.devpilot.module.project.ProjectService;
import com.devpilot.module.project.RepositoryService;
import com.devpilot.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 分析编排：建记录 -> 调用 Python Agent -> 落库结果与步骤轨迹。
 * 失败语义：AI 服务异常时记录置 FAILED 并保留失败原因，然后原样抛给前端。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final AiAnalysisMapper analysisMapper;
    private final AnalysisStepMapper stepMapper;
    private final AiIndexClient aiIndexClient;
    private final ProjectService projectService;
    private final RepositoryService repositoryService;
    private final ObjectMapper objectMapper;

    public AnalysisVO qa(UserPrincipal user, Long projectId, QaRequest request) {
        projectService.requireAccessible(projectId, user);
        CodeRepository repo = repositoryService.requireAccessible(request.repositoryId(), user);
        if (!repo.getProjectId().equals(projectId)) {
            throw BizException.badRequest("仓库不属于该项目");
        }
        AiAnalysis record = newRecord(projectId, repo.getId(), "QA", request.question(), null, user.id());
        try {
            AgentResultDto result = aiIndexClient.qa(repo.getId(), request.question(), 8);
            return persistSuccess(record, result);
        } catch (BizException e) {
            persistFailure(record, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("QA 未知异常 analysisId={}", record.getId(), e);
            persistFailure(record, "分析执行异常");
            throw new BizException(ErrorCode.UPSTREAM_ERROR, "AI 服务调用失败，请稍后重试");
        }
    }

    public AnalysisVO bugAnalysis(UserPrincipal user, Long projectId, BugAnalysisRequest request) {
        projectService.requireAccessible(projectId, user);
        CodeRepository repo = repositoryService.requireAccessible(request.repositoryId(), user);
        if (!repo.getProjectId().equals(projectId)) {
            throw BizException.badRequest("仓库不属于该项目");
        }
        String inputDetail = buildInputDetail(request);
        String question = request.title();
        AiAnalysis record = newRecord(projectId, repo.getId(), "BUG_ANALYSIS", question, inputDetail, user.id());
        try {
            AgentResultDto result = aiIndexClient.bugAnalysis(repo.getId(), request.title(),
                    request.errorMessage(), request.stackTrace(), request.reproSteps());
            return persistSuccess(record, result);
        } catch (BizException e) {
            persistFailure(record, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Bug 分析未知异常 analysisId={}", record.getId(), e);
            persistFailure(record, "分析执行异常");
            throw new BizException(ErrorCode.UPSTREAM_ERROR, "AI 服务调用失败，请稍后重试");
        }
    }

    public com.devpilot.common.api.PageResult<AnalysisVO> pageOf(UserPrincipal user, Long projectId, String type, int page, int size) {
        projectService.requireAccessible(projectId, user);
        Page<AiAnalysis> result = analysisMapper.selectPage(new Page<>(page, Math.min(size, 50)),
                new LambdaQueryWrapper<AiAnalysis>()
                        .eq(AiAnalysis::getProjectId, projectId)
                        .eq(type != null && !type.isBlank(), AiAnalysis::getType, type)
                        .orderByDesc(AiAnalysis::getId));
        return new com.devpilot.common.api.PageResult<>(
                result.getRecords().stream().map(AnalysisVO::summaryOf).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    public AnalysisVO detail(UserPrincipal user, Long analysisId) {
        AiAnalysis record = analysisMapper.selectById(analysisId);
        if (record == null) {
            throw BizException.notFound("分析记录不存在");
        }
        projectService.requireAccessible(record.getProjectId(), user);
        List<AnalysisStep> steps = stepMapper.selectList(new LambdaQueryWrapper<AnalysisStep>()
                .eq(AnalysisStep::getAnalysisId, record.getId()).orderByAsc(AnalysisStep::getStepOrder));
        StoredPayload payload = parsePayload(record.getStructuredResult());
        List<AgentStepDto> stepDtos = steps.stream()
                .map(s -> new AgentStepDto(s.getName(), s.getStatus(), s.getDetail(), s.getDurationMs())).toList();
        return new AnalysisVO(record.getId(), record.getProjectId(), record.getRepositoryId(), record.getType(),
                record.getStatus(), record.getQuestion(), record.getAnswer(), record.getMode(), record.getProvider(),
                record.getModel(), record.getConfidence() == null ? null : record.getConfidence().doubleValue(),
                record.getEvidenceCount(), record.getDurationMs(), record.getErrorMessage(), record.getCreatedBy(),
                record.getCreatedAt(), payload == null ? null : payload.evidence(), stepDtos,
                payload == null ? null : payload.warnings(), payload == null ? null : payload.report());
    }

    public AiAnalysis requireById(Long analysisId) {
        AiAnalysis record = analysisMapper.selectById(analysisId);
        if (record == null) {
            throw BizException.notFound("分析记录不存在");
        }
        return record;
    }

    // ---------- 内部方法 ----------

    private AiAnalysis newRecord(Long projectId, Long repositoryId, String type, String question,
                                 String inputDetail, Long userId) {
        AiAnalysis record = new AiAnalysis();
        record.setProjectId(projectId);
        record.setRepositoryId(repositoryId);
        record.setType(type);
        record.setStatus("RUNNING");
        record.setQuestion(question);
        record.setInputDetail(inputDetail);
        record.setCreatedBy(userId);
        analysisMapper.insert(record);
        return record;
    }

    private AnalysisVO persistSuccess(AiAnalysis record, AgentResultDto result) {
        long duration = result.durationMs() == null ? 0L : result.durationMs();
        record.setStatus("SUCCESS");
        record.setAnswer(result.answer());
        record.setMode(result.mode());
        record.setProvider(result.provider());
        record.setModel(result.model());
        record.setConfidence(result.confidence() == null ? null
                : BigDecimal.valueOf(result.confidence()).setScale(3, RoundingMode.HALF_UP));
        record.setEvidenceCount(result.evidence() == null ? 0 : result.evidence().size());
        record.setDurationMs(duration);
        try {
            StoredPayload payload = new StoredPayload(result.evidence(), result.steps(), result.warnings(), result.report());
            record.setStructuredResult(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("结构化结果序列化失败 analysisId={}", record.getId());
        }
        analysisMapper.updateById(record);

        List<AgentStepDto> steps = result.steps() == null ? List.of() : result.steps();
        int order = 1;
        for (AgentStepDto step : steps) {
            AnalysisStep row = new AnalysisStep();
            row.setAnalysisId(record.getId());
            row.setStepOrder(order++);
            row.setName(truncate(step.name(), 50));
            row.setStatus(step.status() == null ? "SUCCESS" : step.status());
            row.setDetail(truncate(step.detail(), 2000));
            row.setDurationMs(step.durationMs());
            stepMapper.insert(row);
        }
        return detail(currentSystemUser(), record.getId());
    }

    private void persistFailure(AiAnalysis record, String message) {
        record.setStatus("FAILED");
        record.setErrorMessage(message);
        record.setDurationMs(0L);
        analysisMapper.updateById(record);
        AnalysisStep row = new AnalysisStep();
        row.setAnalysisId(record.getId());
        row.setStepOrder(1);
        row.setName("dispatch");
        row.setStatus("FAILED");
        row.setDetail(truncate(message, 2000));
        stepMapper.insert(row);
    }

    private String buildInputDetail(BugAnalysisRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("【标题】").append(request.title()).append('\n');
        sb.append("【错误信息】").append(request.errorMessage()).append('\n');
        if (request.stackTrace() != null && !request.stackTrace().isBlank()) {
            sb.append("【堆栈】").append(request.stackTrace()).append('\n');
        }
        if (request.reproSteps() != null && !request.reproSteps().isBlank()) {
            sb.append("【复现步骤】").append(request.reproSteps());
        }
        return sb.toString();
    }

    private StoredPayload parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, StoredPayload.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "…(截断)";
    }

    /** 服务内部查询（已做过权限校验的场景复用 detail） */
    private UserPrincipal currentSystemUser() {
        // detail() 需要登录态；本方法仅在请求线程内被调用，直接取当前用户
        return com.devpilot.security.SecurityUtils.current();
    }
}
