package com.workforcehub.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.springframework.dao.DuplicateKeyException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * module-file 第一版文件中心接口自动化冒烟。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleFileApiIntegrationTest extends LocalIntegrationTestSupport {

    private static final long EMPLOYEE_USER_ID = 2003L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Value("${workforce.file.local-root:target/test-file-storage}")
    private String localRoot;

    @Test
    @DisplayName("员工端上传应拒绝未开放的业务归属")
    void employeeUploadTicketShouldRejectUnsupportedBusinessOwnerType() throws Exception {
        Cookie employeeSession = loginEmployee();
        seedDefaultQuotaPolicy();
        byte[] content = "workflow-attachment-content".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/employee/files/upload-tickets")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "ownerType": "WF_REQUEST",
                      "ownerId": 90001,
                      "fileName": "审批附件.txt",
                      "mimeType": "text/plain",
                      "fileSize": %s,
                      "contentSha256": "%s",
                      "contentMd5": null,
                      "clientRequestId": "wf-owner-should-reject"
                    }
                    """.formatted(content.length, sha256(content))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(100400))
            .andExpect(jsonPath("$.message", containsString("不支持该文件归属")));
    }

    @Test
    @DisplayName("头像附件应独立于个人文件中心并允许当前用户内联下载")
    void avatarAttachmentShouldStayOutOfPersonalFileCenterAndAllowInlineDownload() throws Exception {
        Cookie employeeSession = loginEmployee();
        seedDefaultQuotaPolicy();
        byte[] content = "avatar-image-content".getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256(content);

        MvcResult ticketResult = createUploadTicket(
                employeeSession,
                "avatar.png",
                "image/png",
                content.length,
                sha256,
                "avatar-upload-" + System.nanoTime(),
                "USER_AVATAR")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        JsonNode ticketData = data(ticketResult);
        Long uploadTicketId = ticketData.get("uploadTicketId").asLong();
        String objectKey = ticketData.get("objectKey").asText();
        writeLocalObject(objectKey, content);

        MvcResult confirmResult = confirmUpload(
                employeeSession,
                uploadTicketId,
                objectKey,
                sha256,
                content.length,
                "avatar.png",
                "USER_AVATAR")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        Long avatarAttachmentRefId = data(confirmResult).get("attachmentRefId").asLong();

        assertEquals(1, personalFileListHiddenCount(avatarAttachmentRefId));
        mockMvc.perform(get("/api/employee/files")
                .cookie(employeeSession)
                .param("pageNo", "1")
                .param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.list[?(@.attachmentRefId == %s)]".formatted(avatarAttachmentRefId)).isEmpty());

        mockMvc.perform(post("/api/employee/files/{attachmentRefId}/download-tickets", avatarAttachmentRefId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "downloadType": "INLINE"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.downloadUrl").value(containsString("/api/employee/files/download/")));
    }

    @Test
    @DisplayName("头像附件应允许其他员工内联获取下载票据")
    void avatarAttachmentShouldAllowOtherEmployeeInlineDownloadTicket() throws Exception {
        Cookie ownerSession = loginEmployee();
        Cookie otherEmployeeSession = loginEmployee("lisi.manager");
        seedDefaultQuotaPolicy();
        byte[] content = "shared-avatar-image-content".getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256(content);

        MvcResult ticketResult = createUploadTicket(
                ownerSession,
                "shared-avatar.png",
                "image/png",
                content.length,
                sha256,
                "shared-avatar-upload-" + System.nanoTime(),
                "USER_AVATAR")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        JsonNode ticketData = data(ticketResult);
        Long uploadTicketId = ticketData.get("uploadTicketId").asLong();
        String objectKey = ticketData.get("objectKey").asText();
        writeLocalObject(objectKey, content);

        MvcResult confirmResult = confirmUpload(
                ownerSession,
                uploadTicketId,
                objectKey,
                sha256,
                content.length,
                "shared-avatar.png",
                "USER_AVATAR")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        Long avatarAttachmentRefId = data(confirmResult).get("attachmentRefId").asLong();

        MvcResult downloadTicketResult = mockMvc.perform(post("/api/employee/files/{attachmentRefId}/download-tickets", avatarAttachmentRefId)
                .cookie(otherEmployeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "downloadType": "INLINE"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.downloadUrl").value(containsString("/api/employee/files/download/")))
            .andReturn();

        String ticketNo = data(downloadTicketResult).get("ticketNo").asText();
        mockMvc.perform(get("/api/employee/files/download/{ticketNo}", ticketNo)
                .cookie(otherEmployeeSession))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(content));
    }

    @Test
    @DisplayName("员工应能完成 LOCAL 上传票据、确认上传、秒传与下载票据闭环")
    void employeeShouldUploadConfirmDeduplicateAndCreateDownloadTicket() throws Exception {
        Cookie employeeSession = loginEmployee();
        seedDefaultQuotaPolicy();

        byte[] content = "module-file-same-content".getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256(content);

        MvcResult ticketResult = createUploadTicket(employeeSession, "请假证明.pdf", content.length, sha256)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.deduplicated").value(false))
            .andExpect(jsonPath("$.data.storageProvider").value("LOCAL"))
            .andExpect(jsonPath("$.data.uploadTicketId").isNumber())
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
                      "displayName": "请假证明.pdf"
                    }
                    """.formatted(uploadTicketId, objectKey, sha256, content.length)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.deduplicated").value(false))
            .andExpect(jsonPath("$.data.attachmentRefId").isNumber())
            .andExpect(jsonPath("$.data.fileObjectId").isNumber())
            .andReturn();

        Long firstAttachmentRefId = data(confirmResult).get("attachmentRefId").asLong();
        Long fileObjectId = data(confirmResult).get("fileObjectId").asLong();

        mockMvc.perform(post("/api/employee/files/confirm-upload")
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
                      "displayName": "请假证明.pdf"
                    }
                    """.formatted(uploadTicketId, objectKey, sha256, content.length)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.attachmentRefId").value(firstAttachmentRefId));

        createUploadTicket(employeeSession, "病假材料.pdf", content.length, sha256)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.deduplicated").value(true))
            .andExpect(jsonPath("$.data.attachmentRefId").isNumber());

        Integer fileObjectCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_file_object WHERE content_sha256 = ? AND file_size = ?",
            Integer.class,
            sha256,
            content.length
        );
        assertEquals(1, fileObjectCount);

        Integer attachmentCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_attachment_ref WHERE file_object_id = ?",
            Integer.class,
            fileObjectId
        );
        assertEquals(2, attachmentCount);

        mockMvc.perform(post("/api/employee/files/{attachmentRefId}/download-tickets", firstAttachmentRefId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "downloadType": "SINGLE",
                      "reason": null
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.downloadTicketId").isNumber())
            .andExpect(jsonPath("$.data.downloadUrl").value(containsString("/api/employee/files/download/")))
            .andExpect(jsonPath("$.data.fileName").value("请假证明.pdf"));

        assertAuditCountAtLeast("FILE_UPLOAD_TICKET_CREATED", 1);
        assertAuditCountAtLeast("FILE_UPLOAD_CONFIRMED", 1);
        assertAuditCountAtLeast("FILE_UPLOAD_DEDUP", 1);
        assertAuditCountAtLeast("FILE_DOWNLOAD_TICKET_CREATED", 1);
    }

    @Test
    @DisplayName("员工使用有效下载票据应能下载原始文件并标记票据已使用")
    void employeeShouldDownloadFileByActiveTicket() throws Exception {
        Cookie employeeSession = loginEmployee();
        seedDefaultQuotaPolicy();
        byte[] content = "download-ticket-content".getBytes(StandardCharsets.UTF_8);
        Long attachmentRefId = createConfirmedPersonalFile(employeeSession, "下载验证.txt", content);

        MvcResult ticketResult = mockMvc.perform(post("/api/employee/files/{attachmentRefId}/download-tickets", attachmentRefId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "downloadType": "SINGLE",
                      "reason": "下载验证"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();

        String ticketNo = data(ticketResult).get("ticketNo").asText();
        mockMvc.perform(get("/api/employee/files/download/{ticketNo}", ticketNo)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(content));

        String status = jdbcTemplate.queryForObject(
            "SELECT status FROM sys_download_ticket WHERE ticket_no = ?",
            String.class,
            ticketNo
        );
        assertEquals("USED", status);
        assertAuditCountAtLeast("FILE_DOWNLOAD_EXECUTED", 1);
    }

    @Test
    @DisplayName("员工端同一下载票据并发执行时只能成功一次")
    void employeeDownloadTicketShouldBeClaimedOnceUnderConcurrency() throws Exception {
        Cookie employeeSession = loginEmployee();
        seedDefaultQuotaPolicy();
        byte[] content = "employee-concurrent-download-content".getBytes(StandardCharsets.UTF_8);
        Long attachmentRefId = createConfirmedPersonalFile(employeeSession, "员工并发下载.txt", content);
        String ticketNo = createEmployeeDownloadTicket(employeeSession, attachmentRefId);
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
                    Future<Integer> first = executor.submit(() -> downloadCode(employeeSession, ticketNo, ready, start));
                    Future<Integer> second = executor.submit(() -> downloadCode(employeeSession, ticketNo, ready, start));
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
    @DisplayName("过期下载票据不能下载文件")
    void expiredDownloadTicketShouldBeRejected() throws Exception {
        Cookie employeeSession = loginEmployee();
        seedDefaultQuotaPolicy();
        Long attachmentRefId = createConfirmedPersonalFile(employeeSession, "过期下载.txt", "expired-download-content");

        MvcResult ticketResult = mockMvc.perform(post("/api/employee/files/{attachmentRefId}/download-tickets", attachmentRefId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "downloadType": "SINGLE",
                      "reason": "过期验证"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();

        String ticketNo = data(ticketResult).get("ticketNo").asText();
        jdbcTemplate.update("""
            UPDATE sys_download_ticket
            SET expire_at = DATE_SUB(NOW(), INTERVAL 1 MINUTE)
            WHERE ticket_no = ?
            """, ticketNo);

        mockMvc.perform(get("/api/employee/files/download/{ticketNo}", ticketNo)
                .cookie(employeeSession))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value(100410))
            .andExpect(jsonPath("$.message", containsString("下载链接已过期")));

        assertEquals("EXPIRED", downloadTicketStatus(ticketNo));
    }

    @Test
    @DisplayName("员工文件应支持回收站删除、恢复和彻底删除引用")
    void employeeShouldTrashRestoreAndPurgeAttachmentReference() throws Exception {
        Cookie employeeSession = loginEmployee();
        seedDefaultQuotaPolicy();
        String content = "trash-content";
        Long attachmentRefId = createConfirmedPersonalFile(employeeSession, "回收站文件.txt", content);

        mockMvc.perform(post("/api/employee/files/{attachmentRefId}/trash", attachmentRefId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "个人清理文件"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.attachmentRefId").value(attachmentRefId))
            .andExpect(jsonPath("$.data.deletedAt").isString())
            .andExpect(jsonPath("$.data.purgeAfter").isString());

        String deletedAt = jdbcTemplate.queryForObject(
            "SELECT deleted_at FROM sys_attachment_ref WHERE id = ?",
            String.class,
            attachmentRefId
        );
        assertTrue(deletedAt != null && !deletedAt.isBlank());

        mockMvc.perform(post("/api/employee/files/{attachmentRefId}/restore", attachmentRefId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "误删恢复"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.attachmentRefId").value(attachmentRefId))
            .andExpect(jsonPath("$.data.restoredAt").isString());

        Integer restoredDeletedCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_attachment_ref WHERE id = ? AND deleted_at IS NULL",
            Integer.class,
            attachmentRefId
        );
        assertEquals(1, restoredDeletedCount);

        mockMvc.perform(post("/api/employee/files/{attachmentRefId}/trash", attachmentRefId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "再次删除"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(delete("/api/employee/files/{attachmentRefId}", attachmentRefId)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.physicalCleanupScheduled").value(true));

        Integer activeReferenceCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_attachment_ref WHERE id = ? AND is_deleted = 0",
            Integer.class,
            attachmentRefId
        );
        assertEquals(0, activeReferenceCount);
        assertEquals(0L, usedBytes("USER", EMPLOYEE_USER_ID));
        assertEquals(0L, usedBytes("GLOBAL", null));
        assertEquals(0L, fileCount("USER", EMPLOYEE_USER_ID));
        assertEquals(0L, fileCount("GLOBAL", null));
        assertAuditCountAtLeast("FILE_TRASHED", 2);
        assertAuditCountAtLeast("FILE_RESTORED", 1);
        assertAuditCountAtLeast("FILE_PURGED", 1);
    }

    @Test
    @DisplayName("员工应能分页查询自己的个人文件列表和回收站列表")
    void employeeShouldListOwnPersonalFilesAndRecycleBin() throws Exception {
        Cookie employeeSession = loginEmployee();
        seedDefaultQuotaPolicy();
        Long activeAttachmentRefId = createConfirmedPersonalFile(employeeSession, "个人列表-有效.txt", "employee-active-file");
        Long trashedAttachmentRefId = createConfirmedPersonalFile(employeeSession, "个人列表-回收站.txt", "employee-trashed-file");
        Long workflowAttachmentRefId = cloneWorkflowAttachmentRef(activeAttachmentRefId);
        Long otherUserAttachmentRefId = cloneOtherUserPersonalAttachmentRef(activeAttachmentRefId);

        trashEmployeeFile(employeeSession, trashedAttachmentRefId);

        mockMvc.perform(get("/api/employee/files")
                .cookie(employeeSession)
                .param("pageNo", "1")
                .param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.list[0].attachmentRefId").value(activeAttachmentRefId))
            .andExpect(jsonPath("$.data.list[0].displayName").value("个人列表-有效.txt"))
            .andExpect(jsonPath("$.data.list[0].ownerType").value("PERSONAL_FILE"))
            .andExpect(jsonPath("$.data.list[0].uploaderType").value("USER"))
            .andExpect(jsonPath("$.data.list[0].uploaderId").value(EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data.list[0].mimeType").value("text/plain"))
            .andExpect(jsonPath("$.data.list[0].fileSize").value("employee-active-file".getBytes(StandardCharsets.UTF_8).length))
            .andExpect(jsonPath("$.data.list[0].scanStatus").value("PENDING"))
            .andExpect(jsonPath("$.data.list[0].deletedAt").doesNotExist());

        mockMvc.perform(get("/api/employee/files/recycle-bin")
                .cookie(employeeSession)
                .param("pageNo", "1")
                .param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.list[0].attachmentRefId").value(trashedAttachmentRefId))
            .andExpect(jsonPath("$.data.list[0].displayName").value("个人列表-回收站.txt"))
            .andExpect(jsonPath("$.data.list[0].deletedAt").isString())
            .andExpect(jsonPath("$.data.list[0].purgeAfter").isString());

        assertEquals(1, personalFileListVisibleCount(activeAttachmentRefId));
        assertEquals(1, personalFileListHiddenCount(workflowAttachmentRefId));
        assertEquals(1, personalFileListHiddenCount(otherUserAttachmentRefId));
    }

    @Test
    @DisplayName("员工个人文件生命周期接口不能操作审批归档附件")
    void employeeLifecycleShouldRejectWorkflowAttachmentRef() throws Exception {
        Cookie employeeSession = loginEmployee();
        seedDefaultQuotaPolicy();
        Long personalAttachmentRefId = createConfirmedPersonalFile(
            employeeSession,
            "审批归档源文件.txt",
            "workflow-archive-source"
        );
        Long workflowAttachmentRefId = cloneWorkflowAttachmentRef(personalAttachmentRefId);

        mockMvc.perform(post("/api/employee/files/{attachmentRefId}/trash", workflowAttachmentRefId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "申请人不能删除审批归档附件"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403))
            .andExpect(jsonPath("$.message", containsString("个人文件接口仅允许操作个人文件")));

        jdbcTemplate.update("""
            UPDATE sys_attachment_ref
            SET deleted_at = NOW(), deleted_by = ?, purge_after = DATE_ADD(NOW(), INTERVAL 30 DAY)
            WHERE id = ?
            """, EMPLOYEE_USER_ID, workflowAttachmentRefId);

        mockMvc.perform(post("/api/employee/files/{attachmentRefId}/restore", workflowAttachmentRefId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "申请人不能恢复审批归档附件"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403))
            .andExpect(jsonPath("$.message", containsString("个人文件接口仅允许操作个人文件")));

        mockMvc.perform(delete("/api/employee/files/{attachmentRefId}", workflowAttachmentRefId)
                .cookie(employeeSession))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403))
            .andExpect(jsonPath("$.message", containsString("个人文件接口仅允许操作个人文件")));

        Integer activeWorkflowRefCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_attachment_ref
            WHERE id = ?
              AND owner_type = 'WF_REQUEST'
              AND is_deleted = 0
            """, Integer.class, workflowAttachmentRefId);
        assertEquals(1, activeWorkflowRefCount);
        assertEquals("workflow-archive-source".getBytes(StandardCharsets.UTF_8).length, usedBytes("USER", EMPLOYEE_USER_ID));
        assertEquals("workflow-archive-source".getBytes(StandardCharsets.UTF_8).length, usedBytes("GLOBAL", null));
    }

    @Test
    @DisplayName("恢复回收站文件时企业总池不足也应被拦截")
    void restoreShouldRejectWhenGlobalQuotaIsInsufficient() throws Exception {
        Cookie employeeSession = loginEmployee();
        seedDefaultQuotaPolicy();
        Long attachmentRefId = createConfirmedPersonalFile(employeeSession, "企业空间恢复拦截.txt", "global-quota-content");

        mockMvc.perform(post("/api/employee/files/{attachmentRefId}/trash", attachmentRefId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "先移入回收站"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        jdbcTemplate.update("""
            UPDATE sys_storage_quota_policy
            SET quota_bytes = 1, updated_at = DATE_ADD(NOW(), INTERVAL 1 SECOND)
            WHERE policy_code = 'GLOBAL_400G'
            """);

        mockMvc.perform(post("/api/employee/files/{attachmentRefId}/restore", attachmentRefId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "恢复时企业空间已不足"
                    }
                    """))
            .andExpect(status().is(507))
            .andExpect(jsonPath("$.code").value(100507))
            .andExpect(jsonPath("$.message", containsString("企业文件总空间不足")));
    }

    @Test
    @DisplayName("彻底删除已删除引用不能重复释放空间")
    void purgeShouldNotReleaseUsageTwice() throws Exception {
        Cookie employeeSession = loginEmployee();
        seedDefaultQuotaPolicy();
        Long firstAttachmentRefId = createConfirmedPersonalFile(employeeSession, "重复删除-1.txt", "first-content");
        createConfirmedPersonalFile(employeeSession, "重复删除-2.txt", "second-content");
        long remainingBytes = "second-content".getBytes(StandardCharsets.UTF_8).length;

        mockMvc.perform(post("/api/employee/files/{attachmentRefId}/trash", firstAttachmentRefId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "先删除第一份"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(delete("/api/employee/files/{attachmentRefId}", firstAttachmentRefId)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(delete("/api/employee/files/{attachmentRefId}", firstAttachmentRefId)
                .cookie(employeeSession))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(100404))
            .andExpect(jsonPath("$.message", containsString("附件引用不存在")));

        assertEquals(remainingBytes, usedBytes("USER", EMPLOYEE_USER_ID));
        assertEquals(remainingBytes, usedBytes("GLOBAL", null));
        assertEquals(1L, fileCount("USER", EMPLOYEE_USER_ID));
        assertEquals(1L, fileCount("GLOBAL", null));
    }

    @Test
    @DisplayName("员工端并发彻底删除同一附件时不应重复释放用量")
    void employeePurgeShouldReleaseUsageOnlyWhenStateChangedUnderConcurrency() throws Exception {
        Cookie employeeSession = loginEmployee();
        seedDefaultQuotaPolicy();
        byte[] targetContent = "employee-purge-idempotent-target".getBytes(StandardCharsets.UTF_8);
        byte[] survivorContent = "employee-purge-idempotent-survivor".getBytes(StandardCharsets.UTF_8);
        Long attachmentRefId = createConfirmedPersonalFile(employeeSession, "员工重复彻底删除.txt", targetContent);
        createConfirmedPersonalFile(employeeSession, "员工账本哨兵.txt", survivorContent);

        trashEmployeeFile(employeeSession, attachmentRefId);

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
                    Future<Integer> first = executor.submit(() -> purgeCode(employeeSession, attachmentRefId, ready, start));
                    Future<Integer> second = executor.submit(() -> purgeCode(employeeSession, attachmentRefId, ready, start));
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

        assertEquals(survivorContent.length, usedBytes("USER", EMPLOYEE_USER_ID));
        assertEquals(survivorContent.length, usedBytes("GLOBAL", null));
        assertEquals(1L, fileCount("USER", EMPLOYEE_USER_ID));
        assertEquals(1L, fileCount("GLOBAL", null));
    }


    @Test
    @DisplayName("上传票据重复确认即使读到待确认状态也只能生成一条附件引用")
    void confirmUploadShouldBeIdempotentWhenTicketStatusIsStale() throws Exception {
        Cookie employeeSession = loginEmployee();
        seedDefaultQuotaPolicy();
        byte[] content = "stale-confirm-content".getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256(content);

        MvcResult ticketResult = createUploadTicket(employeeSession, "并发确认.txt", content.length, sha256)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        JsonNode ticketData = data(ticketResult);
        Long uploadTicketId = ticketData.get("uploadTicketId").asLong();
        String objectKey = ticketData.get("objectKey").asText();
        writeLocalObject(objectKey, content);

        Long firstAttachmentRefId = data(confirmUpload(employeeSession, uploadTicketId, objectKey, sha256, content.length, "并发确认.txt")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn()).get("attachmentRefId").asLong();

        jdbcTemplate.update("""
            UPDATE sys_upload_ticket
            SET status = 'PENDING', confirmed_at = NULL
            WHERE id = ?
            """, uploadTicketId);

        confirmUpload(employeeSession, uploadTicketId, objectKey, sha256, content.length, "并发确认.txt")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.attachmentRefId").value(firstAttachmentRefId));

        Integer attachmentCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_attachment_ref WHERE source_upload_ticket_id = ? AND is_deleted = 0",
            Integer.class,
            uploadTicketId
        );
        assertEquals(1, attachmentCount);
        assertEquals(content.length, usedBytes("USER", EMPLOYEE_USER_ID));
        assertEquals(content.length, usedBytes("GLOBAL", null));
        assertEquals(1L, fileCount("USER", EMPLOYEE_USER_ID));
        assertEquals(1L, fileCount("GLOBAL", null));

        String ticketStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM sys_upload_ticket WHERE id = ?",
            String.class,
            uploadTicketId
        );
        assertEquals("CONFIRMED", ticketStatus);
    }

    @Test
    @DisplayName("个人空间不足时应在申请上传票据阶段明确拦截")
    void uploadTicketShouldRejectWhenPersonalQuotaIsInsufficient() throws Exception {
        Cookie employeeSession = loginEmployee();
        seedQuotaPolicy("USER_DEFAULT_1B", "USER_DEFAULT", null, 1L);

        byte[] content = "too-large".getBytes(StandardCharsets.UTF_8);

        createUploadTicket(employeeSession, "空间不足.txt", content.length, sha256(content))
            .andExpect(status().is(507))
            .andExpect(jsonPath("$.code").value(100507))
            .andExpect(jsonPath("$.message", containsString("个人云空间不足")));
    }

    @Test
    @DisplayName("申请上传票据时应按场景策略拦截不支持的文件类型")
    void uploadTicketShouldRejectUnsupportedFileType() throws Exception {
        Cookie employeeSession = loginEmployee();
        seedDefaultQuotaPolicy();
        byte[] content = "unsafe-executable-content".getBytes(StandardCharsets.UTF_8);

        createUploadTicket(employeeSession, "木马.exe", "application/x-msdownload", content.length, sha256(content))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.code").value(100415))
            .andExpect(jsonPath("$.message", containsString("当前场景不支持该文件类型")));
    }

    @Test
    @DisplayName("申请上传票据时应按场景策略拦截超过大小上限的文件")
    void uploadTicketShouldRejectWhenFileExceedsSceneLimit() throws Exception {
        Cookie employeeSession = loginEmployee();
        seedDefaultQuotaPolicy();
        long fileSize = 101L * 1024L * 1024L;
        byte[] content = "oversized-content-fingerprint".getBytes(StandardCharsets.UTF_8);

        createUploadTicket(employeeSession, "超大归档.zip", "application/zip", fileSize, sha256(content))
            .andExpect(status().is(413))
            .andExpect(jsonPath("$.code").value(100413))
            .andExpect(jsonPath("$.message", containsString("文件超过当前场景大小限制")));
    }

    @Test
    @DisplayName("同一客户端请求ID重复申请上传票据应返回同一张票据")
    void uploadTicketShouldBeIdempotentByClientRequestId() throws Exception {
        Cookie employeeSession = loginEmployee();
        seedDefaultQuotaPolicy();
        byte[] content = "idempotent-upload-ticket".getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256(content);
        String clientRequestId = "same-upload-request";

        MvcResult firstResult = createUploadTicket(employeeSession, "幂等上传.txt", content.length, sha256, clientRequestId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();

        MvcResult secondResult = createUploadTicket(employeeSession, "幂等上传.txt", content.length, sha256, clientRequestId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();

        assertEquals(data(firstResult).get("uploadTicketId").asLong(), data(secondResult).get("uploadTicketId").asLong());
        assertEquals(data(firstResult).get("objectKey").asText(), data(secondResult).get("objectKey").asText());

        Integer ticketCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_upload_ticket WHERE client_request_id = ?",
            Integer.class,
            clientRequestId
        );
        assertEquals(1, ticketCount);
    }

    @Test
    @DisplayName("已签发上传票据在策略变化后重复申请仍应返回原票据")
    void uploadTicketShouldReturnExistingTicketWhenPolicyChangesAfterFirstRequest() throws Exception {
        Cookie employeeSession = loginEmployee();
        seedDefaultQuotaPolicy();
        byte[] content = "policy-change-idempotent-ticket".getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256(content);
        String clientRequestId = "policy-change-same-upload-request";

        MvcResult firstResult = createUploadTicket(employeeSession, "策略变化重试.txt", content.length, sha256, clientRequestId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        long firstUploadTicketId = data(firstResult).get("uploadTicketId").asLong();

        jdbcTemplate.update("""
            UPDATE sys_file_upload_policy
            SET status = 'DISABLED', updated_at = NOW()
            WHERE policy_code = 'PERSONAL_FILE_DEFAULT_100M'
            """);

        try {
            MvcResult retryResult = createUploadTicket(employeeSession, "策略变化重试.txt", content.length, sha256, clientRequestId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

            assertEquals(firstUploadTicketId, data(retryResult).get("uploadTicketId").asLong());
        } finally {
            jdbcTemplate.update("""
                UPDATE sys_file_upload_policy
                SET status = 'ENABLED', updated_at = NOW()
                WHERE policy_code = 'PERSONAL_FILE_DEFAULT_100M'
                """);
        }
    }

    @Test
    @DisplayName("确认上传时不能篡改上传票据中的业务归属")
    void confirmUploadShouldRejectWhenOwnerDoesNotMatchTicket() throws Exception {
        Cookie employeeSession = loginEmployee();
        seedDefaultQuotaPolicy();
        byte[] content = "owner-mismatch-content".getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256(content);

        MvcResult ticketResult = createUploadTicket(employeeSession, "归属校验.txt", content.length, sha256)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();

        JsonNode ticketData = data(ticketResult);
        Long uploadTicketId = ticketData.get("uploadTicketId").asLong();
        String objectKey = ticketData.get("objectKey").asText();
        writeLocalObject(objectKey, content);

        mockMvc.perform(post("/api/employee/files/confirm-upload")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "uploadTicketId": %s,
                      "objectKey": "%s",
                      "contentSha256": "%s",
                      "fileSize": %s,
                      "ownerType": "WF_REQUEST",
                      "ownerId": 90001,
                      "displayName": "归属校验.txt"
                    }
                    """.formatted(uploadTicketId, objectKey, sha256, content.length)))
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.code").value(100422))
            .andExpect(jsonPath("$.message", containsString("业务归属不匹配")));
    }

    @Test
    @DisplayName("员工和后台应能查询文件配额与存储摘要")
    void userAndAdminShouldQueryQuotaAndStorageSummary() throws Exception {
        Cookie employeeSession = loginEmployee();
        Cookie adminSession = loginAdmin();
        seedDefaultQuotaPolicy();
        createConfirmedPersonalFile(employeeSession, "空间统计.txt", "quota-content");

        mockMvc.perform(get("/api/employee/files/quota")
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.personalQuotaBytes").value(10737418240L))
            .andExpect(jsonPath("$.data.personalUsedBytes").value("quota-content".getBytes(StandardCharsets.UTF_8).length))
            .andExpect(jsonPath("$.data.orgPoolQuotaBytes").value(429496729600L))
            .andExpect(jsonPath("$.data.uploadWritable").value(true));

        mockMvc.perform(get("/api/admin/files/storage-summary")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.orgPoolQuotaBytes").value(429496729600L))
            .andExpect(jsonPath("$.data.orgPoolUsedBytes").value("quota-content".getBytes(StandardCharsets.UTF_8).length))
            .andExpect(jsonPath("$.data.fileObjectCount").value(1))
            .andExpect(jsonPath("$.data.attachmentRefCount").value(1));
    }

    @Test
    @DisplayName("后台应能创建、查询和更新配额策略")
    void adminShouldCreateListAndUpdateQuotaPolicy() throws Exception {
        Cookie adminSession = loginAdmin();

        MvcResult createResult = mockMvc.perform(post("/api/admin/files/quota-policies")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "policyCode": "USER_DEFAULT_20G",
                      "policyName": "用户默认20G",
                      "scopeType": "USER_DEFAULT",
                      "scopeId": null,
                      "quotaBytes": 21474836480,
                      "cacheQuotaBytes": null,
                      "warningThreshold": 0.8000,
                      "blockThreshold": 1.0000,
                      "status": "ENABLED"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.policyId").isNumber())
            .andExpect(jsonPath("$.data.policyCode").value("USER_DEFAULT_20G"))
            .andExpect(jsonPath("$.data.quotaBytes").value(21474836480L))
            .andReturn();

        Long policyId = data(createResult).get("policyId").asLong();

        mockMvc.perform(get("/api/admin/files/quota-policies")
                .cookie(adminSession)
                .param("scopeType", "USER_DEFAULT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].policyCode").value("USER_DEFAULT_20G"));

        mockMvc.perform(put("/api/admin/files/quota-policies/{policyId}", policyId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "policyName": "用户默认30G",
                      "quotaBytes": 32212254720,
                      "cacheQuotaBytes": 1073741824,
                      "warningThreshold": 0.7500,
                      "blockThreshold": 0.9500,
                      "status": "ENABLED"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.policyName").value("用户默认30G"))
            .andExpect(jsonPath("$.data.quotaBytes").value(32212254720L))
            .andExpect(jsonPath("$.data.cacheQuotaBytes").value(1073741824L))
            .andExpect(jsonPath("$.data.warningThreshold").value(0.7500))
            .andExpect(jsonPath("$.data.blockThreshold").value(0.9500));
    }

    @Test
    @DisplayName("后台配额策略应校验阈值和当前已用量")
    void adminQuotaPolicyShouldValidateThresholdAndUsedBytes() throws Exception {
        Cookie employeeSession = loginEmployee();
        Cookie adminSession = loginAdmin();
        seedDefaultQuotaPolicy();
        createConfirmedPersonalFile(employeeSession, "已用量校验.txt", "quota-used-content");

        mockMvc.perform(post("/api/admin/files/quota-policies")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "policyCode": "INVALID_THRESHOLD",
                      "policyName": "非法阈值",
                      "scopeType": "USER",
                      "scopeId": 2003,
                      "quotaBytes": 1073741824,
                      "cacheQuotaBytes": null,
                      "warningThreshold": 0.9900,
                      "blockThreshold": 0.8000,
                      "status": "ENABLED"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(100400))
            .andExpect(jsonPath("$.message", containsString("预警阈值必须小于阻断阈值")));

        mockMvc.perform(post("/api/admin/files/quota-policies")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "policyCode": "USER_2003_TOO_SMALL",
                      "policyName": "低于已用量",
                      "scopeType": "USER",
                      "scopeId": 2003,
                      "quotaBytes": 1,
                      "cacheQuotaBytes": null,
                      "warningThreshold": 0.8000,
                      "blockThreshold": 1.0000,
                      "status": "ENABLED"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(100409))
            .andExpect(jsonPath("$.message", containsString("配额不能低于当前已用量")));
    }

    @Test
    @DisplayName("后台不能为同一作用域重复创建启用配额策略")
    void adminQuotaPolicyShouldRejectDuplicateScope() throws Exception {
        Cookie adminSession = loginAdmin();

        mockMvc.perform(post("/api/admin/files/quota-policies")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "policyCode": "USER_DEFAULT_20G",
                      "policyName": "用户默认20G",
                      "scopeType": "USER_DEFAULT",
                      "scopeId": null,
                      "quotaBytes": 21474836480,
                      "cacheQuotaBytes": null,
                      "warningThreshold": 0.8000,
                      "blockThreshold": 1.0000,
                      "status": "ENABLED"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/admin/files/quota-policies")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "policyCode": "USER_DEFAULT_30G",
                      "policyName": "用户默认30G",
                      "scopeType": "USER_DEFAULT",
                      "scopeId": null,
                      "quotaBytes": 32212254720,
                      "cacheQuotaBytes": null,
                      "warningThreshold": 0.8000,
                      "blockThreshold": 1.0000,
                      "status": "ENABLED"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(100409))
            .andExpect(jsonPath("$.message", containsString("同一作用域已存在配额策略")));
    }

    @Test
    @DisplayName("数据库应约束空作用域配额策略不能重复")
    void databaseShouldRejectDuplicateNullScopeQuotaPolicy() {
        insertQuotaPolicy("GLOBAL_A", "GLOBAL", null, 1024L);

        assertThrows(DuplicateKeyException.class,
            () -> insertQuotaPolicy("GLOBAL_B", "GLOBAL", null, 2048L));
    }

    @Test
    @DisplayName("数据库应约束空作用域用量账本不能重复")
    void databaseShouldRejectDuplicateNullScopeUsage() {
        insertUsage("GLOBAL", null, 1024L, 1);

        assertThrows(DuplicateKeyException.class,
            () -> insertUsage("GLOBAL", null, 2048L, 1));
    }

    @Test
    @DisplayName("集成测试重置应清理审计日志避免历史数据误报")
    void integrationTestResetShouldClearAuditLog() throws Exception {
        jdbcTemplate.update("""
            INSERT INTO sys_audit_log
                (id, operator_type, operator_id, action_type, target_type, target_id, result, detail_json)
            VALUES
                (?, 'SYSTEM', NULL, 'TEST_AUDIT_RESET', 'TEST', 1, 'SUCCESS', CAST('{}' AS JSON))
            """, System.nanoTime());

        java.lang.reflect.Method seedDatabase = LocalIntegrationTestSupport.class.getDeclaredMethod("seedDatabase");
        seedDatabase.setAccessible(true);
        seedDatabase.invoke(this);

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_audit_log WHERE action_type = 'TEST_AUDIT_RESET'",
            Integer.class
        );
        assertEquals(0, count);
    }

    @Test
    @DisplayName("后台不能重复创建相同编码的配额策略")
    void adminQuotaPolicyShouldRejectDuplicatePolicyCode() throws Exception {
        Cookie adminSession = loginAdmin();

        mockMvc.perform(post("/api/admin/files/quota-policies")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "policyCode": "DUPLICATE_CODE",
                      "policyName": "重复编码-用户默认",
                      "scopeType": "USER_DEFAULT",
                      "scopeId": null,
                      "quotaBytes": 21474836480,
                      "cacheQuotaBytes": null,
                      "warningThreshold": 0.8000,
                      "blockThreshold": 1.0000,
                      "status": "ENABLED"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/admin/files/quota-policies")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "policyCode": "DUPLICATE_CODE",
                      "policyName": "重复编码-单人覆盖",
                      "scopeType": "USER",
                      "scopeId": 2003,
                      "quotaBytes": 32212254720,
                      "cacheQuotaBytes": null,
                      "warningThreshold": 0.8000,
                      "blockThreshold": 1.0000,
                      "status": "ENABLED"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(100409))
            .andExpect(jsonPath("$.message", containsString("配额策略编码已存在")));
    }

    private org.springframework.test.web.servlet.ResultActions createUploadTicket(
            Cookie session,
            String fileName,
            long fileSize,
            String sha256) throws Exception {
        return createUploadTicket(session, fileName, fileSize, sha256, "file-test-" + System.nanoTime());
    }

    private org.springframework.test.web.servlet.ResultActions createUploadTicket(
            Cookie session,
            String fileName,
            long fileSize,
            String sha256,
            String clientRequestId) throws Exception {
        return createUploadTicket(session, fileName, "text/plain", fileSize, sha256, clientRequestId);
    }

    private org.springframework.test.web.servlet.ResultActions createUploadTicket(
            Cookie session,
            String fileName,
            String mimeType,
            long fileSize,
            String sha256) throws Exception {
        return createUploadTicket(session, fileName, mimeType, fileSize, sha256, "file-test-" + System.nanoTime());
    }

    private org.springframework.test.web.servlet.ResultActions createUploadTicket(
            Cookie session,
            String fileName,
            String mimeType,
            long fileSize,
            String sha256,
            String clientRequestId) throws Exception {
        return createUploadTicket(session, fileName, mimeType, fileSize, sha256, clientRequestId, "PERSONAL_FILE");
    }

    private org.springframework.test.web.servlet.ResultActions createUploadTicket(
            Cookie session,
            String fileName,
            String mimeType,
            long fileSize,
            String sha256,
            String clientRequestId,
            String ownerType) throws Exception {
        return mockMvc.perform(post("/api/employee/files/upload-tickets")
            .cookie(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "ownerType": "%s",
                  "ownerId": null,
                  "fileName": "%s",
                  "mimeType": "%s",
                  "fileSize": %s,
                  "contentSha256": "%s",
                  "contentMd5": null,
                  "clientRequestId": "%s"
                }
                """.formatted(ownerType, fileName, mimeType, fileSize, sha256, clientRequestId)));
    }

    private Long createConfirmedPersonalFile(Cookie employeeSession, String fileName, String text) throws Exception {
        return createConfirmedPersonalFile(employeeSession, fileName, text.getBytes(StandardCharsets.UTF_8));
    }

    private Long createConfirmedPersonalFile(Cookie employeeSession, String fileName, byte[] content) throws Exception {
        String sha256 = sha256(content);
        MvcResult ticketResult = createUploadTicket(employeeSession, fileName, content.length, sha256)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        JsonNode ticketData = data(ticketResult);
        Long uploadTicketId = ticketData.get("uploadTicketId").asLong();
        String objectKey = ticketData.get("objectKey").asText();
        writeLocalObject(objectKey, content);

        MvcResult confirmResult = confirmUpload(employeeSession, uploadTicketId, objectKey, sha256, content.length, fileName)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return data(confirmResult).get("attachmentRefId").asLong();
    }

    private Long cloneWorkflowAttachmentRef(Long sourceAttachmentRefId) {
        Long fileObjectId = jdbcTemplate.queryForObject(
            "SELECT file_object_id FROM sys_attachment_ref WHERE id = ?",
            Long.class,
            sourceAttachmentRefId
        );
        Long workflowAttachmentRefId = System.nanoTime();
        jdbcTemplate.update("""
            INSERT INTO sys_attachment_ref
                (id, owner_type, owner_id, file_object_id, original_file_name,
                 display_name, uploader_type, uploader_id, visibility_scope, created_by)
            VALUES
                (?, 'WF_REQUEST', 910001, ?, '审批归档源文件.txt',
                 '审批归档源文件.txt', 'USER', ?, 'WORKFLOW_PARTICIPANT', ?)
            """,
            workflowAttachmentRefId,
            fileObjectId,
            EMPLOYEE_USER_ID,
            EMPLOYEE_USER_ID
        );
        return workflowAttachmentRefId;
    }

    private Long cloneOtherUserPersonalAttachmentRef(Long sourceAttachmentRefId) {
        Long fileObjectId = jdbcTemplate.queryForObject(
            "SELECT file_object_id FROM sys_attachment_ref WHERE id = ?",
            Long.class,
            sourceAttachmentRefId
        );
        Long otherUserAttachmentRefId = System.nanoTime();
        jdbcTemplate.update("""
            INSERT INTO sys_attachment_ref
                (id, owner_type, owner_id, file_object_id, original_file_name,
                 display_name, uploader_type, uploader_id, visibility_scope, created_by)
            VALUES
                (?, 'PERSONAL_FILE', NULL, ?, '其他员工个人文件.txt',
                 '其他员工个人文件.txt', 'USER', 2004, 'OWNER', 2004)
            """,
            otherUserAttachmentRefId,
            fileObjectId
        );
        return otherUserAttachmentRefId;
    }

    private int personalFileListVisibleCount(Long attachmentRefId) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_attachment_ref
            WHERE id = ?
              AND owner_type = 'PERSONAL_FILE'
              AND uploader_type = 'USER'
              AND uploader_id = ?
              AND deleted_at IS NULL
              AND is_deleted = 0
            """, Integer.class, attachmentRefId, EMPLOYEE_USER_ID);
        return count == null ? 0 : count;
    }

    private int personalFileListHiddenCount(Long attachmentRefId) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_attachment_ref
            WHERE id = ?
              AND (
                owner_type <> 'PERSONAL_FILE'
                OR uploader_type <> 'USER'
                OR uploader_id <> ?
              )
              AND is_deleted = 0
            """, Integer.class, attachmentRefId, EMPLOYEE_USER_ID);
        return count == null ? 0 : count;
    }

    private String createEmployeeDownloadTicket(Cookie employeeSession, Long attachmentRefId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/employee/files/{attachmentRefId}/download-tickets", attachmentRefId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "downloadType": "SINGLE",
                      "reason": "员工下载票据测试"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return data(result).get("ticketNo").asText();
    }

    private void trashEmployeeFile(Cookie employeeSession, Long attachmentRefId) throws Exception {
        mockMvc.perform(post("/api/employee/files/{attachmentRefId}/trash", attachmentRefId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "员工测试移入回收站"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    private int downloadCode(Cookie employeeSession,
                             String ticketNo,
                             CountDownLatch ready,
                             CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        MvcResult result = mockMvc.perform(get("/api/employee/files/download/{ticketNo}", ticketNo)
                .cookie(employeeSession))
            .andReturn();
        String contentType = result.getResponse().getContentType();
        if (contentType != null && contentType.contains(MediaType.APPLICATION_JSON_VALUE)) {
            return objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asInt();
        }
        return 0;
    }

    private int purgeCode(Cookie employeeSession,
                          Long attachmentRefId,
                          CountDownLatch ready,
                          CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        MvcResult result = mockMvc.perform(delete("/api/employee/files/{attachmentRefId}", attachmentRefId)
                .cookie(employeeSession))
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

    private org.springframework.test.web.servlet.ResultActions confirmUpload(
            Cookie employeeSession,
            Long uploadTicketId,
            String objectKey,
            String sha256,
            long fileSize,
            String displayName) throws Exception {
        return confirmUpload(employeeSession, uploadTicketId, objectKey, sha256, fileSize, displayName, "PERSONAL_FILE");
    }

    private org.springframework.test.web.servlet.ResultActions confirmUpload(
            Cookie employeeSession,
            Long uploadTicketId,
            String objectKey,
            String sha256,
            long fileSize,
            String displayName,
            String ownerType) throws Exception {
        return mockMvc.perform(post("/api/employee/files/confirm-upload")
            .cookie(employeeSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "uploadTicketId": %s,
                  "objectKey": "%s",
                  "contentSha256": "%s",
                  "fileSize": %s,
                  "ownerType": "%s",
                  "ownerId": null,
                  "displayName": "%s"
                }
                """.formatted(uploadTicketId, objectKey, sha256, fileSize, ownerType, displayName)));
    }

    private void seedDefaultQuotaPolicy() {
        seedQuotaPolicy("USER_DEFAULT_10G", "USER_DEFAULT", null, 10L * 1024L * 1024L * 1024L);
        seedQuotaPolicy("GLOBAL_400G", "GLOBAL", null, 400L * 1024L * 1024L * 1024L);
    }

    private void seedQuotaPolicy(String policyCode, String scopeType, Long scopeId, long quotaBytes) {
        insertQuotaPolicy(policyCode, scopeType, scopeId, quotaBytes);
    }

    private void insertQuotaPolicy(String policyCode, String scopeType, Long scopeId, long quotaBytes) {
        jdbcTemplate.update("""
            INSERT INTO sys_storage_quota_policy
                (id, policy_code, policy_name, scope_type, scope_id, quota_bytes,
                 cache_quota_bytes, warning_threshold, block_threshold, status)
            VALUES
                (?, ?, ?, ?, ?, ?, NULL, 0.8, 1.0, 'ENABLED')
            """, System.nanoTime(), policyCode, policyCode, scopeType, scopeId, quotaBytes);
    }

    private void insertUsage(String scopeType, Long scopeId, long usedBytes, long fileCount) {
        jdbcTemplate.update("""
            INSERT INTO sys_storage_usage
                (id, scope_type, scope_id, used_bytes, cache_used_bytes, file_count)
            VALUES
                (?, ?, ?, ?, 0, ?)
            """, System.nanoTime(), scopeType, scopeId, usedBytes, fileCount);
    }

    private JsonNode data(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.get("data");
    }

    private void assertAuditCountAtLeast(String actionType, int expectedMin) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_audit_log WHERE action_type = ?",
            Integer.class,
            actionType
        );
        assertTrue(count != null && count >= expectedMin, "应写入审计动作: " + actionType);
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

    private Cookie loginEmployee() throws Exception {
        return loginEmployee("zhangsan.employee");
    }

    private Cookie loginEmployee(String loginName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "Employee@123456"
                    }
                    """.formatted(loginName)))
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

    private void writeLocalObject(String objectKey, byte[] content) throws Exception {
        Path target = Path.of(localRoot).resolve(objectKey);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
