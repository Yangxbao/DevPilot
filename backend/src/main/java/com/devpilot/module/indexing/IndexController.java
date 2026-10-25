package com.devpilot.module.indexing;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devpilot.common.api.ApiResponse;
import com.devpilot.common.api.PageResult;
import com.devpilot.module.indexing.dto.IndexTaskVO;
import com.devpilot.module.indexing.mapper.CodeFileMapper;
import com.devpilot.module.project.CodeRepository;
import com.devpilot.module.project.RepositoryService;
import com.devpilot.security.SecurityUtils;
import com.devpilot.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 索引任务与文件浏览接口。
 */
@Tag(name = "indexing", description = "索引任务与代码文件")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class IndexController {

    private final IndexTaskService indexTaskService;
    private final RepositoryService repositoryService;
    private final CodeFileMapper codeFileMapper;

    @Operation(summary = "触发(重新)索引")
    @PostMapping("/repositories/{id}/index-tasks")
    public ApiResponse<IndexTaskVO> createTask(@PathVariable Long id) {
        UserPrincipal user = SecurityUtils.current();
        return ApiResponse.ok(indexTaskService.createTask(user, id));
    }

    @Operation(summary = "仓库的索引任务历史")
    @GetMapping("/repositories/{id}/index-tasks")
    public ApiResponse<PageResult<IndexTaskVO>> tasks(@PathVariable Long id,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        UserPrincipal user = SecurityUtils.current();
        return ApiResponse.ok(indexTaskService.pageOf(user, id, page, Math.min(size, 100)));
    }

    @Operation(summary = "索引任务详情（轮询进度）")
    @GetMapping("/index-tasks/{id}")
    public ApiResponse<IndexTaskVO> task(@PathVariable Long id) {
        UserPrincipal user = SecurityUtils.current();
        return ApiResponse.ok(indexTaskService.get(user, id));
    }

    @Operation(summary = "仓库文件清单（含被过滤文件与原因）")
    @GetMapping("/repositories/{id}/files")
    public ApiResponse<PageResult<CodeFile>> files(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) String path) {
        UserPrincipal user = SecurityUtils.current();
        CodeRepository repo = repositoryService.requireAccessible(id, user);
        Page<CodeFile> result = codeFileMapper.selectPage(new Page<>(page, Math.min(size, 100)),
                new LambdaQueryWrapper<CodeFile>()
                        .eq(CodeFile::getRepositoryId, repo.getId())
                        .like(StringUtils.hasText(path), CodeFile::getPath, path)
                        .orderByAsc(CodeFile::getPath));
        // 列表不返回大字段内容
        result.getRecords().forEach(f -> f.setContent(null));
        return ApiResponse.ok(PageResult.of(result, f -> f));
    }

    @Operation(summary = "文件内容预览")
    @GetMapping("/code-files/{id}")
    public ApiResponse<CodeFile> file(@PathVariable Long id) {
        UserPrincipal user = SecurityUtils.current();
        CodeFile file = codeFileMapper.selectById(id);
        if (file == null) {
            throw com.devpilot.common.exception.BizException.notFound("文件不存在");
        }
        repositoryService.requireAccessible(file.getRepositoryId(), user);
        return ApiResponse.ok(file);
    }
}
