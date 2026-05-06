package com.workforcehub.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.file.service.StorageUsageLedgerService;
import com.workforcehub.file.storage.LocalStorageProvider;
import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * module-file 后台清理任务集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleFileCleanupIntegrationTest extends LocalIntegrationTestSupport {

    private static final long EMPLOYEE_USER_ID = 2003L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpyBean
    private LocalStorageProvider localStorageProvider;

    @SpyBean
    private StorageUsageLedgerService storageUsageLedgerService;

    @Value("${workforce.file.local-root:target/test-file-storage}")
    private String localRoot;

    @Test
    @DisplayName("清理任务dry-run只统计候选数据，不修改票据、引用和物理对象")
    void cleanupDryRunShouldOnlyReportCandidates() throws Exception {
        Cookie adminSession = loginAdmin();
        seedGlobalQuotaPolicy();
        ExpiredUploadFixture expiredUpload = createExpiredUploadTicket(adminSession, "dry-run-过期上传.pdf");
        String downloadTicketNo = createExpiredDownloadTicket(adminSession);
        Long recycleRefId = createExpiredAdminRecycleRef(adminSession, "dry-run-回收站.pdf",
            "dry-run-recycle-content".getBytes(StandardCharsets.UTF_8));
        Long unreferencedFileObjectId = createUnreferencedFileObject(
            "cleanup/dry-run-unreferenced.txt",
            "dry-run-unreferenced-content".getBytes(StandardCharsets.UTF_8)
        );

        MvcResult result = runCleanupJob(adminSession, "ALL_EXPIRED", true)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.jobType").value("ALL_EXPIRED"))
            .andExpect(jsonPath("$.data.dryRun").value(true))
            .andExpect(jsonPath("$.data.expiredUploadTickets").value(1))
            .andExpect(jsonPath("$.data.expiredDownloadTickets").value(1))
            .andExpect(jsonPath("$.data.purgedAttachmentRefs").value(1))
            .andExpect(jsonPath("$.data.deletedPhysicalObjects").value(2))
            .andReturn();

        assertTrue(data(result).get("jobRunId").asText().startsWith("FILE-CLEAN-"));
        assertEquals("PENDING", uploadTicketStatus(expiredUpload.uploadTicketId()));
        assertEquals("ACTIVE", downloadTicketStatus(downloadTicketNo));
        assertEquals(0, attachmentDeleted(recycleRefId));
        assertEquals(0, fileObjectDeleted(unreferencedFileObjectId));
        assertTrue(Files.exists(objectPath(expiredUpload.objectKey())));
        assertAuditCountAtLeast("FILE_CLEANUP_JOB_RUN", 1);
    }

    @Test
    @DisplayName("清理任务真实执行会过期票据、清理到期回收站引用、删除无引用物理对象并写审计")
    void cleanupExecuteShouldExpireTicketsPurgeRefsDeleteObjectsAndAudit() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee();
        seedGlobalQuotaPolicy();
        ExpiredUploadFixture expiredUpload = createExpiredUploadTicket(adminSession, "execute-过期上传.pdf");
        String downloadTicketNo = createExpiredDownloadTicket(adminSession);
        Long activeDownloadAttachmentRefId = attachmentRefIdByDownloadTicket(downloadTicketNo);

        byte[] adminRecycleContent = "execute-admin-recycle-content".getBytes(StandardCharsets.UTF_8);
        byte[] employeeRecycleContent = "execute-employee-recycle-content".getBytes(StandardCharsets.UTF_8);
        Long adminRecycleRefId = createExpiredAdminRecycleRef(adminSession, "execute-后台回收.pdf", adminRecycleContent);
        Long employeeRecycleRefId = createExpiredEmployeeRecycleRef(employeeSession, "execute-员工回收.pdf", employeeRecycleContent);
        Long unreferencedFileObjectId = createUnreferencedFileObject(
            "cleanup/execute-unreferenced.txt",
            "execute-unreferenced-content".getBytes(StandardCharsets.UTF_8)
        );

        MvcResult result = runCleanupJob(adminSession, "ALL_EXPIRED", false)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.jobType").value("ALL_EXPIRED"))
            .andExpect(jsonPath("$.data.dryRun").value(false))
            .andExpect(jsonPath("$.data.expiredUploadTickets").value(1))
            .andExpect(jsonPath("$.data.expiredDownloadTickets").value(1))
            .andExpect(jsonPath("$.data.purgedAttachmentRefs").value(2))
            .andExpect(jsonPath("$.data.deletedPhysicalObjects").value(2))
            .andExpect(jsonPath("$.data.releasedBytes").value(adminRecycleContent.length + employeeRecycleContent.length))
            .andExpect(jsonPath("$.data.jobStatus").value("SUCCESS"))
            .andExpect(jsonPath("$.data.failedPhysicalObjects").value(0))
            .andReturn();

        assertEquals("EXPIRED", uploadTicketStatus(expiredUpload.uploadTicketId()));
        assertEquals("EXPIRED", downloadTicketStatus(downloadTicketNo));
        assertEquals(1, attachmentDeleted(adminRecycleRefId));
        assertEquals(1, attachmentDeleted(employeeRecycleRefId));
        assertEquals(1, fileObjectDeleted(unreferencedFileObjectId));
        assertFalse(Files.exists(objectPath(expiredUpload.objectKey())));
        assertEquals(activeFileSize(activeDownloadAttachmentRefId), usedBytes("GLOBAL", null));
        assertEquals(0L, usedBytes("USER", EMPLOYEE_USER_ID));
        String jobRunId = data(result).get("jobRunId").asText();
        assertEquals("SUCCESS", cleanupJobStatus(jobRunId));
        assertEquals(1, cleanupDetailCount(jobRunId, "EXPIRED_UPLOAD_TICKET", "SUCCESS"));
        assertEquals(1, cleanupDetailCount(jobRunId, "UNREFERENCED_FILE_OBJECT", "SUCCESS"));
        assertAuditCountAtLeast("FILE_CLEANUP_JOB_RUN", 1);
    }

    @Test
    @DisplayName("物理删除失败时清理任务应保留运行记录和失败明细")
    void cleanupPhysicalDeleteFailureShouldBeRecordedWithoutRollingBackRun() throws Exception {
        Cookie adminSession = loginAdmin();
        Long fileObjectId = createUnreferencedFileObject(
            "MISSING_PROVIDER",
            "missing",
            "cleanup/missing-provider.txt",
            "missing-provider-content".getBytes(StandardCharsets.UTF_8)
        );

        MvcResult result = runCleanupJob(adminSession, "UNREFERENCED_FILE_OBJECT", false)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.jobType").value("UNREFERENCED_FILE_OBJECT"))
            .andExpect(jsonPath("$.data.jobStatus").value("PARTIAL_FAILED"))
            .andExpect(jsonPath("$.data.deletedPhysicalObjects").value(0))
            .andExpect(jsonPath("$.data.failedPhysicalObjects").value(1))
            .andReturn();

        String jobRunId = data(result).get("jobRunId").asText();
        assertEquals(1, fileObjectDeleted(fileObjectId));
        assertEquals("PARTIAL_FAILED", cleanupJobStatus(jobRunId));
        assertEquals(1, cleanupDetailCount(jobRunId, "UNREFERENCED_FILE_OBJECT", "FAILED"));
    }

    @Test
    @DisplayName("物理删除失败明细可以通过失败重试任务恢复")
    void cleanupFailedPhysicalDeleteShouldBeRetried() throws Exception {
        Cookie adminSession = loginAdmin();
        String objectKey = "cleanup/retry-unreferenced.txt";
        Long fileObjectId = createUnreferencedFileObject(
            objectKey,
            "retry-unreferenced-content".getBytes(StandardCharsets.UTF_8)
        );
        doThrow(new BizException(500, "模拟物理删除失败"))
            .doThrow(new BizException(500, "模拟物理删除再次失败"))
            .doCallRealMethod()
            .when(localStorageProvider)
            .deleteObject(eq("local"), eq(objectKey));

        MvcResult failedResult = runCleanupJob(adminSession, "UNREFERENCED_FILE_OBJECT", false)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.jobStatus").value("PARTIAL_FAILED"))
            .andExpect(jsonPath("$.data.deletedPhysicalObjects").value(0))
            .andExpect(jsonPath("$.data.failedPhysicalObjects").value(1))
            .andReturn();

        String failedRunId = data(failedResult).get("jobRunId").asText();
        assertEquals(1, fileObjectDeleted(fileObjectId));
        assertTrue(Files.exists(objectPath(objectKey)));
        assertEquals(1, cleanupDetailCount(failedRunId, "UNREFERENCED_FILE_OBJECT", "FAILED"));

        MvcResult retryFailedResult = runCleanupJob(adminSession, "FAILED_PHYSICAL_OBJECT", false)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.jobType").value("FAILED_PHYSICAL_OBJECT"))
            .andExpect(jsonPath("$.data.jobStatus").value("PARTIAL_FAILED"))
            .andExpect(jsonPath("$.data.deletedPhysicalObjects").value(0))
            .andExpect(jsonPath("$.data.failedPhysicalObjects").value(1))
            .andReturn();

        String retryFailedRunId = data(retryFailedResult).get("jobRunId").asText();
        assertTrue(Files.exists(objectPath(objectKey)));
        assertEquals(1, cleanupDetailCount(retryFailedRunId, "UNREFERENCED_FILE_OBJECT", "FAILED"));

        MvcResult retryResult = runCleanupJob(adminSession, "FAILED_PHYSICAL_OBJECT", false)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.jobType").value("FAILED_PHYSICAL_OBJECT"))
            .andExpect(jsonPath("$.data.jobStatus").value("SUCCESS"))
            .andExpect(jsonPath("$.data.deletedPhysicalObjects").value(1))
            .andExpect(jsonPath("$.data.failedPhysicalObjects").value(0))
            .andReturn();

        String retryRunId = data(retryResult).get("jobRunId").asText();
        assertFalse(Files.exists(objectPath(objectKey)));
        assertEquals(1, cleanupDetailCount(failedRunId, "UNREFERENCED_FILE_OBJECT", "FAILED"));
        assertEquals(1, cleanupDetailCount(retryFailedRunId, "UNREFERENCED_FILE_OBJECT", "FAILED"));
        assertEquals(1, cleanupDetailCount(retryRunId, "UNREFERENCED_FILE_OBJECT", "SUCCESS"));

        runCleanupJob(adminSession, "FAILED_PHYSICAL_OBJECT", true)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.deletedPhysicalObjects").value(0));
    }

    @Test
    @DisplayName("历史PENDING物理清理明细可以通过失败重试任务恢复")
    void cleanupPendingPhysicalDeleteShouldBeRecoveredByRetryJob() throws Exception {
        Cookie adminSession = loginAdmin();
        String objectKey = "cleanup/recover-pending-object.txt";
        byte[] content = "recover-pending-object-content".getBytes(StandardCharsets.UTF_8);
        writeLocalObject(objectKey, content);
        String stuckJobRunId = "FILE-CLEAN-STUCK-" + System.nanoTime();
        jdbcTemplate.update("""
            INSERT INTO sys_file_cleanup_job_run
                (id, job_run_id, job_type, dry_run, status, operator_id, started_at)
            VALUES
                (?, ?, 'UNREFERENCED_FILE_OBJECT', 0, 'RUNNING', 8001, ?)
            """,
            System.nanoTime(),
            stuckJobRunId,
            LocalDateTime.now().minusHours(2)
        );
        jdbcTemplate.update("""
            INSERT INTO sys_file_cleanup_job_detail
                (id, job_run_id, item_type, target_type, target_id,
                 storage_provider, bucket_name, object_key, status)
            VALUES
                (?, ?, 'UNREFERENCED_FILE_OBJECT', 'FILE_OBJECT', ?, 'LOCAL', 'local', ?, 'PENDING')
            """,
            System.nanoTime(),
            stuckJobRunId,
            System.nanoTime(),
            objectKey
        );

        MvcResult result = runCleanupJob(adminSession, "FAILED_PHYSICAL_OBJECT", false)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.jobType").value("FAILED_PHYSICAL_OBJECT"))
            .andExpect(jsonPath("$.data.jobStatus").value("SUCCESS"))
            .andExpect(jsonPath("$.data.deletedPhysicalObjects").value(1))
            .andExpect(jsonPath("$.data.failedPhysicalObjects").value(0))
            .andReturn();

        String recoveryJobRunId = data(result).get("jobRunId").asText();
        assertFalse(Files.exists(objectPath(objectKey)));
        assertEquals("FAILED", cleanupJobStatus(stuckJobRunId));
        assertEquals(1, cleanupDetailCount(stuckJobRunId, "UNREFERENCED_FILE_OBJECT", "FAILED"));
        assertEquals(1, cleanupDetailCount(recoveryJobRunId, "UNREFERENCED_FILE_OBJECT", "SUCCESS"));
    }

    @Test
    @DisplayName("数据库阶段失败时清理任务应保留失败运行记录")
    void cleanupDatabaseStageFailureShouldRecordFailedRun() throws Exception {
        Cookie adminSession = loginAdmin();
        seedGlobalQuotaPolicy();
        Long recycleRefId = createExpiredAdminRecycleRef(
            adminSession,
            "db-stage-failure.pdf",
            "db-stage-failure-content".getBytes(StandardCharsets.UTF_8)
        );
        doThrow(new RuntimeException("模拟数据库阶段失败"))
            .when(storageUsageLedgerService)
            .releaseUsage(eq("GLOBAL"), isNull(), anyLong(), anyInt());

        MvcResult result = runCleanupJob(adminSession, "EXPIRED_RECYCLE_REF", false)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.jobStatus").value("FAILED"))
            .andExpect(jsonPath("$.data.errorMessage").value("模拟数据库阶段失败"))
            .andReturn();

        String jobRunId = data(result).get("jobRunId").asText();
        assertEquals(0, attachmentDeleted(recycleRefId));
        assertEquals("FAILED", cleanupJobStatus(jobRunId));
        assertEquals("模拟数据库阶段失败", cleanupJobErrorMessage(jobRunId));
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

    private ExpiredUploadFixture createExpiredUploadTicket(Cookie adminSession, String fileName) throws Exception {
        byte[] content = ("expired-upload-" + fileName).getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256(content);
        MvcResult ticketResult = createAdminUploadTicket(adminSession, fileName, content.length, sha256)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        JsonNode ticketData = data(ticketResult);
        Long uploadTicketId = ticketData.get("uploadTicketId").asLong();
        String objectKey = ticketData.get("objectKey").asText();
        writeLocalObject(objectKey, content);
        jdbcTemplate.update("UPDATE sys_upload_ticket SET expire_at = ? WHERE id = ?",
            LocalDateTime.now().minusMinutes(10),
            uploadTicketId);
        return new ExpiredUploadFixture(uploadTicketId, objectKey);
    }

    private String createExpiredDownloadTicket(Cookie adminSession) throws Exception {
        byte[] content = "expired-download-active-file".getBytes(StandardCharsets.UTF_8);
        Long attachmentRefId = createConfirmedAdminFile(adminSession, "过期下载源文件.pdf", content);
        MvcResult result = mockMvc.perform(post("/api/admin/files/{attachmentRefId}/download-tickets", attachmentRefId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "downloadType": "SINGLE",
                      "reason": "清理任务测试下载票据"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        String ticketNo = data(result).get("ticketNo").asText();
        jdbcTemplate.update("UPDATE sys_download_ticket SET expire_at = ? WHERE ticket_no = ?",
            LocalDateTime.now().minusMinutes(10),
            ticketNo);
        return ticketNo;
    }

    private Long createExpiredAdminRecycleRef(Cookie adminSession, String fileName, byte[] content) throws Exception {
        Long attachmentRefId = createConfirmedAdminFile(adminSession, fileName, content);
        trashAdminFile(adminSession, attachmentRefId);
        jdbcTemplate.update("UPDATE sys_attachment_ref SET purge_after = ? WHERE id = ?",
            LocalDateTime.now().minusMinutes(10),
            attachmentRefId);
        return attachmentRefId;
    }

    private Long createExpiredEmployeeRecycleRef(Cookie employeeSession, String fileName, byte[] content) throws Exception {
        Long attachmentRefId = createConfirmedEmployeeFile(employeeSession, fileName, content);
        mockMvc.perform(post("/api/employee/files/{attachmentRefId}/trash", attachmentRefId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "清理任务测试员工回收站"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
        jdbcTemplate.update("UPDATE sys_attachment_ref SET purge_after = ? WHERE id = ?",
            LocalDateTime.now().minusMinutes(10),
            attachmentRefId);
        return attachmentRefId;
    }

    private Long createUnreferencedFileObject(String objectKey, byte[] content) throws Exception {
        return createUnreferencedFileObject("LOCAL", "local", objectKey, content);
    }

    private Long createUnreferencedFileObject(String storageProvider,
                                              String bucketName,
                                              String objectKey,
                                              byte[] content) throws Exception {
        writeLocalObject(objectKey, content);
        Long fileObjectId = System.nanoTime();
        jdbcTemplate.update("""
            INSERT INTO sys_file_object
                (id, storage_provider, bucket_name, object_key, file_name, file_ext, mime_type,
                 file_size, file_hash, upload_status, content_md5, content_sha256, ref_count,
                 scan_status, cleanup_after, is_deleted)
            VALUES
                (?, ?, ?, ?, '无引用文件.txt', 'txt', 'text/plain',
                 ?, ?, 'UPLOADED', NULL, ?, 0, 'PASSED', ?, 0)
            """,
            fileObjectId,
            storageProvider,
            bucketName,
            objectKey,
            content.length,
            sha256(content),
            sha256(content),
            LocalDateTime.now().minusMinutes(10));
        return fileObjectId;
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

        MvcResult confirmResult = mockMvc.perform(post("/api/admin/files/confirm-upload")
                .cookie(adminSession)
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
                    """.formatted(uploadTicketId, objectKey, sha256, content.length, fileName)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return data(confirmResult).get("attachmentRefId").asLong();
    }

    private Long createConfirmedEmployeeFile(Cookie employeeSession, String fileName, byte[] content) throws Exception {
        String sha256 = sha256(content);
        MvcResult ticketResult = mockMvc.perform(post("/api/employee/files/upload-tickets")
                .cookie(employeeSession)
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
                      "clientRequestId": "cleanup-employee-file-%s"
                    }
                    """.formatted(fileName, content.length, sha256, System.nanoTime())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        JsonNode ticketData = data(ticketResult);
        Long uploadTicketId = ticketData.get("uploadTicketId").asLong();
        String objectKey = ticketData.get("objectKey").asText();
        writeLocalObject(objectKey, content);

        MvcResult confirmResult = mockMvc.perform(post("/api/employee/files/confirm-upload")
                .cookie(employeeSession)
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
                    """.formatted(uploadTicketId, objectKey, sha256, content.length, fileName)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return data(confirmResult).get("attachmentRefId").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions createAdminUploadTicket(
            Cookie session,
            String fileName,
            long fileSize,
            String sha256) throws Exception {
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
                  "clientRequestId": "cleanup-admin-file-%s"
                }
                """.formatted(fileName, fileSize, sha256, System.nanoTime())));
    }

    private void trashAdminFile(Cookie adminSession, Long attachmentRefId) throws Exception {
        mockMvc.perform(post("/api/admin/files/{attachmentRefId}/trash", attachmentRefId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "清理任务测试后台回收站"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
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
        Path target = objectPath(objectKey);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
    }

    private Path objectPath(String objectKey) {
        return Path.of(localRoot).resolve(objectKey).normalize();
    }

    private String uploadTicketStatus(Long uploadTicketId) {
        return jdbcTemplate.queryForObject("SELECT status FROM sys_upload_ticket WHERE id = ?", String.class, uploadTicketId);
    }

    private String downloadTicketStatus(String ticketNo) {
        return jdbcTemplate.queryForObject("SELECT status FROM sys_download_ticket WHERE ticket_no = ?", String.class, ticketNo);
    }

    private Long attachmentRefIdByDownloadTicket(String ticketNo) {
        return jdbcTemplate.queryForObject(
            "SELECT attachment_ref_id FROM sys_download_ticket WHERE ticket_no = ?",
            Long.class,
            ticketNo
        );
    }

    private int attachmentDeleted(Long attachmentRefId) {
        Integer value = jdbcTemplate.queryForObject(
            "SELECT is_deleted FROM sys_attachment_ref WHERE id = ?",
            Integer.class,
            attachmentRefId
        );
        return value == null ? 0 : value;
    }

    private int fileObjectDeleted(Long fileObjectId) {
        Integer value = jdbcTemplate.queryForObject(
            "SELECT is_deleted FROM sys_file_object WHERE id = ?",
            Integer.class,
            fileObjectId
        );
        return value == null ? 0 : value;
    }

    private String cleanupJobStatus(String jobRunId) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM sys_file_cleanup_job_run WHERE job_run_id = ?",
            String.class,
            jobRunId
        );
    }

    private String cleanupJobErrorMessage(String jobRunId) {
        return jdbcTemplate.queryForObject(
            "SELECT error_message FROM sys_file_cleanup_job_run WHERE job_run_id = ?",
            String.class,
            jobRunId
        );
    }

    private int cleanupDetailCount(String jobRunId, String itemType, String status) {
        Integer value = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_file_cleanup_job_detail
            WHERE job_run_id = ? AND item_type = ? AND status = ?
            """,
            Integer.class,
            jobRunId,
            itemType,
            status
        );
        return value == null ? 0 : value;
    }

    private long activeFileSize(Long attachmentRefId) {
        Long value = jdbcTemplate.queryForObject("""
            SELECT fo.file_size
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE ar.id = ?
            """, Long.class, attachmentRefId);
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

    private record ExpiredUploadFixture(Long uploadTicketId, String objectKey) {}
}
