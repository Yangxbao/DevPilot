package com.devpilot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 认证与权限：注册/登录/错误口令/未认证 401/管理员接口 403/越权访问他人项目 403。
 */
class AuthPermissionIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("注册->登录->读取个人信息 全链路")
    void registerLoginMe() throws Exception {
        String username = uniqueName("u");
        String token = registerAndLogin(username);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("错误密码登录被拒绝")
    void wrongPasswordRejected() throws Exception {
        String username = uniqueName("u");
        registerAndLogin(username);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"WrongPass999\"}".formatted(username)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("伪造 Token 访问受保护接口返回 401")
    void forgedTokenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("普通用户访问管理员接口返回 403")
    void userCannotAccessAdminApi() throws Exception {
        String token = registerAndLogin(uniqueName("u"));

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("越权读取他人项目返回 403")
    void userCannotReadOthersProject() throws Exception {
        String ownerToken = registerAndLogin(uniqueName("owner"));
        long projectId = createProject(ownerToken, uniqueName("proj"));

        String attackerToken = registerAndLogin(uniqueName("attacker"));

        mockMvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    @DisplayName("管理员可以访问任意项目，普通用户可以访问自己的项目")
    void adminCanAccessAnyProject() throws Exception {
        String ownerToken = registerAndLogin(uniqueName("owner"));
        long projectId = createProject(ownerToken, uniqueName("proj"));

        String adminToken = loginAdmin();
        mockMvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("登出后 Token 立即失效")
    void logoutInvalidatesToken() throws Exception {
        String username = uniqueName("u");
        String token = registerAndLogin(username);

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        MvcResult me = mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andReturn();
        int status = me.getResponse().getStatus();
        assertTrue(status == 401, "登出后的 Token 应被拒绝，实际 " + status);
        assertFalse(status == 200);
    }
}
