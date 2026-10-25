package com.devpilot.module.defect;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 缺陷任务，对应表 defect。
 * 状态机：OPEN → IN_PROGRESS → RESOLVED → CLOSED；IN_PROGRESS/RESOLVED 可退回 OPEN；CLOSED 为终态。
 */
@Data
@TableName("defect")
public class Defect {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String title;

    private String description;

    /** CRITICAL / MAJOR / MEDIUM / MINOR */
    private String severity;

    /** P0 / P1 / P2 / P3 */
    private String priority;

    /** OPEN / IN_PROGRESS / RESOLVED / CLOSED */
    private String status;

    private Long assigneeId;

    private Long reporterId;

    private Long analysisId;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
