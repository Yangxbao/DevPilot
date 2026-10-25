package com.devpilot.module.indexing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 索引任务，对应表 index_task。
 * active=1 表示进行中（PENDING/RUNNING），终态置 NULL；UNIQUE(repository_id, active) 防并发重复。
 */
@Data
@TableName("index_task")
public class IndexTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long repositoryId;

    private Long projectId;

    private String taskNo;

    /** MANUAL / REINDEX */
    private String triggerType;

    /** PENDING / RUNNING / SUCCESS / FAILED */
    private String status;

    private Integer active;

    private Integer totalFiles;

    private Integer processedFiles;

    private Integer totalChunks;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
