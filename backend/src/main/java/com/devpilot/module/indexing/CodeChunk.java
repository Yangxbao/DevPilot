package com.devpilot.module.indexing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代码分块，对应表 code_chunk。检索的基本单元。
 */
@Data
@TableName("code_chunk")
public class CodeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long repositoryId;

    private Long fileId;

    private Integer chunkIndex;

    private Integer startLine;

    private Integer endLine;

    private String content;

    private LocalDateTime createdAt;
}
