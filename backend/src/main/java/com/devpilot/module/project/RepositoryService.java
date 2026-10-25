package com.devpilot.module.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devpilot.common.exception.BizException;
import com.devpilot.module.ai.AiIndexClient;
import com.devpilot.module.indexing.mapper.CodeChunkMapper;
import com.devpilot.module.indexing.mapper.CodeFileMapper;
import com.devpilot.module.project.dto.GitImportRequest;
import com.devpilot.module.project.dto.RepositoryVO;
import com.devpilot.module.project.mapper.CodeRepositoryMapper;
import com.devpilot.common.config.AppProperties;
import com.devpilot.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Set;

/**
 * 代码仓库导入服务：zip 上传 / Git URL 两种来源。
 * 安全面：zip 魔数与大小校验、文件名服务端重命名、Git URL 协议白名单与 SSRF 防护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepositoryService {

    public static final long MAX_ARCHIVE_SIZE = 100L * 1024 * 1024;

    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    /** 禁止导入的 Git 主机（SSRF 防护：内网/回环/云元数据地址） */
    private static final Set<String> BLOCKED_HOSTS = Set.of("localhost", "127.0.0.1", "0.0.0.0",
            "169.254.169.254", "metadata.google.internal");

    private final CodeRepositoryMapper repositoryMapper;
    private final ProjectService projectService;
    private final AppProperties properties;
    private final AiIndexClient aiIndexClient;
    private final CodeFileMapper codeFileMapper;
    private final CodeChunkMapper codeChunkMapper;

    public RepositoryVO createByUpload(UserPrincipal user, Long projectId, String name, MultipartFile file) {
        Project project = projectService.requireAccessible(projectId, user);
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请上传 zip 压缩包");
        }
        if (file.getSize() > MAX_ARCHIVE_SIZE) {
            throw BizException.badRequest("压缩包超过 100MB 上限");
        }
        String safeName = StringUtils.hasText(name) ? name.trim() : "repo";
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(4);
            if (!Arrays.equals(head, ZIP_MAGIC)) {
                throw BizException.badRequest("仅支持 zip 格式压缩包");
            }
        } catch (IOException e) {
            throw new BizException(com.devpilot.common.api.ErrorCode.SYSTEM_ERROR, "读取上传文件失败");
        }

        CodeRepository repo = new CodeRepository();
        repo.setProjectId(project.getId());
        repo.setName(safeName);
        repo.setSourceType("UPLOAD");
        repo.setFileName(file.getOriginalFilename());
        repo.setImportStatus("PENDING");
        repo.setFileCount(0);
        repo.setTotalSize(0L);
        repositoryMapper.insert(repo);

        // 服务端重命名存储，杜绝用户文件名注入路径
        Path archives = Path.of(properties.getStorage().getPath(), "archives").toAbsolutePath().normalize();
        try {
            Files.createDirectories(archives);
            file.transferTo(archives.resolve(repo.getId() + ".zip"));
        } catch (IOException e) {
            log.error("保存上传压缩包失败 repoId={}", repo.getId(), e);
            repo.setImportStatus("FAILED");
            repo.setErrorMessage("保存上传文件失败");
            repositoryMapper.updateById(repo);
            throw new BizException(com.devpilot.common.api.ErrorCode.SYSTEM_ERROR, "保存上传文件失败");
        }
        return RepositoryVO.from(repositoryMapper.selectById(repo.getId()));
    }

    public RepositoryVO createByGit(UserPrincipal user, Long projectId, GitImportRequest request) {
        Project project = projectService.requireAccessible(projectId, user);
        validateGitUrl(request.gitUrl());

        CodeRepository repo = new CodeRepository();
        repo.setProjectId(project.getId());
        repo.setName(request.name().trim());
        repo.setSourceType("GIT_URL");
        repo.setGitUrl(request.gitUrl());
        repo.setGitRef(StringUtils.hasText(request.gitRef()) ? request.gitRef().trim() : "main");
        repo.setImportStatus("PENDING");
        repo.setFileCount(0);
        repo.setTotalSize(0L);
        repositoryMapper.insert(repo);
        return RepositoryVO.from(repo);
    }

    public com.devpilot.common.api.PageResult<RepositoryVO> pageOf(UserPrincipal user, Long projectId, int page, int size) {
        projectService.requireAccessible(projectId, user);
        Page<CodeRepository> result = repositoryMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<CodeRepository>().eq(CodeRepository::getProjectId, projectId)
                        .orderByDesc(CodeRepository::getId));
        return new com.devpilot.common.api.PageResult<>(
                result.getRecords().stream().map(RepositoryVO::from).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    public CodeRepository requireAccessible(Long repositoryId, UserPrincipal user) {
        CodeRepository repo = repositoryMapper.selectById(repositoryId);
        if (repo == null) {
            throw BizException.notFound("仓库不存在");
        }
        projectService.requireAccessible(repo.getProjectId(), user);
        return repo;
    }

    public void delete(UserPrincipal user, Long repositoryId) {
        CodeRepository repo = requireAccessible(repositoryId, user);
        // 清理派生数据（文件与分块），保留仓库记录（软删除）以便审计
        codeChunkMapper.delete(new LambdaQueryWrapper<com.devpilot.module.indexing.CodeChunk>()
                .eq(com.devpilot.module.indexing.CodeChunk::getRepositoryId, repo.getId()));
        codeFileMapper.delete(new LambdaQueryWrapper<com.devpilot.module.indexing.CodeFile>()
                .eq(com.devpilot.module.indexing.CodeFile::getRepositoryId, repo.getId()));
        repositoryMapper.deleteById(repo.getId());
        // 尽力清理 AI 服务侧索引（失败不影响删除）
        try {
            aiIndexClient.deleteIndex(repo.getId());
        } catch (Exception e) {
            log.warn("清理 AI 侧索引失败（忽略）repoId={} err={}", repo.getId(), e.getMessage());
        }
    }

    private void validateGitUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw BizException.badRequest("Git 地址不合法");
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw BizException.badRequest("Git 地址缺少主机名");
        }
        if (BLOCKED_HOSTS.contains(host.toLowerCase())) {
            throw BizException.badRequest("禁止导入内网或保留地址");
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                        || address.isAnyLocalAddress()) {
                    throw BizException.badRequest("禁止导入解析到内网地址的仓库");
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw BizException.badRequest("Git 主机不可解析");
        }
    }
}
