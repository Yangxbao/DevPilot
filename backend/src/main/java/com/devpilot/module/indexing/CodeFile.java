package com.devpilot.module.indexing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代码文件，对应表 code_file。被过滤的文件也会记录（skip_reason 说明原因）。
 */
@Data
@TableName("code_file")
public class CodeFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long repositoryId;

    private String path;

    private String language;

    private Long size;

    private Integer lineCount;

    /** 文本内容；二进制/超限文件为 null */
    private String content;

    /** binary / too_large / unsupported / dependency_dir / sensitive */
    private String skipReason;

    private LocalDateTime createdAt;
}
