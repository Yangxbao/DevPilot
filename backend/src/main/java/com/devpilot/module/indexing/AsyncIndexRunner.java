package com.devpilot.module.indexing;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.devpilot.common.exception.BizException;
import com.devpilot.module.ai.AiIndexClient;
import com.devpilot.module.ai.dto.ChunkPayload;
import com.devpilot.module.indexing.mapper.CodeChunkMapper;
import com.devpilot.module.indexing.mapper.CodeFileMapper;
import com.devpilot.module.indexing.mapper.IndexTaskMapper;
import com.devpilot.module.project.CodeRepository;
import com.devpilot.module.project.mapper.CodeRepositoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 索引流水线异步执行器。
 * 流程：RUNNING -> 导入(zip/git) -> 事务内替换文件与分块 -> 推送 Python 建语义索引 -> SUCCESS/FAILED。
 * 任何阶段失败都会将任务与仓库状态落库为 FAILED 并记录原因，不留进行中的 active 任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncIndexRunner {

    private final IndexTaskMapper taskMapper;
    private final CodeFileMapper fileMapper;
    private final CodeChunkMapper chunkMapper;
    private final CodeRepositoryMapper repositoryMapper;
    private final ImportService importService;
    private final AiIndexClient aiIndexClient;
    private final TransactionTemplate transactionTemplate;

    @Async("indexExecutor")
    public void executeTask(Long taskId) {
        IndexTask task = taskMapper.selectById(taskId);
        if (task == null || !"PENDING".equals(task.getStatus())) {
            return;
        }
        CodeRepository repo = repositoryMapper.selectById(task.getRepositoryId());
        if (repo == null) {
            finish(task, "FAILED", "仓库不存在或已删除");
            return;
        }

        markRunning(task, repo);
        long begin = System.currentTimeMillis();
        try {
            // 1) 导入文件清单
            List<ImportService.ImportedFile> files = "UPLOAD".equals(repo.getSourceType())
                    ? importService.importFromUpload(repo.getId())
                    : importService.importFromGit(repo);

            int totalFiles = files.size();
            task.setTotalFiles(totalFiles);
            updateTask(task);

            // 2) 事务内全量替换文件与分块（旧数据一次清掉，避免半新半旧）
            InsertResult inserted = transactionTemplate.execute(status -> {
                chunkMapper.delete(new LambdaUpdateWrapper<CodeChunk>()
                        .eq(CodeChunk::getRepositoryId, repo.getId()));
                fileMapper.delete(new LambdaUpdateWrapper<CodeFile>()
                        .eq(CodeFile::getRepositoryId, repo.getId()));
                return insertFilesAndChunks(repo.getId(), files, task);
            });

            // 3) 推送语义索引（失败则任务 FAILED，但文件/分块已入库，关键词检索可用）
            boolean vectorEnabled = aiIndexClient.reindex(repo.getId(), inserted.payloads());

            // 4) 成功收尾
            long totalSize = files.stream().filter(f -> f.content() != null).mapToLong(ImportService.ImportedFile::size).sum();
            finish(task, "SUCCESS", null);
            repo.setImportStatus("SUCCESS");
            repo.setErrorMessage(null);
            repo.setFileCount(totalFiles);
            repo.setTotalSize(totalSize);
            repo.setLastIndexedAt(LocalDateTime.now());
            repositoryMapper.updateById(repo);
            log.info("索引完成 repoId={} files={} chunks={} vector={} cost={}ms",
                    repo.getId(), totalFiles, inserted.chunks().size(), vectorEnabled, System.currentTimeMillis() - begin);
        } catch (ImportService.ImportAbortedException e) {
            finish(task, "FAILED", e.getMessage());
            repo.setImportStatus("FAILED");
            repo.setErrorMessage(e.getMessage());
            repositoryMapper.updateById(repo);
        } catch (BizException e) {
            // 上游 AI 服务失败：导入已成功，仓库保持 SUCCESS，错误归索引任务
            finish(task, "FAILED", e.getMessage());
            repo.setImportStatus("SUCCESS");
            repositoryMapper.updateById(repo);
        } catch (Exception e) {
            log.error("索引任务异常 taskId={}", taskId, e);
            finish(task, "FAILED", "索引执行异常: " + e.getClass().getSimpleName());
            repo.setImportStatus("FAILED");
            repo.setErrorMessage("索引执行异常，请查看服务日志");
            repositoryMapper.updateById(repo);
        }
    }

    /** 事务插入结果：分块实体 + 推送负载 */
    record InsertResult(List<CodeChunk> chunks, List<ChunkPayload> payloads) {
    }

    private InsertResult insertFilesAndChunks(Long repositoryId, List<ImportService.ImportedFile> files, IndexTask task) {
        List<CodeChunk> all = new ArrayList<>();
        List<ChunkPayload> payloads = new ArrayList<>();
        int processed = 0;
        for (ImportService.ImportedFile imported : files) {
            CodeFile file = new CodeFile();
            file.setRepositoryId(repositoryId);
            file.setPath(imported.path());
            file.setLanguage(imported.language());
            file.setSize(imported.size());
            file.setLineCount(imported.lineCount());
            file.setContent(imported.content());
            file.setSkipReason(imported.skipReason());
            fileMapper.insert(file);
            processed++;

            if (imported.content() != null) {
                List<Chunker.Chunk> chunks = Chunker.chunk(imported.content());
                for (int i = 0; i < chunks.size(); i++) {
                    Chunker.Chunk c = chunks.get(i);
                    CodeChunk chunk = new CodeChunk();
                    chunk.setRepositoryId(repositoryId);
                    chunk.setFileId(file.getId());
                    chunk.setChunkIndex(i);
                    chunk.setStartLine(c.startLine());
                    chunk.setEndLine(c.endLine());
                    chunk.setContent(c.content());
                    chunkMapper.insert(chunk);
                    all.add(chunk);
                    payloads.add(new ChunkPayload(
                            repositoryId + "-" + chunk.getId(),
                            file.getId(),
                            file.getPath(),
                            file.getLanguage(),
                            c.startLine(), c.endLine(), c.content()));
                }
            }
            if (processed % 25 == 0) {
                task.setProcessedFiles(processed);
                updateTask(task);
            }
        }
        task.setProcessedFiles(processed);
        task.setTotalChunks(all.size());
        updateTask(task);
        return new InsertResult(all, payloads);
    }

    private void markRunning(IndexTask task, CodeRepository repo) {
        task.setStatus("RUNNING");
        task.setStartedAt(LocalDateTime.now());
        updateTask(task);
        repo.setImportStatus("PROCESSING");
        repositoryMapper.updateById(repo);
    }

    private void updateTask(IndexTask task) {
        taskMapper.update(null, new LambdaUpdateWrapper<IndexTask>()
                .eq(IndexTask::getId, task.getId())
                .set(IndexTask::getStatus, task.getStatus())
                .set(IndexTask::getStartedAt, task.getStartedAt())
                .set(IndexTask::getTotalFiles, task.getTotalFiles())
                .set(IndexTask::getProcessedFiles, task.getProcessedFiles())
                .set(IndexTask::getTotalChunks, task.getTotalChunks()));
    }

    private void finish(IndexTask task, String status, String errorMessage) {
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
