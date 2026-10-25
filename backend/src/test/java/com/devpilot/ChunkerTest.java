package com.devpilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devpilot.module.indexing.Chunker;
import com.devpilot.module.indexing.ImportService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分块器与导入过滤的纯单元测试（无 Spring 上下文）。
 */
class ChunkerTest {

    @Test
    @DisplayName("语言识别：扩展名映射正确，未知返回空")
    void languageOf() {
        assertEquals("java", Chunker.languageOf("src/Main.java"));
        assertEquals("python", Chunker.languageOf("app/main.py"));
        assertEquals("markdown", Chunker.languageOf("docs/readme.MD"));
        assertEquals("", Chunker.languageOf("assets/logo.xyz"));
        assertEquals("", Chunker.languageOf("noextension"));
    }

    @Test
    @DisplayName("分块：行号连续、内容对齐")
    void chunkLines() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 150; i++) {
            sb.append("line-").append(i).append('\n');
        }
        List<Chunker.Chunk> chunks = Chunker.chunk(sb.toString());
        assertEquals(3, chunks.size());
        assertEquals(1, chunks.get(0).startLine());
        assertEquals(60, chunks.get(0).endLine());
        assertEquals(61, chunks.get(1).startLine());
        assertEquals(150, chunks.get(2).endLine());
        assertTrue(chunks.get(0).content().startsWith("line-1\n"));
        assertTrue(chunks.get(2).content().contains("line-150"));
    }

    @Test
    @DisplayName("分块上限：超多行文件最多 200 块")
    void chunkCap() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50000; i++) {
            sb.append("x\n");
        }
        List<Chunker.Chunk> chunks = Chunker.chunk(sb.toString());
        assertEquals(Chunker.MAX_CHUNKS_PER_FILE, chunks.size());
    }

    @Test
    @DisplayName("二进制嗅探：含 NUL 字节判定为二进制")
    void binarySniff() {
        assertTrue(Chunker.looksBinary(new byte[]{'a', 'b', 0, 'c'}));
        assertFalse(Chunker.looksBinary("纯文本内容".getBytes()));
    }

    @Test
    @DisplayName("zip 条目路径安全化：拒绝穿越与依赖目录")
    void sanitizeEntryPath() {
        assertEquals(null, ImportService.sanitizeEntryPath("../evil.txt"));
        assertEquals(null, ImportService.sanitizeEntryPath("/abs/path.txt"));
        assertEquals(null, ImportService.sanitizeEntryPath("demo/node_modules/lib.js"));
        assertEquals(null, ImportService.sanitizeEntryPath("demo/target/app.jar"));
        assertEquals("demo/src/Main.java", ImportService.sanitizeEntryPath("demo\\src\\Main.java"));
    }

    @Test
    @DisplayName("敏感文件名识别")
    void sensitiveNames() {
        assertTrue(ImportService.isSensitiveName(".env"));
        assertTrue(ImportService.isSensitiveName("id_rsa"));
        assertTrue(ImportService.isSensitiveName("server.keystore"));
        assertFalse(ImportService.isSensitiveName("application.yml"));
    }
}
