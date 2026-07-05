package com.workforcehub.auth;

import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * module-auth 后台 RBAC 接口自动化冒烟
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleRbacApiIntegrationTest extends LocalIntegrationTestSupport {

    private static final String ADMIN_LOGIN_NAME = "admin.demo";
    private static final String ADMIN_PASSWORD = "Admin@123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("管理员应能维护角色并分配权限")
    void adminShouldManageRolesAndPermissions() throws Exception {
        Cookie session = login(ADMIN_LOGIN_NAME, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/admin/rbac/permissions").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[?(@.permCode=='ADMIN_RBAC_MANAGE')]").exists());

        mockMvc.perform(get("/api/admin/rbac/roles").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.roleCode=='SUPER_ADMIN')]").exists());

        MvcResult createResult = mockMvc.perform(post("/api/admin/rbac/roles")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "roleCode": "AUDITOR",
                      "roleName": "审计员",
                      "status": "ACTIVE"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.roleCode").value("AUDITOR"))
            .andReturn();

        String roleId = com.jayway.jsonpath.JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.roleId").toString();

        mockMvc.perform(put("/api/admin/rbac/roles/" + roleId)
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "roleCode": "AUDITOR",
                      "roleName": "审计主管",
                      "status": "ACTIVE"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roleName").value("审计主管"));

        mockMvc.perform(put("/api/admin/rbac/roles/" + roleId + "/permissions")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "permissionIds": [7001]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.permissions[0].permCode").value("ADMIN_RBAC_MANAGE"));

        jdbcTemplate.update("""
            INSERT INTO admin_user (id, username, password_hash, display_name, email, status, is_deleted)
            VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0)
            """, 8102L, "auditor.admin", passwordEncoder.encode("Auditor@123456"), "审计管理员", "auditor.admin@example.com");

        mockMvc.perform(put("/api/admin/rbac/admin-users/8102/roles")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "roleIds": [%s]
                    }
                    """.formatted(roleId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roles[0].roleCode").value("AUDITOR"));

        mockMvc.perform(get("/api/admin/rbac/admin-users").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.username=='auditor.admin')].roles[0].roleCode").value("AUDITOR"));

        mockMvc.perform(get("/api/admin/rbac/admin-users/8102/roles").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roles[0].roleCode").value("AUDITOR"));

        mockMvc.perform(delete("/api/admin/rbac/roles/" + roleId).cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("缺少权限的管理员不允许维护 RBAC")
    void adminWithoutPermissionShouldBeRejected() throws Exception {
        jdbcTemplate.update("""
            INSERT INTO admin_user (id, username, password_hash, display_name, email, status, is_deleted)
            VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0)
            """, 8101L, "limited.admin", passwordEncoder.encode("Limited@123456"), "受限管理员", "limited.admin@example.com");

        Cookie session = login("limited.admin", "Limited@123456");

        mockMvc.perform(post("/api/admin/rbac/roles")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "roleCode": "LIMITED",
                      "roleName": "受限角色",
                      "status": "ACTIVE"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));
    }

    private Cookie login(String loginName, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "%s"
                    }
                    """.formatted(loginName, password)))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();

        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session, "登录成功后应返回 SESSION Cookie");
        return session;
    }
}
