package com.devpilot.module.stats;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.devpilot.module.ai.AiAnalysis;
import com.devpilot.module.ai.mapper.AiAnalysisMapper;
import com.devpilot.module.defect.Defect;
import com.devpilot.module.defect.mapper.DefectMapper;
import com.devpilot.module.indexing.IndexTask;
import com.devpilot.module.indexing.mapper.IndexTaskMapper;
import com.devpilot.module.project.CodeRepository;
import com.devpilot.module.project.Project;
import com.devpilot.module.project.mapper.CodeRepositoryMapper;
import com.devpilot.module.project.mapper.ProjectMapper;
import com.devpilot.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘统计：全部数字来自实时数据库查询（空数据返回 0 + 前端空状态）。
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final ProjectMapper projectMapper;
    private final CodeRepositoryMapper repositoryMapper;
    private final IndexTaskMapper indexTaskMapper;
    private final DefectMapper defectMapper;
    private final AiAnalysisMapper analysisMapper;

    public DashboardVO dashboard(UserPrincipal user) {
        List<Long> projectIds = projectIdsOf(user);
        boolean all = user.isAdmin();

        DashboardVO vo = new DashboardVO();
        vo.setGeneratedAt(LocalDateTime.now());

        vo.setProjectCount(all ? projectMapper.selectCount(null) : projectIds.size());

        vo.setRepositoryCount(repositoryMapper.selectCount(scoped(all, projectIds, "project_id")));
        vo.setRepositoryByStatus(groupCount(repositoryMapper.selectMaps(groupQuery(all, projectIds,
                "repository", "project_id", "import_status"))));

        vo.setIndexTaskTotal(indexTaskMapper.selectCount(scoped(all, projectIds, "project_id")));
        vo.setIndexTaskByStatus(groupCount(indexTaskMapper.selectMaps(groupQuery(all, projectIds,
                "index_task", "project_id", "status"))));

        vo.setDefectTotal(defectMapper.selectCount(scoped(all, projectIds, "project_id")));
        vo.setDefectByStatus(groupCount(defectMapper.selectMaps(groupQuery(all, projectIds,
                "defect", "project_id", "status"))));

        vo.setAnalysisTotal(analysisMapper.selectCount(scoped(all, projectIds, "project_id")));
        vo.setAnalysisByType(groupCount(analysisMapper.selectMaps(groupQuery(all, projectIds,
                "ai_analysis", "project_id", "type"))));
        vo.setAnalysisByMode(groupCount(analysisMapper.selectMaps(groupQuery(all, projectIds,
                "ai_analysis", "project_id", "mode"))));

        return vo;
    }

    /** 分组计数查询：SELECT <group> as k, count(*) as cnt FROM <table> [WHERE scope] GROUP BY <group> */
    private static <T> QueryWrapper<T> groupQuery(boolean all, List<Long> projectIds,
                                                  String table, String scopeColumn, String groupColumn) {
        QueryWrapper<T> qw = new QueryWrapper<>();
        qw.select(groupColumn + " as k", "count(*) as cnt");
        if (!all) {
            if (projectIds.isEmpty()) {
                qw.apply("1 = 0");
            } else {
                qw.in(scopeColumn, projectIds);
            }
        }
        qw.groupBy(groupColumn);
        qw.last("/*" + table + "*/"); // 注释标注来源表，便于慢日志排查
        return qw;
    }

    private List<Long> projectIdsOf(UserPrincipal user) {
        if (user.isAdmin()) {
            return projectMapper.selectList(new LambdaQueryWrapper<Project>().select(Project::getId))
                    .stream().map(Project::getId).toList();
        }
        return projectMapper.selectList(new LambdaQueryWrapper<Project>()
                        .eq(Project::getOwnerId, user.id()).select(Project::getId))
                .stream().map(Project::getId).toList();
    }

    private static <T> QueryWrapper<T> scoped(boolean all, List<Long> projectIds, String column) {
        QueryWrapper<T> qw = new QueryWrapper<>();
        if (!all) {
            if (projectIds.isEmpty()) {
                qw.apply("1 = 0"); // 无项目 -> 恒空结果
            } else {
                qw.in(column, projectIds);
            }
        }
        return qw;
    }

    private static Map<String, Long> groupCount(List<Map<String, Object>> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object key = row.get("k");
                Object cnt = row.get("cnt");
                result.put(key == null ? "UNKNOWN" : key.toString(), cnt == null ? 0L : ((Number) cnt).longValue());
            }
        }
        return result;
    }

    @lombok.Data
    public static class DashboardVO {
        private Long projectCount;
        private Long repositoryCount;
        private Map<String, Long> repositoryByStatus;
        private Long indexTaskTotal;
        private Map<String, Long> indexTaskByStatus;
        private Long defectTotal;
        private Map<String, Long> defectByStatus;
        private Long analysisTotal;
        private Map<String, Long> analysisByType;
        private Map<String, Long> analysisByMode;
        private LocalDateTime generatedAt;
    }
}
