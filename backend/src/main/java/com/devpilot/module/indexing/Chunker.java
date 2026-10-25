package com.devpilot.module.indexing;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 纯函数式分块与语言识别工具（无状态、可单测）。
 */
public final class Chunker {

    /** 每块行数 */
    public static final int LINES_PER_CHUNK = 60;
    /** 单文件最大分块数（超出部分截断，避免极端大文件拖垮索引） */
    public static final int MAX_CHUNKS_PER_FILE = 200;
    /** 单块最大字符数（再截断） */
    public static final int MAX_CHUNK_CHARS = 8000;

    private static final Map<String, String> EXT_LANGUAGE = Map.ofEntries(
            Map.entry("java", "java"), Map.entry("py", "python"), Map.entry("js", "javascript"),
            Map.entry("jsx", "javascript"), Map.entry("ts", "typescript"), Map.entry("tsx", "typescript"),
            Map.entry("vue", "vue"), Map.entry("go", "go"), Map.entry("c", "c"), Map.entry("h", "c"),
            Map.entry("cpp", "cpp"), Map.entry("hpp", "cpp"), Map.entry("cs", "csharp"),
            Map.entry("rb", "ruby"), Map.entry("php", "php"), Map.entry("rs", "rust"),
            Map.entry("kt", "kotlin"), Map.entry("swift", "swift"), Map.entry("sql", "sql"),
            Map.entry("xml", "xml"), Map.entry("yaml", "yaml"), Map.entry("yml", "yaml"),
            Map.entry("json", "json"), Map.entry("md", "markdown"), Map.entry("txt", "text"),
            Map.entry("html", "html"), Map.entry("css", "css"), Map.entry("scss", "scss"),
            Map.entry("properties", "properties"), Map.entry("gradle", "gradle"), Map.entry("sh", "shell"),
            Map.entry("toml", "toml"), Map.entry("ini", "ini"), Map.entry("cfg", "ini"),
            Map.entry("conf", "ini"));

    private Chunker() {
    }

    public static String languageOf(String path) {
        String name = path.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return EXT_LANGUAGE.getOrDefault(name.substring(dot + 1), "");
    }

    public record Chunk(int startLine, int endLine, String content) {
    }

    /**
     * 按固定行数分块（无重叠），行号从 1 开始；超长单块内容再按字符截断。
     */
    public static List<Chunk> chunk(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String[] lines = content.split("\n", -1);
        // 以 \n 结尾的文本 split 会多出一个空尾条目，剔除以免行号虚高
        if (lines.length > 0 && lines[lines.length - 1].isEmpty()) {
            String[] trimmed = new String[lines.length - 1];
            System.arraycopy(lines, 0, trimmed, 0, trimmed.length);
            lines = trimmed;
        }
        if (lines.length == 0) {
            return List.of();
        }
        List<Chunk> result = new java.util.ArrayList<>();
        for (int start = 0; start < lines.length && result.size() < MAX_CHUNKS_PER_FILE; start += LINES_PER_CHUNK) {
            int end = Math.min(start + LINES_PER_CHUNK, lines.length);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                sb.append(lines[i]).append('\n');
            }
            String text = sb.toString();
            if (text.length() > MAX_CHUNK_CHARS) {
                text = text.substring(0, MAX_CHUNK_CHARS);
            }
            result.add(new Chunk(start + 1, end, text));
        }
        return result;
    }

    /**
     * 二进制内容嗅探：前 8KB 出现 NUL 字节视为二进制。
     */
    public static boolean looksBinary(byte[] bytes) {
        int n = Math.min(bytes.length, 8192);
        for (int i = 0; i < n; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    /** 允许索引的扩展名（小写、不含点） */
    public static final Set<String> ALLOWED_EXTENSIONS = EXT_LANGUAGE.keySet();
}
