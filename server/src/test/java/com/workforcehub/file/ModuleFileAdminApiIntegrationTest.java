package com.workforcehub.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * module-file 后台文件治理接口自动化冒烟。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleFileAdminApiIntegrationTest extends LocalIntegrationTestSupport {

    private static final long EMPLOYEE_USER_ID = 2003L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${workforce.file.local-root:target/test-file-storage}")
    private String localRoot;

    @Test
    @DisplayName("后台应能完成上传票据、确认上传与秒传闭环")
    void adminShouldUploadConfirmAndDeduplicateFile() throws Exception {
        Cookie adminSession = loginAdmin();
        seedGlobalQuotaPolicy();
        byte[] content = "admin-governance-file-content".getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256(content);

        MvcResult ticketResult = createAdminUploadTicket(adminSession, "后台制度文件.pdf", content.length, sha256)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.deduplicated").value(false))
            .andExpect(jsonPath("$.data.uploadTicketId").isNumber())
            .andReturn();

        JsonNode ticketData = data(ticketResult);
        long uploadTicketId = ticketData.get("uploadTicketId").asLong();
        String objectKey = ticketData.get("objectKey").asText();
        writeLocalObject(objectKey, content);

        MvcResult confirmResult = confirmAdminUpload(adminSession, uploadTicketId, objectKey, sha256, content.length, "后台制度文件.pdf")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.deduplicated").value(false))
            .andExpect(jsonPath("$.data.attachmentRefId").isNumber())
            .andReturn();

        long firstAttachmentRefId = data(confirmResult).get("attachmentRefId").asLong();
        Long adminUploaderCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_attachment_ref
            WHERE id = ? AND uploader_type = 'ADMIN'
            """, Long.class, firstAttachmentRefId);
        assertEquals(1L, adminUploaderCount);

        createAdminUploadTicket(adminSession, "后台制度文件-重命名.pdf", content.length, sha256)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.deduplicated").value(true))
            .andExpect(jsonPath("$.data.attachmentRefId").isNumber());

        assertEquals(1L, fileObjectCount(sha256, content.length));
        assertEquals(content.length * 2L, usedBytes("GLOBAL", null));
        assertEquals(2L, fileCount("GLOBAL", null));
        assertEquals(0L, usedBytes("USER", EMPLOYEE_USER_ID));
        assertAuditCountAtLeast("FILE_ADMIN_UPLOAD_TICKET_CREATED", 1);
        assertAuditCountAtLeast("FILE_ADMIN_UPLOAD_CONFIRMED", 1);
        assertAuditCountAtLeast("FILE_ADMIN_UPLOAD_DEDUP", 1);
    }

    @Test
    @DisplayName("后台秒传重复客户端请求ID应返回同一附件引用且不重复加账")
    void adminDeduplicatedUploadShouldBeIdempotentByClientRequestId() throws Exception {
        Cookie adminSession = loginAdmin();
        seedGlobalQuotaPolicy();
        byte[] content = "admin-dedup-idempotent-content".getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256(content);
        Long existingAttachmentRefId = createConfirmedAdminFile(adminSession, "后台秒传源文件.pdf", content);
        String clientRequestId = "same-admin-deduplicated-upload";

        MvcResult firstDedupResult = createAdminUploadTicket(
                adminSession,
                "后台秒传复用-1.pdf",
                content.length,
                sha256,
                clientRequestId
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.deduplicated").value(true))
            .andExpect(jsonPath("$.data.uploadTicketId").isNumber())
            .andExpect(jsonPath("$.data.attachmentRefId").isNumber())
            .andReturn();
        long firstUploadTicketId = data(firstDedupResult).get("uploadTicketId").asLong();
        long firstAttachmentRefId = data(firstDedupResult).get("attachmentRefId").asLong();

        MvcResult secondDedupResult = createAdminUploadTicket(
                adminSession,
                "后台秒传复用-2.pdf",
                content.length,
                sha256,
                clientRequestId
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.deduplicated").value(true))
            .andReturn();

        assertEquals(firstUploadTicketId, data(secondDedupResult).get("uploadTicketId").asLong());
        assertEquals(firstAttachmentRefId, data(secondDedupResult).get("attachmentRefId").asLong());
        assertEquals(content.length * 2L, usedBytes("GLOBAL", null));
        assertEquals(2L, fileCount("GLOBAL", null));
        assertEquals(2L, attachmentRefCount(sha256, content.length));
        assertEquals(1L, uploadTicketCount(clientRequestId));
        assertTrue(existingAttachmentRefId > 0);
    }

    private org.springframework.test.web.servlet.ResultActions createAdminUploadTicket(
            Cookie session,
            String fileName,
            long fileSize,
            String sha256) throws Exception {
        return createAdminUploadTicket(session, fileName, fileSize, sha256, "admin-file-test-" + System.nanoTime());
    }

    private org.springframework.test.web.servlet.ResultActions createAdminUploadTicket(
            Cookie session,
            String fileName,
            long fileSize,
            String sha256,
            String clientRequestId) throws Exception {
        return mockMvc.perform(post("/api/admin/files/upload-tickets")
            .cookie(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "ownerType": "ADMIN_FILE",
                  "ownerId": null,
                  "fileName": "%s",
                  "mimeType": "application/pdf",
                  "fileSize": %s,
                  "contentSha256": "%s",
                  "contentMd5": null,
                  "clientRequestId": "%s"
                }
                """.formatted(fileName, fileSize, sha256, clientRequestId)));
    }

    private org.springframework.test.web.servlet.ResultActions confirmAdminUpload(
            Cookie session,
            long uploadTicketId,
            String objectKey,
            String sha256,
            long fileSize,
            String displayName) throws Exception {
        return mockMvc.perform(post("/api/admin/files/confirm-upload")
            .cookie(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "uploadTicketId": %s,
                  "objectKey": "%s",
                  "contentSha256": "%s",
                  "fileSize": %s,
                  "ownerType": "ADMIN_FILE",
                  "ownerId": null,
                  "displayName": "%s"
                }
                """.formatted(uploadTicketId, objectKey, sha256, fileSize, displayName)));
    }

    private Long createConfirmedAdminFile(Cookie adminSession, String fileName, byte[] content) throws Exception {
        String sha256 = sha256(content);
        MvcResult ticketResult = createAdminUploadTicket(adminSession, fileName, content.length, sha256)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        JsonNode ticketData = data(ticketResult);
        Long uploadTicketId = ticketData.get("uploadTicketId").asLong();
        String objectKey = ticketData.get("objectKey").asText();
        writeLocalObject(objectKey, content);

        MvcResult confirmResult = confirmAdminUpload(adminSession, uploadTicketId, objectKey, sha256, content.length, fileName)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return data(confirmResult).get("attachmentRefId").asLong();
    }

    private Cookie loginAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "admin.demo",
                      "password": "Admin@123456"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(cookie().exists("SESSION"))
            .andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    private void seedGlobalQuotaPolicy() {
        jdbcTemplate.update("""
            INSERT INTO sys_storage_quota_policy
                (id, policy_code, policy_name, scope_type, scope_id, quota_bytes,
                 cache_quota_bytes, warning_threshold, block_threshold, status)
            VALUES
                (?, 'GLOBAL_400G', 'GLOBAL_400G', 'GLOBAL', NULL, ?, NULL, 0.8, 1.0, 'ENABLED')
            """, System.nanoTime(), 400L * 1024L * 1024L * 1024L);
    }

    private JsonNode data(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.get("data");
    }

    private void writeLocalObject(String objectKey, byte[] content) throws Exception {
        Path target = Path.of(localRoot).resolve(objectKey);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
    }

    private long fileObjectCount(String sha256, long fileSize) {
        Long value = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_file_object
            WHERE content_sha256 = ? AND file_size = ?
            """, Long.class, sha256, fileSize);
        return value == null ? 0L : value;
    }

    private long usedBytes(String scopeType, Long scopeId) {
        Long value = jdbcTemplate.query("""
            SELECT used_bytes FROM sys_storage_usage
            WHERE scope_type = ? AND ((scope_id IS NULL AND ? IS NULL) OR scope_id = ?)
            """,
            rs -> rs.next() ? rs.getLong("used_bytes") : 0L,
            scopeType,
            scopeId,
            scopeId
        );
        return value == null ? 0L : value;
    }

    private long fileCount(String scopeType, Long scopeId) {
        Long value = jdbcTemplate.query("""
            SELECT file_count FROM sys_storage_usage
            WHERE scope_type = ? AND ((scope_id IS NULL AND ? IS NULL) OR scope_id = ?)
            """,
            rs -> rs.next() ? rs.getLong("file_count") : 0L,
            scopeType,
            scopeId,
            scopeId
        );
        return value == null ? 0L : value;
    }

    private long attachmentRefCount(String sha256, long fileSize) {
        Long value = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE fo.content_sha256 = ? AND fo.file_size = ? AND ar.is_deleted = 0
            """, Long.class, sha256, fileSize);
        return value == null ? 0L : value;
    }

    private long uploadTicketCount(String clientRequestId) {
        Long value = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_upload_ticket
            WHERE client_request_id = ? AND status = 'CONFIRMED' AND is_deleted = 0
            """, Long.class, clientRequestId);
        return value == null ? 0L : value;
    }

    private void assertAuditCountAtLeast(String actionType, int expectedMin) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_audit_log WHERE action_type = ?",
            Integer.class,
            actionType
        );
        assertTrue(count != null && count >= expectedMin, "应写入审计动作: " + actionType);
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
