package com.devpilot.module.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devpilot.common.exception.BizException;
import com.devpilot.module.project.mapper.ProjectMapper;
import com.devpilot.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 项目领域服务。所有项目域数据的访问都必须先通过这里做归属校验（防越权）。
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;

    public Project create(Long ownerId, String name, String description) {
        Long dup = projectMapper.selectCount(new LambdaQueryWrapper<Project>()
                .eq(Project::getOwnerId, ownerId).eq(Project::getName, name));
        if (dup != null && dup > 0) {
            throw BizException.conflict("同名项目已存在");
        }
        Project project = new Project();
        project.setOwnerId(ownerId);
        project.setName(name);
        project.setDescription(description == null ? "" : description);
        projectMapper.insert(project);
        return project;
    }

    /**
     * 归属校验：项目存在、未删除，且当前用户是 owner 或 ADMIN。
     * 越权访问统一返回 403（不泄露他人项目是否存在之外的信息）。
     */
    public Project requireAccessible(Long projectId, UserPrincipal user) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw BizException.notFound("项目不存在");
        }
        if (!user.isAdmin() && !project.getOwnerId().equals(user.id())) {
            throw BizException.forbidden("无权限访问该项目");
        }
        return project;
    }

    public Page<Project> pageOf(UserPrincipal user, int page, int size, String keyword) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<Project>()
                .eq(!user.isAdmin(), Project::getOwnerId, user.id())
                .and(StringUtils.hasText(keyword), w -> w.like(Project::getName, keyword))
                .orderByDesc(Project::getId);
        return projectMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public void delete(Long projectId, UserPrincipal user) {
        Project project = requireAccessible(projectId, user);
        projectMapper.deleteById(project.getId());
    }
}
