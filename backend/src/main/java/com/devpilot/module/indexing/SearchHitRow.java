package com.devpilot.module.indexing;

import lombok.Data;

/**
 * 检索命中行（Mapper 原生映射）。
 */
@Data
public class SearchHitRow {
    private Long chunkId;
    private Long fileId;
    private String path;
    private String language;
    private Integer startLine;
    private Integer endLine;
    private String content;
    private Double score;
}
