package com.devpilot.module.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devpilot.common.exception.BizException;
import com.devpilot.module.ai.AiIndexClient;
import com.devpilot.module.ai.dto.SearchHitDto;
import com.devpilot.module.indexing.SearchHitRow;
import com.devpilot.module.indexing.mapper.CodeChunkMapper;
import com.devpilot.module.project.CodeRepository;
import com.devpilot.module.project.ProjectService;
import com.devpilot.module.project.RepositoryService;
import com.devpilot.module.project.mapper.CodeRepositoryMapper;
import com.devpilot.module.search.dto.SearchHitVO;
import com.devpilot.module.search.dto.SearchResponse;
import com.devpilot.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 检索服务：关键词（MySQL FULLTEXT + LIKE 兜底）与语义/混合（Python 侧混合检索）。
 * 范围：单个仓库，或整个项目下全部"导入成功"的仓库（结果合并排序后分页）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int KEYWORD_FETCH_LIMIT = 200;

    private final RepositoryService repositoryService;
    private final ProjectService projectService;
    private final CodeRepositoryMapper repositoryMapper;
    private final CodeChunkMapper chunkMapper;
    private final AiIndexClient aiIndexClient;

    public SearchResponse search(UserPrincipal user, Long projectId, Long repositoryId,
                                 String query, String mode, int page, int size) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            throw BizException.badRequest("搜索关键词不能为空");
        }
        if (q.length() > 200) {
            q = q.substring(0, 200);
        }
        String normalizedMode = switch (mode == null ? "keyword" : mode) {
            case "keyword", "semantic", "hybrid" -> mode;
            default -> throw BizException.badRequest("mode 仅支持 keyword/semantic/hybrid");
        };

        // 解析并校验检索范围（防越权）
        List<CodeRepository> scope;
        if (repositoryId != null) {
            scope = List.of(repositoryService.requireAccessible(repositoryId, user));
        } else if (projectId != null) {
            projectService.requireAccessible(projectId, user);
            scope = repositoryMapper.selectList(new LambdaQueryWrapper<CodeRepository>()
                    .eq(CodeRepository::getProjectId, projectId)
                    .eq(CodeRepository::getImportStatus, "SUCCESS"));
        } else {
            throw BizException.badRequest("projectId 与 repositoryId 至少提供一个");
        }
        if (scope.isEmpty()) {
            return new SearchResponse(List.of(), 0, page, size, false);
        }

        boolean vectorEnabled = true;
        List<SearchHitVO> merged = new ArrayList<>();
        if ("keyword".equals(normalizedMode)) {
            for (CodeRepository repo : scope) {
                merged.addAll(keywordSearch(repo, q));
            }
            merged.sort(Comparator.comparingDouble(SearchHitVO::score).reversed());
        } else {
            // 语义/混合：优先走 Python；AI 服务不可用时自动降级为关键词检索（并在响应中如实标注）
            for (CodeRepository repo : scope) {
                try {
                    var response = aiIndexClient.search(repo.getId(), q, Math.min(50, Math.max(size * 3, 15)), normalizedMode);
                    vectorEnabled = vectorEnabled && response.vectorEnabled();
                    for (SearchHitDto hit : response.results()) {
                        merged.add(new SearchHitVO(parseChunkId(hit.chunkId()), hit.fileId(), repo.getId(),
                                hit.path(), hit.language(), hit.startLine(), hit.endLine(),
                                hit.snippet(), hit.score(), hit.channel()));
                    }
                } catch (BizException e) {
                    vectorEnabled = false;
                    log.warn("语义检索降级为关键词 repoId={}: {}", repo.getId(), e.getMessage());
                    merged.addAll(keywordSearch(repo, q));
                }
            }
            merged.sort(Comparator.comparingDouble(SearchHitVO::score).reversed());
        }

        int total = merged.size();
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);
        return new SearchResponse(new ArrayList<>(merged.subList(from, to)), total, page, size, vectorEnabled);
    }

    private List<SearchHitVO> keywordSearch(CodeRepository repo, String q) {
        // FULLTEXT BOOLEAN 模式对特殊符号敏感，先净化；净化后为空则原样使用
        String sanitized = q.replaceAll("[+\\-*/=~!@#$%^&(){}\\[\\]|:;\"'<>?,.`]", " ").trim();
        if (sanitized.isEmpty()) {
            sanitized = q;
        }
        List<SearchHitRow> rows = chunkMapper.searchFulltext(repo.getId(), sanitized, KEYWORD_FETCH_LIMIT);
        if (rows.isEmpty()) {
            String escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            rows = chunkMapper.searchLike(repo.getId(), escaped, KEYWORD_FETCH_LIMIT);
        }
        return rows.stream().map(row -> new SearchHitVO(row.getChunkId(), row.getFileId(), repo.getId(),
                row.getPath(), row.getLanguage(), row.getStartLine(), row.getEndLine(),
                snippetAround(row.getContent(), q), row.getScore() == null ? 0.0 : row.getScore(), "keyword")).toList();
    }

    /** 在关键词首次出现处截取窗口片段，便于前端直接定位 */
    static String snippetAround(String content, String keyword) {
        if (content == null) {
            return "";
        }
        String flat = content.length() > 2000 ? content.substring(0, 2000) : content;
        int idx = flat.indexOf(keyword);
        if (idx < 0) {
            return flat.substring(0, Math.min(300, flat.length()));
        }
        int start = Math.max(0, idx - 120);
        int end = Math.min(flat.length(), idx + 200);
        return (start > 0 ? "…" : "") + flat.substring(start, end) + (end < flat.length() ? "…" : "");
    }

    private static long parseChunkId(String chunkId) {
        try {
            String[] parts = chunkId.split("-");
            return Long.parseLong(parts[parts.length - 1]);
        } catch (Exception e) {
            return 0L;
        }
    }
}
