package com.devpilot.module.indexing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devpilot.module.indexing.CodeChunk;
import com.devpilot.module.indexing.SearchHitRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CodeChunkMapper extends BaseMapper<CodeChunk> {

    /**
     * MySQL FULLTEXT(ngram) 布尔模式检索，按相关度排序。
     */
    @Select("""
            SELECT c.id AS chunkId, c.file_id AS fileId, f.path AS path, f.language AS language,
                   c.start_line AS startLine, c.end_line AS endLine, c.content AS content,
                   MATCH(c.content) AGAINST(#{q} IN BOOLEAN MODE) AS score
            FROM code_chunk c
            JOIN code_file f ON f.id = c.file_id
            WHERE c.repository_id = #{repositoryId}
              AND f.skip_reason IS NULL
              AND MATCH(c.content) AGAINST(#{q} IN BOOLEAN MODE)
            ORDER BY score DESC
            LIMIT #{limit}
            """)
    List<SearchHitRow> searchFulltext(@Param("repositoryId") Long repositoryId,
                                      @Param("q") String q,
                                      @Param("limit") int limit);

    /**
     * FULLTEXT 无命中时的 LIKE 兜底（例如短标识符）。
     */
    @Select("""
            SELECT c.id AS chunkId, c.file_id AS fileId, f.path AS path, f.language AS language,
                   c.start_line AS startLine, c.end_line AS endLine, c.content AS content,
                   0.0 AS score
            FROM code_chunk c
            JOIN code_file f ON f.id = c.file_id
            WHERE c.repository_id = #{repositoryId}
              AND f.skip_reason IS NULL
              AND c.content LIKE CONCAT('%', #{kw}, '%')
            ORDER BY c.id
            LIMIT #{limit}
            """)
    List<SearchHitRow> searchLike(@Param("repositoryId") Long repositoryId,
                                  @Param("kw") String kw,
                                  @Param("limit") int limit);
}
