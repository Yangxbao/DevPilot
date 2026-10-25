package com.devpilot.module.indexing;

import com.devpilot.common.config.AppProperties;
import com.devpilot.module.project.CodeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 代码导入服务：从 zip 压缩包或本地克隆的 Git 目录读取文件清单。
 *
 * 安全规则（用户导入内容一律不可信）：
 * - 绝不执行任何被导入的文件，只做文本读取；
 * - zip 只读条目流，不落盘解压（不存在 Zip-Slip 写入面）；
 * - 条目路径做规范化校验（拒绝绝对路径、..、反斜杠注入）；
 * - 三重上限：条目数 / 总字节 / 单文件字节；
 * - 依赖目录、敏感文件（.env、*.key、id_rsa 等）明确过滤并记录原因。
 */
@Slf4j
@Service
public class ImportService {

    public static final int MAX_ENTRIES = 8000;
    public static final long MAX_TOTAL_BYTES = 200L * 1024 * 1024;
    public static final long MAX_FILE_BYTES = 512L * 1024;
    public static final int MAX_CLONE_SECONDS = 120;

    private static final Set<String> DENY_DIR_NAMES = Set.of(
            "node_modules", "target", "build", "dist", "out", "bin", "obj", "vendor",
            "__pycache__", ".git", ".idea", ".vscode", ".gradle", "venv", ".venv", "coverage");

    private static final List<String> SENSITIVE_NAME_RULES = List.of(
            ".env", ".pem", ".key", ".p12", ".pfx", ".jks", ".keystore", "id_rsa", "id_dsa", ".htpasswd", "credentials");

    /** 单条导入结果 */
    public record ImportedFile(String path, String language, long size, int lineCount, String content, String skipReason) {
    }

    /** 上限被突破时抛出，任务以失败结束 */
    public static class ImportAbortedException extends RuntimeException {
        public ImportAbortedException(String message) {
            super(message);
        }
    }

    private final AppProperties properties;

    public ImportService(AppProperties properties) {
        this.properties = properties;
    }

    public List<ImportedFile> importFromUpload(Long repositoryId) {
        Path zipPath = Path.of(properties.getStorage().getPath(), "archives", repositoryId + ".zip")
                .toAbsolutePath().normalize();
        if (!Files.exists(zipPath)) {
            throw new ImportAbortedException("找不到已上传的压缩包");
        }
        try (ZipFile zipFile = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            Map<String, ImportedFile> result = new LinkedHashMap<>();
            var entries = zipFile.entries();
            long totalBytes = 0;
            int seen = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++seen > MAX_ENTRIES) {
                    throw new ImportAbortedException("压缩包条目数超过上限 " + MAX_ENTRIES);
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String safePath = sanitizeEntryPath(entry.getName());
                if (safePath == null) {
                    continue;
                }
                // 不信任条目声明大小（流式 zip 可能为 -1 或伪造），读取时强制限额
                ImportedFile imported = readEntry(zipFile, entry, safePath);
                totalBytes += imported.size();
                if (totalBytes > MAX_TOTAL_BYTES) {
                    throw new ImportAbortedException("压缩包解压总量超过上限 " + MAX_TOTAL_BYTES / 1024 / 1024 + "MB");
                }
                result.put(safePath, imported);
            }
            return new ArrayList<>(result.values());
        } catch (ImportAbortedException e) {
            throw e;
        } catch (IOException e) {
            log.error("读取压缩包失败 repoId={}", repositoryId, e);
            throw new ImportAbortedException("压缩包读取失败: " + e.getMessage());
        }
    }

    public List<ImportedFile> importFromGit(CodeRepository repo) {
        Path workRoot = Path.of(properties.getStorage().getPath(), "repos").toAbsolutePath().normalize();
        Path target = workRoot.resolve(String.valueOf(repo.getId()));
        try {
            Files.createDirectories(workRoot);
            if (Files.exists(target)) {
                deleteRecursively(target);
            }
            Files.createDirectories(target);
        } catch (IOException e) {
            throw new ImportAbortedException("创建克隆目录失败: " + e.getMessage());
        }

        List<String> command = new ArrayList<>(List.of("git", "clone", "--depth", "1", "--single-branch", "--no-tags"));
        if (repo.getGitRef() != null && !repo.getGitRef().isBlank()) {
            command.add("--branch");
            command.add(repo.getGitRef());
        }
        command.add(repo.getGitUrl());
        command.add(target.toString());

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.environment().put("GIT_TERMINAL_PROMPT", "0");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(MAX_CLONE_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ImportAbortedException("git clone 超时（>" + MAX_CLONE_SECONDS + "s）");
            }
            if (process.exitValue() != 0) {
                throw new ImportAbortedException("git clone 失败: " + tail(output, 300));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImportAbortedException("git clone 被中断");
        } catch (IOException e) {
            throw new ImportAbortedException("git clone 执行失败: " + e.getMessage());
        }

        List<ImportedFile> files = new ArrayList<>();
        long[] totalBytes = {0};
        try {
            Files.walkFileTree(target, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (DENY_DIR_NAMES.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String relative = target.relativize(file).toString().replace('\\', '/');
                    if (files.size() >= MAX_ENTRIES) {
                        throw new ImportAbortedException("文件数超过上限 " + MAX_ENTRIES);
                    }
                    totalBytes[0] += attrs.size();
                    if (totalBytes[0] > MAX_TOTAL_BYTES) {
                        throw new ImportAbortedException("仓库总大小超过上限 " + MAX_TOTAL_BYTES / 1024 / 1024 + "MB");
                    }
                    files.add(readFileSystem(file, relative, attrs.size()));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (ImportAbortedException e) {
            throw e;
        } catch (IOException e) {
            throw new ImportAbortedException("遍历克隆目录失败: " + e.getMessage());
        } finally {
            // 克隆产物为派生数据，读取完成后立即清理
            try {
                deleteRecursively(target);
            } catch (IOException e) {
                log.warn("清理克隆目录失败 repoId={}（忽略）", repo.getId());
            }
        }
        return files;
    }

    // ---------- 内部方法 ----------

    private ImportedFile readEntry(ZipFile zipFile, ZipEntry entry, String safePath) {
        try (var in = zipFile.getInputStream(entry)) {
            byte[] head = in.readNBytes(8192);
            if (Chunker.looksBinary(head)) {
                return new ImportedFile(safePath, "", head.length, 0, null, "binary");
            }
            // 多读 1 字节用于判断超限，杜绝超大文件占用内存
            byte[] rest = in.readNBytes((int) (MAX_FILE_BYTES + 1 - head.length));
            byte[] all = concat(head, rest);
            if (all.length > MAX_FILE_BYTES) {
                return new ImportedFile(safePath, "", all.length, 0, null, "too_large");
            }
            return buildTextFile(safePath, all);
        } catch (IOException e) {
            return new ImportedFile(safePath, "", 0, 0, null, "read_error");
        }
    }

    private ImportedFile readFileSystem(Path file, String relativePath, long size) {
        String fileName = file.getFileName().toString().toLowerCase();
        if (isSensitiveName(fileName)) {
            return new ImportedFile(relativePath, "", size, 0, null, "sensitive");
        }
        String language = Chunker.languageOf(relativePath);
        if (language.isEmpty()) {
            return new ImportedFile(relativePath, "", size, 0, null, "unsupported");
        }
        if (size > MAX_FILE_BYTES) {
            return new ImportedFile(relativePath, language, size, 0, null, "too_large");
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (Chunker.looksBinary(bytes)) {
                return new ImportedFile(relativePath, language, size, 0, null, "binary");
            }
            return buildTextFile(relativePath, bytes);
        } catch (IOException e) {
            return new ImportedFile(relativePath, language, size, 0, null, "read_error");
        }
    }

    private ImportedFile buildTextFile(String path, byte[] bytes) {
        String fileName = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
        if (isSensitiveName(fileName)) {
            return new ImportedFile(path, "", bytes.length, 0, null, "sensitive");
        }
        String language = Chunker.languageOf(path);
        if (language.isEmpty()) {
            return new ImportedFile(path, "", bytes.length, 0, null, "unsupported");
        }
        if (bytes.length > MAX_FILE_BYTES) {
            return new ImportedFile(path, language, bytes.length, 0, null, "too_large");
        }
        String content = new String(bytes, StandardCharsets.UTF_8);
        int lineCount = countLines(content);
        return new ImportedFile(path, language, bytes.length, lineCount, content, null);
    }

    /** zip 条目路径安全化：拒绝目录穿越/绝对路径/盘符，返回规范化的相对路径（/ 分隔） */
    static String sanitizeEntryPath(String rawName) {
        String name = rawName.replace('\\', '/');
        if (name.startsWith("/") || name.contains("../") || name.contains("/..")
                || name.matches("^[A-Za-z]:.*") || name.contains("\0")) {
            return null;
        }
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        // 依赖目录黑名单（任意层级命中即跳过）
        for (String segment : name.split("/")) {
            if (DENY_DIR_NAMES.contains(segment)) {
                return null;
            }
        }
        return name;
    }

    static boolean isSensitiveName(String lowerFileName) {
        return SENSITIVE_NAME_RULES.stream().anyMatch(lowerFileName::contains);
    }

    static int countLines(String content) {
        int lines = 1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lines++;
            }
        }
        return content.isEmpty() ? 0 : lines;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] all = new byte[a.length + b.length];
        System.arraycopy(a, 0, all, 0, a.length);
        System.arraycopy(b, 0, all, a.length, b.length);
        return all;
    }

    private static void deleteRecursively(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                    // 尽力清理
                }
            });
        }
    }

    private static String tail(String s, int max) {
        String trimmed = s.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(trimmed.length() - max);
    }
}
