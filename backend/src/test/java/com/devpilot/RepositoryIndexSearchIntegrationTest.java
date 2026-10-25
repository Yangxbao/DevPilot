package com.devpilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.module.ai.AiIndexClient;
import com.devpilot.module.ai.dto.AgentResultDto;
import com.devpilot.module.ai.dto.SearchResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 导入->索引->检索 集成链路（AI 客户端打桩：索引推送返回 vector=true，不依赖 Python）。
 */
class RepositoryIndexSearchIntegrationTest extends IntegrationTestBase {

    @MockitoBean
    private AiIndexClient aiIndexClient;

    private void stubAiClient() {
        when(aiIndexClient.reindex(anyLong(), any())).thenReturn(true);
        when(aiIndexClient.search(anyLong(), any(), anyInt(), any())).thenReturn(
                new SearchResponseDto(true, List.of()));
        when(aiIndexClient.qa(anyLong(), any(), anyInt())).thenReturn(new AgentResultDto(
                "mock", "mock", "mock-v1", "测试回答", 0.5, List.of(), List.of(), List.of(), 1L, null));
    }

    static byte[] demoZip() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("demo/src/TaskService.java"));
            zip.write("""
                    public class TaskService {
                        public void completeTask(Long id) {
                            // completeTask 应可被检索
                        }
                    }
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("demo/.env"));
            zip.write("API_KEY=fake".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("demo/node_modules/lib.js"));
            zip.write("var x=1;".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private long uploadRepo(String token, long projectId) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "demo.zip", "application/zip", demoZip());
        String body = mockMvc.perform(multipart("/api/v1/projects/{id}/repositories/upload", projectId)
                        .file(file).param("name", "demo")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asLong();
    }

    private String waitTaskTerminal(String token, long taskId) throws Exception {
        String status = "PENDING";
        for (int i = 0; i < 40; i++) {
            Thread.sleep(500);
            String body = mockMvc.perform(get("/api/v1/index-tasks/{id}", taskId)
                            .header("Authorization", "Bearer " + token))
                    .andReturn().getResponse().getContentAsString();
            JsonNode node = objectMapper.readTree(body).get("data");
            status = node.get("status").asText();
            if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
                break;
            }
        }
        return status;
    }

    @Test
    @DisplayName("上传zip->索引成功->关键词检索命中，敏感与依赖文件被过滤")
    void uploadIndexSearchFlow() throws Exception {
        stubAiClient();
        String token = registerAndLogin(uniqueName("u"));
        long projectId = createProject(token, uniqueName("proj"));
        long repoId = uploadRepo(token, projectId);

        String taskBody = mockMvc.perform(post("/api/v1/repositories/{id}/index-tasks", repoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        long taskId = objectMapper.readTree(taskBody).get("data").get("id").asLong();

        String status = waitTaskTerminal(token, taskId);
        assertEquals("SUCCESS", status, "索引任务应成功");

        // 文件清单：TaskService 正常入库；.env 标记 sensitive；node_modules 整目录跳过
        String files = mockMvc.perform(get("/api/v1/repositories/{id}/files", repoId)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        assertTrue(files.contains("TaskService.java"), "应包含 TaskService.java");
        assertTrue(files.contains("sensitive"), ".env 应被标记 sensitive: " + files);
        assertFalse(files.contains("node_modules"), "node_modules 不应出现在文件清单");

        // 关键词检索命中
        String search = mockMvc.perform(get("/api/v1/search")
                        .header("Authorization", "Bearer " + token)
                        .param("repositoryId", String.valueOf(repoId))
                        .param("q", "completeTask")
                        .param("mode", "keyword"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        assertTrue(search.contains("TaskService.java"), "检索应命中 TaskService.java: " + search);
    }

    @Test
    @DisplayName("同一仓库并发重复索引被唯一索引拒绝(409)")
    void duplicateIndexTaskRejected() throws Exception {
        stubAiClient();
        String token = registerAndLogin(uniqueName("u"));
        long projectId = createProject(token, uniqueName("proj"));
        long repoId = uploadRepo(token, projectId);

        mockMvc.perform(post("/api/v1/repositories/{id}/index-tasks", repoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/repositories/{id}/index-tasks", repoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }
}
