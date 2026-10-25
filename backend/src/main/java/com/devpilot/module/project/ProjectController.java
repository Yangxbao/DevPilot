package com.devpilot.module.project;

import com.devpilot.common.api.ApiResponse;
import com.devpilot.common.api.PageResult;
import com.devpilot.module.project.dto.GitImportRequest;
import com.devpilot.module.project.dto.ProjectCreateRequest;
import com.devpilot.module.project.dto.ProjectVO;
import com.devpilot.module.project.dto.RepositoryVO;
import com.devpilot.security.SecurityUtils;
import com.devpilot.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 项目与代码仓库接口。
 */
@Tag(name = "project", description = "项目与代码仓库")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final RepositoryService repositoryService;

    @Operation(summary = "项目分页列表（用户看自己的，管理员看全部）")
    @GetMapping("/projects")
    public ApiResponse<PageResult<ProjectVO>> page(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size,
                                                   @RequestParam(required = false) String keyword) {
        UserPrincipal user = SecurityUtils.current();
        return ApiResponse.ok(PageResult.of(projectService.pageOf(user, page, Math.min(size, 100), keyword), ProjectVO::from));
    }

    @Operation(summary = "创建项目")
    @PostMapping("/projects")
    public ApiResponse<ProjectVO> create(@Valid @RequestBody ProjectCreateRequest request) {
        UserPrincipal user = SecurityUtils.current();
        return ApiResponse.ok(ProjectVO.from(projectService.create(user.id(), request.name(), request.description())));
    }

    @Operation(summary = "项目详情")
    @GetMapping("/projects/{id}")
    public ApiResponse<ProjectVO> detail(@PathVariable Long id) {
        UserPrincipal user = SecurityUtils.current();
        return ApiResponse.ok(ProjectVO.from(projectService.requireAccessible(id, user)));
    }

    @Operation(summary = "删除项目（软删除）")
    @DeleteMapping("/projects/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        UserPrincipal user = SecurityUtils.current();
        projectService.delete(id, user);
        return ApiResponse.ok();
    }

    @Operation(summary = "上传 zip 压缩包导入代码")
    @PostMapping(value = "/projects/{id}/repositories/upload", consumes = "multipart/form-data")
    public ApiResponse<RepositoryVO> upload(@PathVariable Long id,
                                            @RequestParam("file") MultipartFile file,
                                            @RequestParam(value = "name", required = false) String name) {
        UserPrincipal user = SecurityUtils.current();
        return ApiResponse.ok(repositoryService.createByUpload(user, id, name, file));
    }

    @Operation(summary = "导入公开 Git 仓库（https）")
    @PostMapping("/projects/{id}/repositories/git")
    public ApiResponse<RepositoryVO> importGit(@PathVariable Long id, @Valid @RequestBody GitImportRequest request) {
        UserPrincipal user = SecurityUtils.current();
        return ApiResponse.ok(repositoryService.createByGit(user, id, request));
    }

    @Operation(summary = "仓库列表")
    @GetMapping("/projects/{id}/repositories")
    public ApiResponse<PageResult<RepositoryVO>> repositories(@PathVariable Long id,
                                                              @RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "10") int size) {
        UserPrincipal user = SecurityUtils.current();
        return ApiResponse.ok(repositoryService.pageOf(user, id, page, Math.min(size, 100)));
    }

    @Operation(summary = "删除仓库")
    @DeleteMapping("/repositories/{id}")
    public ApiResponse<Void> deleteRepository(@PathVariable Long id) {
        UserPrincipal user = SecurityUtils.current();
        repositoryService.delete(user, id);
        return ApiResponse.ok();
    }
}
