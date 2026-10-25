package com.devpilot.module.defect;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 缺陷处理记录，对应表 defect_comment。
 */
@Data
@TableName("defect_comment")
public class DefectComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long defectId;

    private Long authorId;

    private String content;

    private LocalDateTime createdAt;
}
