package com.devpilot.module.indexing;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devpilot.common.exception.BizException;
import com.devpilot.module.indexing.dto.IndexTaskVO;
import com.devpilot.module.indexing.mapper.CodeChunkMapper;
import com.devpilot.module.indexing.mapper.CodeFileMapper;
import com.devpilot.module.indexing.mapper.IndexTaskMapper;
import com.devpilot.module.project.CodeRepository;
import com.devpilot.module.project.mapper.CodeRepositoryMapper;
import com.devpilot.module.project.RepositoryService;
import com.devpilot.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 索引任务服务：触发/查询。执行逻辑在 {@link AsyncIndexRunner}。
 * 并发防重由数据库唯一索引 (repository_id, active) 兜底。
 */
@Service
@RequiredArgsConstructor
public class IndexTaskService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final IndexTaskMapper taskMapper;
    private final CodeFileMapper fileMapper;
    private final RepositoryService repositoryService;
    private final CodeRepositoryMapper repositoryMapper;
    private final AsyncIndexRunner runner;

    public IndexTaskVO createTask(UserPrincipal user, Long repositoryId) {
        CodeRepository repo = repositoryService.requireAccessible(repositoryId, user);

        IndexTask existing = taskMapper.selectOne(new LambdaQueryWrapper<IndexTask>()
                .eq(IndexTask::getRepositoryId, repo.getId()).eq(IndexTask::getActive, 1));
        if (existing != null) {
            throw BizException.conflict("该仓库已有进行中的索引任务（任务号 " + existing.getTaskNo() + "），请等待完成");
        }

        IndexTask task = new IndexTask();
        task.setRepositoryId(repo.getId());
        task.setProjectId(repo.getProjectId());
        task.setTaskNo("IDX" + LocalDateTime.now().format(TS) + "-" + ThreadLocalRandom.current().nextInt(1000, 9999));
        task.setTriggerType("MANUAL");
        task.setStatus("PENDING");
        task.setActive(1);
        task.setCreatedBy(user.id());
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException e) {
            throw BizException.conflict("该仓库已有进行中的索引任务，请勿重复提交");
        }
        runner.executeTask(task.getId());
        return IndexTaskVO.from(taskMapper.selectById(task.getId()));
    }

    public com.devpilot.common.api.PageResult<IndexTaskVO> pageOf(UserPrincipal user, Long repositoryId, int page, int size) {
        repositoryService.requireAccessible(repositoryId, user);
        Page<IndexTask> result = taskMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<IndexTask>()
                .eq(IndexTask::getRepositoryId, repositoryId).orderByDesc(IndexTask::getId));
        return new com.devpilot.common.api.PageResult<>(
                result.getRecords().stream().map(IndexTaskVO::from).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    public IndexTaskVO get(UserPrincipal user, Long taskId) {
        IndexTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw BizException.notFound("索引任务不存在");
        }
        repositoryService.requireAccessible(task.getRepositoryId(), user);
        return IndexTaskVO.from(task);
    }

    public List<IndexTask> activeTasksOf(Long projectId) {
        return taskMapper.selectList(new LambdaQueryWrapper<IndexTask>()
                .eq(IndexTask::getProjectId, projectId).eq(IndexTask::getActive, 1));
    }

    /** 终态化：active 置 NULL 是放行下一个任务的关键（MyBatis-Plus 默认跳过 null 字段，需显式 set） */
    public void finishTask(IndexTask task, String status, String errorMessage) {
        task.setStatus(status);
        task.setErrorMessage(errorMessage);
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.update(null, new LambdaUpdateWrapper<IndexTask>()
                .eq(IndexTask::getId, task.getId())
                .set(IndexTask::getStatus, status)
                .set(IndexTask::getErrorMessage, errorMessage)
                .set(IndexTask::getActive, null)
                .set(IndexTask::getFinishedAt, task.getFinishedAt()));
    }
}
