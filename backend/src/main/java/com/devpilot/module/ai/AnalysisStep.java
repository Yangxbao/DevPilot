package com.devpilot.module.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 执行步骤轨迹，对应表 analysis_step。
 */
@Data
@TableName("analysis_step")
public class AnalysisStep {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long analysisId;

    private Integer stepOrder;

    private String name;

    /** RUNNING / SUCCESS / FAILED / SKIPPED */
    private String status;

    private String detail;

    private Long durationMs;

    private LocalDateTime createdAt;
}
