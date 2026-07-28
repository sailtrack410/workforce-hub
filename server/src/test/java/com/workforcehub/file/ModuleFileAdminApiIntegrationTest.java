package com.workforcehub.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.plugin.runtime.PluginEventDispatcher;
import com.workforcehub.plugin.sdk.DomainEvent;
import com.workforcehub.plugin.sdk.EventTypes;
import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    private static final long ADMIN_USER_ID = 8001L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @MockBean
    private PluginEventDispatcher pluginEventDispatcher;

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
            .andExpect(jsonPath("$.data.uploadTicketId").isString())
            .andReturn();

        JsonNode ticketData = data(ticketResult);
        long uploadTicketId = ticketData.get("uploadTicketId").asLong();
        String objectKey = ticketData.get("objectKey").asText();
        writeLocalObject(objectKey, content);

        MvcResult confirmResult = confirmAdminUpload(adminSession, uploadTicketId, objectKey, sha256, content.length, "后台制度文件.pdf")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.deduplicated").value(false))
            .andExpect(jsonPath("$.data.attachmentRefId").isString())
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
            .andExpect(jsonPath("$.data.attachmentRefId").isString());

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
            .andExpect(jsonPath("$.data.uploadTicketId").isString())
            .andExpect(jsonPath("$.data.attachmentRefId").isString())
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

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(pluginEventDispatcher, times(2)).dispatch(eventCaptor.capture());
        List<DomainEvent> uploadedEvents = eventCaptor.getAllValues();
        assertAdminFileUploadedEvent(uploadedEvents.get(0), existingAttachmentRefId);
        assertAdminFileUploadedEvent(uploadedEvents.get(1), firstAttachmentRefId);

        assertEquals(content.length * 2L, usedBytes("GLOBAL", null));
        assertEquals(2L, fileCount("GLOBAL", null));
        assertEquals(2L, attachmentRefCount(sha256, content.length));
        assertEquals(1L, uploadTicketCount(clientRequestId));
        assertTrue(existingAttachmentRefId > 0);
    }

    @Test
    @DisplayName("后台不应仅凭员工文件hash和大小直接命中秒传")
    void adminUploadTicketShouldNotDeduplicateEmployeeFileByHashOnly() throws Exception {
        Cookie employeeSession = loginEmployee();
        Cookie adminSession = loginAdmin();
        seedGlobalQuotaPolicy();
        byte[] content = "employee-private-hash-only-content".getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256(content);

        Long employeeAttachmentRefId = createConfirmedEmployeeFile(employeeSession, "员工私有文件.pdf", content);

        MvcResult adminTicketResult = createAdminUploadTicket(
                adminSession,
                "后台不能直接秒传员工文件.pdf",
                content.length,
                sha256,
                "admin-should-upload-own-copy"
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.deduplicated").value(false))
            .andExpect(jsonPath("$.data.uploadTicketId").isString())
            .andExpect(jsonPath("$.data.attachmentRefId").doesNotExist())
            .andReturn();

        assertTrue(data(adminTicketResult).get("objectKey").asText().contains("/"));
        assertEquals(0L, adminAttachmentRefCount());
        assertTrue(employeeAttachmentRefId > 0);
    }

    @Test
    @DisplayName("后台普通确认命中全局去重时应登记本次临时对象清理明细")
    void adminConfirmUploadShouldScheduleTemporaryObjectCleanupWhenGlobalDeduplicated() throws Exception {
        Cookie employeeSession = loginEmployee();
        seedGlobalQuotaPolicy();
        byte[] content = "admin-confirm-global-dedup-temp-object".getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256(content);

        createConfirmedEmployeeFile(employeeSession, "员工已有同内容文件.pdf", content);
        Cookie adminSession = loginAdmin();

        MvcResult adminTicketResult = createAdminUploadTicket(
                adminSession,
                "后台实际上传同内容文件.pdf",
                content.length,
                sha256,
                "admin-confirm-global-dedup-temp-object"
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.deduplicated").value(false))
            .andReturn();
        long uploadTicketId = data(adminTicketResult).get("uploadTicketId").asLong();
        String objectKey = data(adminTicketResult).get("objectKey").asText();
        writeLocalObject(objectKey, content);

        confirmAdminUpload(adminSession, uploadTicketId, objectKey, sha256, content.length, "后台实际上传同内容文件.pdf")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.deduplicated").value(true));

        assertEquals(1L, fileObjectCount(sha256, content.length));
        assertEquals(1L, cleanupDetailCountByObjectKey(objectKey, "DEDUPLICATED_UPLOAD_OBJECT", "PENDING"));
        assertTrue(Files.exists(Path.of(localRoot).resolve(objectKey)));

        runCleanupJob(adminSession, "FAILED_PHYSICAL_OBJECT", false)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.jobStatus").value("SUCCESS"))
            .andExpect(jsonPath("$.data.deletedPhysicalObjects").value(1))
            .andExpect(jsonPath("$.data.failedPhysicalObjects").value(0));

        assertFalse(Files.exists(Path.of(localRoot).resolve(objectKey)));
        assertEquals(1L, cleanupDetailCountByObjectKey(objectKey, "DEDUPLICATED_UPLOAD_OBJECT", "FAILED"));
        assertEquals(1L, cleanupDetailCountByObjectKey(objectKey, "DEDUPLICATED_UPLOAD_OBJECT", "SUCCESS"));
    }

    @Test
    @DisplayName("后台应能治理ADMIN_FILE列表、下载、回收站、恢复和彻底删除")
    void adminShouldManageAdminFileLifecycle() throws Exception {
        Cookie adminSession = loginAdmin();
        seedGlobalQuotaPolicy();
        byte[] content = "admin-lifecycle-content".getBytes(StandardCharsets.UTF_8);
        Long attachmentRefId = createConfirmedAdminFile(adminSession, "后台生命周期.pdf", content);

        mockMvc.perform(get("/api/admin/files")
                .cookie(adminSession)
                .param("pageNo", "1")
                .param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonLong("$.data.list[0].attachmentRefId", attachmentRefId))
            .andExpect(jsonPath("$.data.list[0].ownerType").value("ADMIN_FILE"))
            .andExpect(jsonPath("$.data.list[0].uploaderType").value("ADMIN"));

        MvcResult downloadTicketResult = mockMvc.perform(post("/api/admin/files/{attachmentRefId}/download-tickets", attachmentRefId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "downloadType": "SINGLE",
                      "reason": "后台生命周期下载验证"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.ticketNo").isString())
            .andReturn();
        String ticketNo = data(downloadTicketResult).get("ticketNo").asText();

        mockMvc.perform(get("/api/admin/files/download/{ticketNo}", ticketNo)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(content().bytes(content));

        mockMvc.perform(post("/api/admin/files/{attachmentRefId}/trash", attachmentRefId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "后台生命周期删除验证"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data.attachmentRefId", attachmentRefId))
            .andExpect(jsonPath("$.data.deletedAt").isString());

        mockMvc.perform(get("/api/admin/files/recycle-bin")
                .cookie(adminSession)
                .param("pageNo", "1")
                .param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonLong("$.data.list[0].attachmentRefId", attachmentRefId))
            .andExpect(jsonPath("$.data.list[0].deletedAt").isString());

        mockMvc.perform(post("/api/admin/files/{attachmentRefId}/restore", attachmentRefId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "后台生命周期恢复验证"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data.attachmentRefId", attachmentRefId));

        mockMvc.perform(post("/api/admin/files/{attachmentRefId}/trash", attachmentRefId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "后台生命周期再次删除验证"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(delete("/api/admin/files/{attachmentRefId}", attachmentRefId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data.attachmentRefId", attachmentRefId))
            .andExpect(jsonPath("$.data.physicalCleanupScheduled").value(true));

        assertEquals(0L, usedBytes("GLOBAL", null));
        assertEquals(0L, fileCount("GLOBAL", null));
        assertAuditCountAtLeast("FILE_ADMIN_DOWNLOAD_TICKET_CREATED", 1);
        assertAuditCountAtLeast("FILE_ADMIN_DOWNLOAD_EXECUTED", 1);
        assertAuditCountAtLeast("FILE_ADMIN_TRASHED", 2);
        assertAuditCountAtLeast("FILE_ADMIN_RESTORED", 1);
        assertAuditCountAtLeast("FILE_ADMIN_PURGED", 1);
    }

    @Test
    @DisplayName("后台同一下载票据并发执行时只能成功一次")
    void adminDownloadTicketShouldBeClaimedOnceUnderConcurrency() throws Exception {
        Cookie adminSession = loginAdmin();
        seedGlobalQuotaPolicy();
        byte[] content = "admin-concurrent-download-content".getBytes(StandardCharsets.UTF_8);
        Long attachmentRefId = createConfirmedAdminFile(adminSession, "后台并发下载.pdf", content);
        String ticketNo = createAdminDownloadTicket(adminSession, attachmentRefId);
        Long ticketId = ticketId(ticketNo);

        try (Connection lockConnection = dataSource.getConnection()) {
            lockConnection.setAutoCommit(false);
            try (PreparedStatement statement = lockConnection.prepareStatement(
                    "SELECT id FROM sys_download_ticket WHERE id = ? FOR UPDATE")) {
                statement.setLong(1, ticketId);
                statement.executeQuery();

                ExecutorService executor = Executors.newFixedThreadPool(2);
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                try {
                    Future<Integer> first = executor.submit(() -> downloadCode(adminSession, ticketNo, ready, start));
                    Future<Integer> second = executor.submit(() -> downloadCode(adminSession, ticketNo, ready, start));
                    ready.await();
                    start.countDown();
                    Thread.sleep(200L);
                    lockConnection.commit();

                    List<Integer> resultCodes = List.of(first.get(), second.get());
                    long successCount = resultCodes.stream().filter(code -> code == 0).count();
                    long conflictCount = resultCodes.stream().filter(code -> code == 100409).count();
                    assertEquals(1L, successCount);
                    assertEquals(1L, conflictCount);
                } finally {
                    executor.shutdownNow();
                }
            }
        }
    }

    @Test
    @DisplayName("后台访问过期下载票据后应持久化过期状态")
    void adminExpiredDownloadTicketShouldBeMarkedExpired() throws Exception {
        Cookie adminSession = loginAdmin();
        seedGlobalQuotaPolicy();
        byte[] content = "admin-expired-download-content".getBytes(StandardCharsets.UTF_8);
        Long attachmentRefId = createConfirmedAdminFile(adminSession, "后台过期下载.pdf", content);
        String ticketNo = createAdminDownloadTicket(adminSession, attachmentRefId);

        jdbcTemplate.update("""
            UPDATE sys_download_ticket
            SET expire_at = DATE_SUB(NOW(), INTERVAL 1 MINUTE)
            WHERE ticket_no = ?
            """, ticketNo);

        mockMvc.perform(get("/api/admin/files/download/{ticketNo}", ticketNo)
                .cookie(adminSession))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value(100410))
            .andExpect(jsonPath("$.message", containsString("下载链接已过期")));

        assertEquals("EXPIRED", downloadTicketStatus(ticketNo));
    }

    @Test
    @DisplayName("后台彻底删除同一附件时不应重复释放企业用量")
    void adminPurgeShouldReleaseUsageOnlyWhenStateChanged() throws Exception {
        Cookie adminSession = loginAdmin();
        seedGlobalQuotaPolicy();
        byte[] targetContent = "admin-purge-idempotent-target".getBytes(StandardCharsets.UTF_8);
        byte[] survivorContent = "admin-purge-idempotent-survivor".getBytes(StandardCharsets.UTF_8);
        Long attachmentRefId = createConfirmedAdminFile(adminSession, "后台重复彻底删除.pdf", targetContent);
        createConfirmedAdminFile(adminSession, "后台账本哨兵.pdf", survivorContent);
        trashAdminFile(adminSession, attachmentRefId);

        try (Connection lockConnection = dataSource.getConnection()) {
            lockConnection.setAutoCommit(false);
            try (PreparedStatement statement = lockConnection.prepareStatement(
                    "SELECT id FROM sys_attachment_ref WHERE id = ? FOR UPDATE")) {
                statement.setLong(1, attachmentRefId);
                statement.executeQuery();

                ExecutorService executor = Executors.newFixedThreadPool(2);
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                try {
                    Future<Integer> first = executor.submit(() -> purgeCode(adminSession, attachmentRefId, ready, start));
                    Future<Integer> second = executor.submit(() -> purgeCode(adminSession, attachmentRefId, ready, start));
                    ready.await();
                    start.countDown();
                    Thread.sleep(200L);
                    lockConnection.commit();

                    List<Integer> resultCodes = List.of(first.get(), second.get());
                    long successCount = resultCodes.stream().filter(code -> code == 0).count();
                    long notFoundCount = resultCodes.stream().filter(code -> code == 100404).count();
                    assertEquals(1L, successCount);
                    assertEquals(1L, notFoundCount);
                } finally {
                    executor.shutdownNow();
                }
            }
        }

        assertEquals(survivorContent.length, usedBytes("GLOBAL", null));
        assertEquals(1L, fileCount("GLOBAL", null));
    }

    @Test
    @DisplayName("后台文件列表默认不暴露对象存储内部字段")
    void adminFileListShouldHideStorageInternals() throws Exception {
        Cookie adminSession = loginAdmin();
        seedGlobalQuotaPolicy();
        byte[] content = "admin-list-storage-internals-content".getBytes(StandardCharsets.UTF_8);
        Long attachmentRefId = createConfirmedAdminFile(adminSession, "后台列表字段.pdf", content);

        mockMvc.perform(get("/api/admin/files")
                .cookie(adminSession)
                .param("pageNo", "1")
                .param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data.list[0].attachmentRefId", attachmentRefId))
            .andExpect(jsonPath("$.data.list[0].contentSha256").doesNotExist())
            .andExpect(jsonPath("$.data.list[0].storageProvider").doesNotExist())
            .andExpect(jsonPath("$.data.list[0].bucketName").doesNotExist())
            .andExpect(jsonPath("$.data.list[0].objectKey").doesNotExist());
    }

    @Test
    @DisplayName("后台企业文件目录应包含员工文件并支持筛选")
    void adminShouldBrowseEnterpriseFileCatalog() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee();
        seedGlobalQuotaPolicy();
        Long employeeAttachmentRefId = createConfirmedEmployeeFile(
            employeeSession, "员工目录凭证.pdf", "employee-catalog-content".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(get("/api/admin/files")
                .cookie(adminSession)
                .param("pageNo", "1")
                .param("pageSize", "20")
                .param("keyword", "员工目录凭证")
                .param("ownerType", "PERSONAL_FILE")
                .param("uploaderType", "USER")
                .param("uploaderId", String.valueOf(EMPLOYEE_USER_ID))
                .param("scanStatus", "PENDING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonLong("$.data.list[0].attachmentRefId", employeeAttachmentRefId))
            .andExpect(jsonPath("$.data.list[0].ownerType").value("PERSONAL_FILE"))
            .andExpect(jsonPath("$.data.list[0].uploaderType").value("USER"))
            .andExpect(jsonPath("$.data.list[0].uploaderName").value("张三"))
            .andExpect(jsonPath("$.data.list[0].employeeNo").value("E1003"))
            .andExpect(jsonPath("$.data.list[0].contentSha256").doesNotExist())
            .andExpect(jsonPath("$.data.list[0].objectKey").doesNotExist());

        mockMvc.perform(get("/api/admin/files")
                .cookie(adminSession)
                .param("pageNo", "1")
                .param("pageSize", "20")
                .param("keyword", "不存在的文件"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("后台下载遇到非法MIME时降级为二进制响应")
    void adminDownloadShouldFallbackWhenMimeTypeInvalid() throws Exception {
        Cookie adminSession = loginAdmin();
        seedGlobalQuotaPolicy();
        byte[] content = "admin-invalid-mime-content".getBytes(StandardCharsets.UTF_8);
        Long attachmentRefId = createConfirmedAdminFile(adminSession, "后台非法MIME.pdf", content);
        jdbcTemplate.update("""
            UPDATE sys_file_object fo
            JOIN sys_attachment_ref ar ON ar.file_object_id = fo.id
            SET fo.mime_type = ?
            WHERE ar.id = ?
            """, "bad mime", attachmentRefId);
        String ticketNo = createAdminDownloadTicket(adminSession, attachmentRefId);

        mockMvc.perform(get("/api/admin/files/download/{ticketNo}", ticketNo)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
            .andExpect(content().bytes(content));
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

    private org.springframework.test.web.servlet.ResultActions runCleanupJob(Cookie adminSession,
                                                                            String jobType,
                                                                            boolean dryRun) throws Exception {
        return mockMvc.perform(post("/api/admin/files/cleanup-jobs/run")
            .cookie(adminSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "jobType": "%s",
                  "dryRun": %s
                }
                """.formatted(jobType, dryRun)));
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

    private String createAdminDownloadTicket(Cookie adminSession, Long attachmentRefId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/files/{attachmentRefId}/download-tickets", attachmentRefId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "downloadType": "SINGLE",
                      "reason": "后台下载票据测试"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return data(result).get("ticketNo").asText();
    }

    private void trashAdminFile(Cookie adminSession, Long attachmentRefId) throws Exception {
        mockMvc.perform(post("/api/admin/files/{attachmentRefId}/trash", attachmentRefId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "后台测试移入回收站"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    private int downloadCode(Cookie adminSession,
                             String ticketNo,
                             CountDownLatch ready,
                             CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        MvcResult result = mockMvc.perform(get("/api/admin/files/download/{ticketNo}", ticketNo)
                .cookie(adminSession))
            .andReturn();
        String contentType = result.getResponse().getContentType();
        if (contentType != null && contentType.contains(MediaType.APPLICATION_JSON_VALUE)) {
            return objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asInt();
        }
        return 0;
    }

    private int purgeCode(Cookie adminSession,
                          Long attachmentRefId,
                          CountDownLatch ready,
                          CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        MvcResult result = mockMvc.perform(delete("/api/admin/files/{attachmentRefId}", attachmentRefId)
                .cookie(adminSession))
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asInt();
    }

    private Long ticketId(String ticketNo) {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM sys_download_ticket WHERE ticket_no = ?",
            Long.class,
            ticketNo
        );
    }

    private String downloadTicketStatus(String ticketNo) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM sys_download_ticket WHERE ticket_no = ?",
            String.class,
            ticketNo
        );
    }

    private org.springframework.test.web.servlet.ResultActions createEmployeeUploadTicket(
            Cookie session,
            String fileName,
            long fileSize,
            String sha256) throws Exception {
        return mockMvc.perform(post("/api/employee/files/upload-tickets")
            .cookie(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "ownerType": "PERSONAL_FILE",
                  "ownerId": null,
                  "fileName": "%s",
                  "mimeType": "application/pdf",
                  "fileSize": %s,
                  "contentSha256": "%s",
                  "contentMd5": null,
                  "clientRequestId": "employee-file-test-%s"
                }
                """.formatted(fileName, fileSize, sha256, System.nanoTime())));
    }

    private org.springframework.test.web.servlet.ResultActions confirmEmployeeUpload(
            Cookie session,
            long uploadTicketId,
            String objectKey,
            String sha256,
            long fileSize,
            String displayName) throws Exception {
        return mockMvc.perform(post("/api/employee/files/confirm-upload")
            .cookie(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "uploadTicketId": %s,
                  "objectKey": "%s",
                  "contentSha256": "%s",
                  "fileSize": %s,
                  "ownerType": "PERSONAL_FILE",
                  "ownerId": null,
                  "displayName": "%s"
                }
                """.formatted(uploadTicketId, objectKey, sha256, fileSize, displayName)));
    }

    private Long createConfirmedEmployeeFile(Cookie employeeSession, String fileName, byte[] content) throws Exception {
        String sha256 = sha256(content);
        MvcResult ticketResult = createEmployeeUploadTicket(employeeSession, fileName, content.length, sha256)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        JsonNode ticketData = data(ticketResult);
        Long uploadTicketId = ticketData.get("uploadTicketId").asLong();
        String objectKey = ticketData.get("objectKey").asText();
        writeLocalObject(objectKey, content);

        MvcResult confirmResult = confirmEmployeeUpload(employeeSession, uploadTicketId, objectKey, sha256, content.length, fileName)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return data(confirmResult).get("attachmentRefId").asLong();
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

    private long adminAttachmentRefCount() {
        Long value = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_attachment_ref
            WHERE uploader_type = 'ADMIN' AND owner_type = 'ADMIN_FILE' AND is_deleted = 0
            """, Long.class);
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

    private void assertAdminFileUploadedEvent(DomainEvent event, Long attachmentRefId) {
        assertEquals(EventTypes.FILE_UPLOADED, event.eventType());
        assertEquals(attachmentRefId, event.payload().get("attachmentRefId"));
        assertEquals("ADMIN_FILE", event.payload().get("ownerType"));
        assertEquals("ADMIN", event.payload().get("uploaderType"));
        assertEquals(ADMIN_USER_ID, event.payload().get("uploaderId"));
    }

    private long cleanupDetailCountByObjectKey(String objectKey, String itemType, String status) {
        Long value = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_file_cleanup_job_detail
            WHERE object_key = ? AND item_type = ? AND status = ?
            """, Long.class, objectKey, itemType, status);
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
