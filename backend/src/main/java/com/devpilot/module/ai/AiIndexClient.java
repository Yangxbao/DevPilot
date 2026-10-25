package com.devpilot.module.ai;

import com.devpilot.common.config.AppProperties;
import com.devpilot.common.exception.BizException;
import com.devpilot.common.api.ErrorCode;
import com.devpilot.module.ai.dto.AgentResultDto;
import com.devpilot.module.ai.dto.ChunkPayload;
import com.devpilot.module.ai.dto.ReindexResult;
import com.devpilot.module.ai.dto.SearchHitDto;
import com.devpilot.module.ai.dto.SearchResponseDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Python AI 服务客户端。
 * - 检索类调用（15s 超时）与 Agent 调用（150s 超时）分离；
 * - 请求头携带内部 Token；
 * - 任何网络/HTTP 失败统一转为 50200 业务异常（前端显示"AI 服务不可用"）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiIndexClient {

    private static final int REINDEX_BATCH_SIZE = 200;

    private final AppProperties properties;

    private RestClient searchClient;
    private RestClient agentClient;

    @PostConstruct
    void init() {
        AppProperties.Ai cfg = properties.getAi();
        this.searchClient = RestClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .defaultHeader("X-Internal-Token", cfg.getInternalToken())
                .requestFactory(simpleFactory(3000, cfg.getSearchTimeoutSeconds() * 1000))
                .build();
        this.agentClient = RestClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .defaultHeader("X-Internal-Token", cfg.getInternalToken())
                .requestFactory(simpleFactory(3000, cfg.getAgentTimeoutSeconds() * 1000))
                .build();
    }

    private static org.springframework.http.client.SimpleClientHttpRequestFactory simpleFactory(int connectMs, int readMs) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectMs);
        factory.setReadTimeout(readMs);
        return factory;
    }

    /** 全量重建索引：先清空再分批推送，返回是否启用了向量检索 */
    public boolean reindex(Long repositoryId, List<ChunkPayload> chunks) {
        try {
            deleteIndex(repositoryId);
            boolean vectorEnabled = true;
            List<List<ChunkPayload>> batches = partition(chunks, REINDEX_BATCH_SIZE);
            for (List<ChunkPayload> batch : batches) {
                Map<String, Object> body = new HashMap<>();
                body.put("repositoryId", repositoryId);
                body.put("chunks", batch);
                ReindexResult result = searchClient.post()
                        .uri("/internal/reindex")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(ReindexResult.class);
                if (result != null) {
                    vectorEnabled = vectorEnabled && result.vectorEnabled();
                }
            }
            return vectorEnabled;
        } catch (RestClientException e) {
            log.warn("AI 服务 reindex 调用失败 repoId={}: {}", repositoryId, e.getMessage());
            throw new BizException(ErrorCode.UPSTREAM_ERROR, "AI 服务不可用，语义索引未建立（关键词检索不受影响）");
        }
    }

    public void deleteIndex(Long repositoryId) {
        try {
            searchClient.delete().uri("/internal/index/{id}", repositoryId).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("AI 服务 deleteIndex 调用失败 repoId={}: {}", repositoryId, e.getMessage());
        }
    }

    public SearchResponseDto search(Long repositoryId, String query, int topK, String mode) {
        try {
            Map<String, Object> body = Map.of("repositoryId", repositoryId, "query", query,
                    "topK", topK, "mode", mode);
            return searchClient.post().uri("/internal/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(SearchResponseDto.class);
        } catch (RestClientException e) {
            log.warn("AI 服务 search 调用失败: {}", e.getMessage());
            throw new BizException(ErrorCode.UPSTREAM_ERROR, "AI 服务不可用，语义检索暂不可用（可切换关键词模式）");
        }
    }

    public AgentResultDto qa(Long repositoryId, String question, int topK) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("repositoryId", repositoryId);
            body.put("question", question);
            body.put("topK", topK);
            return agentClient.post().uri("/agent/qa")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(AgentResultDto.class);
        } catch (RestClientException e) {
            log.warn("AI 服务 qa 调用失败: {}", e.getMessage());
            throw new BizException(ErrorCode.UPSTREAM_ERROR, "AI 服务不可用或调用超时，请稍后重试");
        }
    }

    public AgentResultDto bugAnalysis(Long repositoryId, String title, String errorMessage,
                                      String stackTrace, String reproSteps) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("repositoryId", repositoryId);
            body.put("title", title);
            body.put("errorMessage", errorMessage);
            body.put("stackTrace", stackTrace);
            body.put("reproSteps", reproSteps);
            return agentClient.post().uri("/agent/bug-analysis")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(AgentResultDto.class);
        } catch (RestClientException e) {
            log.warn("AI 服务 bug-analysis 调用失败: {}", e.getMessage());
            throw new BizException(ErrorCode.UPSTREAM_ERROR, "AI 服务不可用或调用超时，请稍后重试");
        }
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            parts.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return parts;
    }
}
