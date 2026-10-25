package com.devpilot.module.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 分析记录，对应表 ai_analysis。问答(QA)与 Bug 分析(BUG_ANALYSIS)统一存储。
 */
@Data
@TableName("ai_analysis")
public class AiAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long repositoryId;

    /** QA / BUG_ANALYSIS */
    private String type;

    /** PENDING / RUNNING / SUCCESS / FAILED */
    private String status;

    private String question;

    private String inputDetail;

    private String answer;

    /** JSON：{ evidence[], steps[], warnings[], report{} } */
    private String structuredResult;

    /** real / mock */
    private String mode;

    private String provider;

    private String model;

    private BigDecimal confidence;

    private Integer evidenceCount;

    private Long durationMs;

    private String errorMessage;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
