package com.devpilot.module.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代码仓库导入源，对应表 repository。
 */
@Data
@TableName("repository")
public class CodeRepository {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String name;

    /** GIT_URL / UPLOAD */
    private String sourceType;

    private String gitUrl;

    private String gitRef;

    private String fileName;

    /** PENDING / PROCESSING / SUCCESS / FAILED */
    private String importStatus;

    private String errorMessage;

    private Integer fileCount;

    private Long totalSize;

    private LocalDateTime lastIndexedAt;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
