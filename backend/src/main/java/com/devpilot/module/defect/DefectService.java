package com.devpilot.module.defect;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devpilot.common.exception.BizException;
import com.devpilot.module.ai.AiAnalysis;
import com.devpilot.module.ai.AiAnalysisService;
import com.devpilot.module.defect.dto.DefectCommentVO;
import com.devpilot.module.defect.dto.DefectCreateRequest;
import com.devpilot.module.defect.dto.DefectUpdateRequest;
import com.devpilot.module.defect.dto.DefectVO;
import com.devpilot.module.defect.mapper.DefectCommentMapper;
import com.devpilot.module.defect.mapper.DefectMapper;
import com.devpilot.module.project.Project;
import com.devpilot.module.project.ProjectService;
import com.devpilot.module.user.SysUser;
import com.devpilot.module.user.UserService;
import com.devpilot.module.user.mapper.SysUserMapper;
import com.devpilot.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 缺陷领域服务：CRUD + 状态机 + 操作权限。
 * 状态流转与操作权限都在服务端校验，前端提示仅为体验优化。
 */
@Service
@RequiredArgsConstructor
public class DefectService {

    /** 缺陷状态机：只允许列出的目标状态 */
    private static final Map<String, List<String>> TRANSITIONS = Map.of(
            "OPEN", List.of("IN_PROGRESS"),
            "IN_PROGRESS", List.of("RESOLVED", "OPEN"),
            "RESOLVED", List.of("CLOSED", "OPEN"),
            "CLOSED", List.of());

    private static final Set<String> SEVERITIES = Set.of("CRITICAL", "MAJOR", "MEDIUM", "MINOR");
    private static final Set<String> PRIORITIES = Set.of("P0", "P1", "P2", "P3");

    private final DefectMapper defectMapper;
    private final DefectCommentMapper commentMapper;
    private final ProjectService projectService;
    private final UserService userService;
    private final SysUserMapper userMapper;
    private final AiAnalysisService aiAnalysisService;

    @Transactional
    public DefectVO create(UserPrincipal user, Long projectId, DefectCreateRequest request) {
        Project project = projectService.requireAccessible(projectId, user);
        Defect defect = new Defect();
        defect.setProjectId(project.getId());
        defect.setTitle(request.title());
        defect.setDescription(request.description());
        defect.setSeverity(request.severity() == null ? "MEDIUM" : request.severity());
        defect.setPriority(request.priority() == null ? "P2" : request.priority());
        defect.setStatus("OPEN");
        defect.setReporterId(user.id());
        if (request.assigneeId() != null) {
            userService.requireById(request.assigneeId());
            defect.setAssigneeId(request.assigneeId());
        }
        if (request.analysisId() != null) {
            AiAnalysis analysis = aiAnalysisService.requireById(request.analysisId());
            if (!analysis.getProjectId().equals(projectId)) {
                throw BizException.badRequest("关联的分析记录不属于该项目");
            }
            defect.setAnalysisId(analysis.getId());
        }
        defectMapper.insert(defect);
        return toVO(defect);
    }

    public com.devpilot.common.api.PageResult<DefectVO> pageOf(UserPrincipal user, Long projectId, String status, String severity,
                                 String keyword, int page, int size) {
        projectService.requireAccessible(projectId, user);
        Page<Defect> result = defectMapper.selectPage(new Page<>(page, Math.min(size, 100)), new LambdaQueryWrapper<Defect>()
                .eq(Defect::getProjectId, projectId)
                .eq(StringUtils.hasText(status), Defect::getStatus, status)
                .eq(StringUtils.hasText(severity), Defect::getSeverity, severity)
                .like(StringUtils.hasText(keyword), Defect::getTitle, keyword)
                .orderByDesc(Defect::getId));
        return new com.devpilot.common.api.PageResult<>(
                result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    public DefectVO detail(UserPrincipal user, Long defectId) {
        Defect defect = requireAccessible(defectId, user);
        return toVO(defect);
    }

    @Transactional
    public DefectVO update(UserPrincipal user, Long defectId, DefectUpdateRequest request) {
        Defect defect = requireAccessible(defectId, user);
        if (StringUtils.hasText(request.title())) {
            defect.setTitle(request.title());
        }
        if (request.description() != null) {
            defect.setDescription(request.description());
        }
        if (StringUtils.hasText(request.severity())) {
            defect.setSeverity(request.severity());
        }
        if (StringUtils.hasText(request.priority())) {
            defect.setPriority(request.priority());
        }
        if (request.assigneeId() != null) {
            userService.requireById(request.assigneeId());
            defect.setAssigneeId(request.assigneeId());
        }
        defectMapper.updateById(defect);
        return toVO(defect);
    }

    /**
     * 状态流转：服务端校验状态机与操作人权限。
     * 非法流转返回 409 并附上当前允许的目标状态，前端据此渲染按钮。
     */
    @Transactional
    public DefectVO changeStatus(UserPrincipal user, Long defectId, String target) {
        Defect defect = requireAccessible(defectId, user);
        List<String> allowed = TRANSITIONS.getOrDefault(defect.getStatus(), List.of());
        if (!allowed.contains(target)) {
            throw BizException.conflict("非法状态流转: " + defect.getStatus() + " → " + target
                    + "（允许: " + (allowed.isEmpty() ? "无，已终态" : allowed) + "）");
        }
        if (!canOperate(user, defect)) {
            throw BizException.forbidden("仅报告人/负责人/项目管理员可以流转该缺陷");
        }
        defect.setStatus(target);
        defectMapper.updateById(defect);
        return toVO(defect);
    }

    @Transactional
    public DefectCommentVO addComment(UserPrincipal user, Long defectId, String content) {
        Defect defect = requireAccessible(defectId, user);
        DefectComment comment = new DefectComment();
        comment.setDefectId(defect.getId());
        comment.setAuthorId(user.id());
        comment.setContent(content);
        commentMapper.insert(comment);
        return DefectCommentVO.from(comment, userService.requireById(user.id()).getNickname());
    }

    public List<DefectCommentVO> comments(UserPrincipal user, Long defectId) {
        Defect defect = requireAccessible(defectId, user);
        return commentMapper.selectList(new LambdaQueryWrapper<DefectComment>()
                        .eq(DefectComment::getDefectId, defect.getId()).orderByAsc(DefectComment::getId))
                .stream()
                .map(c -> DefectCommentVO.from(c, nicknameOf(c.getAuthorId())))
                .toList();
    }

    public Defect requireById(Long defectId) {
        Defect defect = defectMapper.selectById(defectId);
        if (defect == null) {
            throw BizException.notFound("缺陷不存在");
        }
        return defect;
    }

    public void updateEntity(Defect defect) {
        defectMapper.updateById(defect);
    }

    // ---------- 内部 ----------

    private Defect requireAccessible(Long defectId, UserPrincipal user) {
        Defect defect = requireById(defectId);
        projectService.requireAccessible(defect.getProjectId(), user);
        return defect;
    }

    private boolean canOperate(UserPrincipal user, Defect defect) {
        if (user.isAdmin()) {
            return true;
        }
        if (defect.getReporterId().equals(user.id())) {
            return true;
        }
        if (user.id().equals(defect.getAssigneeId())) {
            return true;
        }
        Project project = projectService.requireAccessible(defect.getProjectId(), user);
        return project.getOwnerId().equals(user.id());
    }

    private DefectVO toVO(Defect defect) {
        return DefectVO.from(defect, nicknameOf(defect.getAssigneeId()), nicknameOf(defect.getReporterId()));
    }

    private String nicknameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = userMapper.selectById(userId);
        return user == null ? null : user.getNickname();
    }
}
