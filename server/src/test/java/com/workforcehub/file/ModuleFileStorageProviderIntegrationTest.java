package com.workforcehub.file;

import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文件存储提供方配置冒烟。
 */
@SpringBootTest(properties = {
    "workforce.file.storage-provider=MINIO",
    "minio.endpoint=http://localhost:9000",
    "minio.access-key=minioadmin",
    "minio.secret-key=minioadmin",
    "minio.bucket=workforce-hub-test"
})
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleFileStorageProviderIntegrationTest extends LocalIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("员工上传票据应使用配置的对象存储提供方")
    void employeeUploadTicketShouldUseConfiguredStorageProvider() throws Exception {
        Cookie session = loginEmployee();
        seedDefaultQuotaPolicy();
        byte[] content = "minio-upload-ticket-content".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/employee/files/upload-tickets")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "ownerType": "PERSONAL_FILE",
                      "ownerId": null,
                      "fileName": "生产上传.txt",
                      "mimeType": "text/plain",
                      "fileSize": %s,
                      "contentSha256": "%s",
                      "contentMd5": null,
                      "clientRequestId": "minio-provider-ticket"
                    }
                    """.formatted(content.length, sha256(content))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.storageProvider").value("MINIO"))
            .andExpect(jsonPath("$.data.bucketName").value("workforce-hub-test"))
            .andExpect(jsonPath("$.data.uploadUrl").value(startsWith("http://localhost:9000/")));
    }

    private void seedDefaultQuotaPolicy() {
        seedQuotaPolicy("USER_DEFAULT_10G", "USER_DEFAULT", null, 10L * 1024L * 1024L * 1024L);
        seedQuotaPolicy("GLOBAL_400G", "GLOBAL", null, 400L * 1024L * 1024L * 1024L);
    }

    private void seedQuotaPolicy(String policyCode, String scopeType, Long scopeId, long quotaBytes) {
        jdbcTemplate.update("""
            INSERT INTO sys_storage_quota_policy
                (id, policy_code, policy_name, scope_type, scope_id, quota_bytes,
                 cache_quota_bytes, warning_threshold, block_threshold, status)
            VALUES
                (?, ?, ?, ?, ?, ?, NULL, 0.8, 1.0, 'ENABLED')
            """, System.nanoTime(), policyCode, policyCode, scopeType, scopeId, quotaBytes);
    }

    private Cookie loginEmployee() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "zhangsan.employee",
                      "password": "Employee@123456"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(cookie().exists("SESSION"))
            .andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    private String sha256(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(content));
    }
}
