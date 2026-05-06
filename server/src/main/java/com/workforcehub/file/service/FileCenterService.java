package com.workforcehub.file.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.file.dto.AdminFileResponse;
import com.workforcehub.file.dto.AttachmentRefResponse;
import com.workforcehub.file.dto.ConfirmUploadRequest;
import com.workforcehub.file.dto.CreateQuotaPolicyRequest;
import com.workforcehub.file.dto.CreateDownloadTicketRequest;
import com.workforcehub.file.dto.CreateUploadTicketRequest;
import com.workforcehub.file.dto.DownloadFileResult;
import com.workforcehub.file.dto.DownloadTicketResponse;
import com.workforcehub.file.dto.FilePurgeResponse;
import com.workforcehub.file.dto.FileRestoreResponse;
import com.workforcehub.file.dto.FileTrashRequest;
import com.workforcehub.file.dto.FileTrashResponse;
import com.workforcehub.file.dto.QuotaPolicyResponse;
import com.workforcehub.file.dto.QuotaResponse;
import com.workforcehub.file.dto.StorageSummaryResponse;
import com.workforcehub.file.dto.UpdateQuotaPolicyRequest;
import com.workforcehub.file.dto.UploadTicketResponse;
import com.workforcehub.file.repository.AttachmentRefRepository;
import com.workforcehub.file.repository.AttachmentRow;
import com.workforcehub.file.repository.FileObjectRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件中心第一版服务。
 * 当前先落 LOCAL 主链路，Provider 字段保留 MINIO 扩展位。
 */
@Service
public class FileCenterService {

    private final JdbcTemplate jdbcTemplate;
    private final FileUploadTicketService fileUploadTicketService;
    private final FileUploadConfirmService fileUploadConfirmService;
    private final FileDownloadService fileDownloadService;
    private final StorageUsageLedgerService storageUsageLedgerService;
    private final FileObjectRepository fileObjectRepository;
    private final AttachmentRefRepository attachmentRefRepository;
    private final FileQuotaPolicyService fileQuotaPolicyService;

    public FileCenterService(JdbcTemplate jdbcTemplate,
                             FileUploadTicketService fileUploadTicketService,
                             FileUploadConfirmService fileUploadConfirmService,
                             FileDownloadService fileDownloadService,
                             StorageUsageLedgerService storageUsageLedgerService,
                             FileObjectRepository fileObjectRepository,
                             AttachmentRefRepository attachmentRefRepository,
                             FileQuotaPolicyService fileQuotaPolicyService) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileUploadTicketService = fileUploadTicketService;
        this.fileUploadConfirmService = fileUploadConfirmService;
        this.fileDownloadService = fileDownloadService;
        this.storageUsageLedgerService = storageUsageLedgerService;
        this.fileObjectRepository = fileObjectRepository;
        this.attachmentRefRepository = attachmentRefRepository;
        this.fileQuotaPolicyService = fileQuotaPolicyService;
    }

    public UploadTicketResponse createEmployeeUploadTicket(CreateUploadTicketRequest request) {
        return fileUploadTicketService.createEmployeeUploadTicket(request);
    }

    public UploadTicketResponse createAdminUploadTicket(CreateUploadTicketRequest request) {
        return fileUploadTicketService.createAdminUploadTicket(request);
    }

    public AttachmentRefResponse confirmEmployeeUpload(ConfirmUploadRequest request) {
        return fileUploadConfirmService.confirmEmployeeUpload(request);
    }

    public AttachmentRefResponse confirmAdminUpload(ConfirmUploadRequest request) {
        return fileUploadConfirmService.confirmAdminUpload(request);
    }

    public DownloadTicketResponse createEmployeeDownloadTicket(Long attachmentRefId, CreateDownloadTicketRequest request) {
        return fileDownloadService.createEmployeeDownloadTicket(attachmentRefId, request);
    }

    public DownloadFileResult downloadEmployeeFile(String ticketNo) {
        return fileDownloadService.downloadEmployeeFile(ticketNo);
    }

    public PageResult<AdminFileResponse> listAdminFiles(long pageNo, long pageSize, boolean recycleBin) {
        CurrentUserHolder.requireUserId();
        long safePageNo = Math.max(pageNo, 1L);
        long safePageSize = Math.min(Math.max(pageSize, 1L), 100L);
        long offset = (safePageNo - 1L) * safePageSize;
        String deletedFilter = recycleBin ? "ar.deleted_at IS NOT NULL" : "ar.deleted_at IS NULL";

        Long total = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE ar.owner_type = 'ADMIN_FILE'
              AND ar.uploader_type = 'ADMIN'
              AND ar.is_deleted = 0
              AND fo.is_deleted = 0
              AND %s
            """.formatted(deletedFilter), Long.class);

        List<AdminFileResponse> rows = jdbcTemplate.query("""
            SELECT ar.id AS attachment_ref_id, ar.file_object_id, ar.owner_type, ar.owner_id,
                   ar.original_file_name, ar.display_name, ar.uploader_type, ar.uploader_id,
                   ar.deleted_at, ar.purge_after, ar.created_at, ar.updated_at,
                   fo.mime_type, fo.file_size, fo.scan_status
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE ar.owner_type = 'ADMIN_FILE'
              AND ar.uploader_type = 'ADMIN'
              AND ar.is_deleted = 0
              AND fo.is_deleted = 0
              AND %s
            ORDER BY ar.created_at DESC, ar.id DESC
            LIMIT ? OFFSET ?
            """.formatted(deletedFilter),
            this::mapAdminFile,
            safePageSize,
            offset
        );
        return PageResult.of(safePageNo, safePageSize, total == null ? 0L : total, rows);
    }

    public DownloadTicketResponse createAdminDownloadTicket(Long attachmentRefId, CreateDownloadTicketRequest request) {
        return fileDownloadService.createAdminDownloadTicket(attachmentRefId, request);
    }

    public DownloadFileResult downloadAdminFile(String ticketNo) {
        return fileDownloadService.downloadAdminFile(ticketNo);
    }

    public QuotaResponse getEmployeeQuota() {
        return fileQuotaPolicyService.getEmployeeQuota();
    }

    public StorageSummaryResponse getStorageSummary() {
        return fileQuotaPolicyService.getStorageSummary();
    }

    public List<QuotaPolicyResponse> listQuotaPolicies(String scopeType) {
        return fileQuotaPolicyService.listQuotaPolicies(scopeType);
    }

    public QuotaPolicyResponse createQuotaPolicy(CreateQuotaPolicyRequest request) {
        return fileQuotaPolicyService.createQuotaPolicy(request);
    }

    public QuotaPolicyResponse updateQuotaPolicy(Long policyId, UpdateQuotaPolicyRequest request) {
        return fileQuotaPolicyService.updateQuotaPolicy(policyId, request);
    }

    @Transactional(rollbackFor = Exception.class)
    public FileTrashResponse trashEmployeeFile(Long attachmentRefId, FileTrashRequest request) {
        Long userId = CurrentUserHolder.requireUserId();
        AttachmentRow attachment = attachmentRefRepository.getAttachmentOrThrow(attachmentRefId);
        ensureUploaderOwnsAttachment(userId, attachment);
        if (attachment.isDeleted() != null && attachment.isDeleted() == 1) {
            throw new BizException(404, "附件引用不存在");
        }
        if (attachment.deletedAt() != null) {
            FileTrashResponse response = new FileTrashResponse();
            response.setAttachmentRefId(attachmentRefId);
            response.setDeletedAt(attachment.deletedAt());
            response.setPurgeAfter(attachment.purgeAfter());
            return response;
        }
        LocalDateTime deletedAt = LocalDateTime.now();
        LocalDateTime purgeAfter = deletedAt.plusDays(30);
        int updated = jdbcTemplate.update("""
            UPDATE sys_attachment_ref
            SET deleted_at = ?, deleted_by = ?, purge_after = ?, updated_by = ?
            WHERE id = ? AND is_deleted = 0 AND deleted_at IS NULL
            """, deletedAt, userId, purgeAfter, userId, attachmentRefId);
        if (updated == 0) {
            AttachmentRow latest = attachmentRefRepository.getAttachmentOrThrow(attachmentRefId);
            if (latest.isDeleted() != null && latest.isDeleted() == 1) {
                throw new BizException(404, "附件引用不存在");
            }
            FileTrashResponse response = new FileTrashResponse();
            response.setAttachmentRefId(attachmentRefId);
            response.setDeletedAt(latest.deletedAt());
            response.setPurgeAfter(latest.purgeAfter());
            return response;
        }

        FileTrashResponse response = new FileTrashResponse();
        response.setAttachmentRefId(attachmentRefId);
        response.setDeletedAt(deletedAt);
        response.setPurgeAfter(purgeAfter);
        audit("USER", userId, "FILE_TRASHED", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
            "{\"reason\":\"%s\"}".formatted(safeJsonText(request.getReason())));
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public FileRestoreResponse restoreEmployeeFile(Long attachmentRefId, FileTrashRequest request) {
        Long userId = CurrentUserHolder.requireUserId();
        AttachmentRow attachment = attachmentRefRepository.getAttachmentOrThrow(attachmentRefId);
        ensureUploaderOwnsAttachment(userId, attachment);
        if (attachment.isDeleted() != null && attachment.isDeleted() == 1) {
            throw new BizException(404, "附件引用不存在");
        }
        if (attachment.deletedAt() == null) {
            FileRestoreResponse response = new FileRestoreResponse();
            response.setAttachmentRefId(attachmentRefId);
            response.setRestoredAt(LocalDateTime.now());
            return response;
        }
        if (attachment.purgeAfter() != null && attachment.purgeAfter().isBefore(LocalDateTime.now())) {
            throw new BizException(409, "文件已超过回收站保留期");
        }
        fileQuotaPolicyService.ensurePersonalQuota(userId, 0L);
        fileQuotaPolicyService.ensureGlobalQuota(0L);
        int updated = jdbcTemplate.update("""
            UPDATE sys_attachment_ref
            SET deleted_at = NULL, deleted_by = NULL, purge_after = NULL, updated_by = ?
            WHERE id = ? AND is_deleted = 0 AND deleted_at IS NOT NULL
            """, userId, attachmentRefId);
        if (updated == 0) {
            AttachmentRow latest = attachmentRefRepository.getAttachmentOrThrow(attachmentRefId);
            if (latest.isDeleted() != null && latest.isDeleted() == 1) {
                throw new BizException(404, "附件引用不存在");
            }
            FileRestoreResponse response = new FileRestoreResponse();
            response.setAttachmentRefId(attachmentRefId);
            response.setRestoredAt(LocalDateTime.now());
            return response;
        }

        FileRestoreResponse response = new FileRestoreResponse();
        response.setAttachmentRefId(attachmentRefId);
        response.setRestoredAt(LocalDateTime.now());
        audit("USER", userId, "FILE_RESTORED", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
            "{\"reason\":\"%s\"}".formatted(safeJsonText(request.getReason())));
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public FilePurgeResponse purgeEmployeeFile(Long attachmentRefId) {
        Long userId = CurrentUserHolder.requireUserId();
        AttachmentRow attachment = attachmentRefRepository.getAttachmentOrThrow(attachmentRefId);
        ensureUploaderOwnsAttachment(userId, attachment);
        if (attachment.isDeleted() != null && attachment.isDeleted() == 1) {
            throw new BizException(404, "附件引用不存在");
        }
        if (attachment.deletedAt() == null) {
            throw new BizException(409, "请先将文件移入回收站");
        }
        int updated = jdbcTemplate.update(
            """
                UPDATE sys_attachment_ref
                SET is_deleted = 1, updated_by = ?
                WHERE id = ? AND is_deleted = 0 AND deleted_at IS NOT NULL
                """,
            userId,
            attachmentRefId
        );
        if (updated == 0) {
            throw new BizException(404, "附件引用不存在");
        }
        storageUsageLedgerService.releaseUsage("USER", userId, attachment.fileSize(), 1);
        storageUsageLedgerService.releaseUsage("GLOBAL", null, attachment.fileSize(), 1);
        int refCount = attachmentRefRepository.countActiveByFileObjectId(attachment.fileObjectId());
        boolean cleanupScheduled = fileObjectRepository.refreshRefCountAndCleanupAfter(
            attachment.fileObjectId(),
            refCount,
            LocalDateTime.now().plusDays(7)
        );

        FilePurgeResponse response = new FilePurgeResponse();
        response.setAttachmentRefId(attachmentRefId);
        response.setPurgedAt(LocalDateTime.now());
        response.setPhysicalCleanupScheduled(cleanupScheduled);
        audit("USER", userId, "FILE_PURGED", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
            "{\"physicalCleanupScheduled\":%s}".formatted(cleanupScheduled));
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public FileTrashResponse trashAdminFile(Long attachmentRefId, FileTrashRequest request) {
        Long adminId = CurrentUserHolder.requireUserId();
        AttachmentRow attachment = attachmentRefRepository.getAttachmentOrThrow(attachmentRefId);
        ensureAdminFileAttachment(attachment);
        if (attachment.isDeleted() != null && attachment.isDeleted() == 1) {
            throw new BizException(404, "附件引用不存在");
        }
        if (attachment.deletedAt() != null) {
            FileTrashResponse response = new FileTrashResponse();
            response.setAttachmentRefId(attachmentRefId);
            response.setDeletedAt(attachment.deletedAt());
            response.setPurgeAfter(attachment.purgeAfter());
            return response;
        }
        LocalDateTime deletedAt = LocalDateTime.now();
        LocalDateTime purgeAfter = deletedAt.plusDays(30);
        int updated = jdbcTemplate.update("""
            UPDATE sys_attachment_ref
            SET deleted_at = ?, deleted_by = ?, purge_after = ?, updated_by = ?
            WHERE id = ? AND is_deleted = 0 AND deleted_at IS NULL
            """, deletedAt, adminId, purgeAfter, adminId, attachmentRefId);
        if (updated == 0) {
            AttachmentRow latest = attachmentRefRepository.getAttachmentOrThrow(attachmentRefId);
            if (latest.isDeleted() != null && latest.isDeleted() == 1) {
                throw new BizException(404, "附件引用不存在");
            }
            FileTrashResponse response = new FileTrashResponse();
            response.setAttachmentRefId(attachmentRefId);
            response.setDeletedAt(latest.deletedAt());
            response.setPurgeAfter(latest.purgeAfter());
            return response;
        }

        FileTrashResponse response = new FileTrashResponse();
        response.setAttachmentRefId(attachmentRefId);
        response.setDeletedAt(deletedAt);
        response.setPurgeAfter(purgeAfter);
        audit("ADMIN", adminId, "FILE_ADMIN_TRASHED", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
            "{\"reason\":\"%s\"}".formatted(safeJsonText(request.getReason())));
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public FileRestoreResponse restoreAdminFile(Long attachmentRefId, FileTrashRequest request) {
        Long adminId = CurrentUserHolder.requireUserId();
        AttachmentRow attachment = attachmentRefRepository.getAttachmentOrThrow(attachmentRefId);
        ensureAdminFileAttachment(attachment);
        if (attachment.isDeleted() != null && attachment.isDeleted() == 1) {
            throw new BizException(404, "附件引用不存在");
        }
        if (attachment.deletedAt() == null) {
            FileRestoreResponse response = new FileRestoreResponse();
            response.setAttachmentRefId(attachmentRefId);
            response.setRestoredAt(LocalDateTime.now());
            return response;
        }
        if (attachment.purgeAfter() != null && attachment.purgeAfter().isBefore(LocalDateTime.now())) {
            throw new BizException(409, "文件已超过回收站保留期");
        }
        fileQuotaPolicyService.ensureGlobalQuota(0L);
        int updated = jdbcTemplate.update("""
            UPDATE sys_attachment_ref
            SET deleted_at = NULL, deleted_by = NULL, purge_after = NULL, updated_by = ?
            WHERE id = ? AND is_deleted = 0 AND deleted_at IS NOT NULL
            """, adminId, attachmentRefId);
        if (updated == 0) {
            AttachmentRow latest = attachmentRefRepository.getAttachmentOrThrow(attachmentRefId);
            if (latest.isDeleted() != null && latest.isDeleted() == 1) {
                throw new BizException(404, "附件引用不存在");
            }
            FileRestoreResponse response = new FileRestoreResponse();
            response.setAttachmentRefId(attachmentRefId);
            response.setRestoredAt(LocalDateTime.now());
            return response;
        }

        FileRestoreResponse response = new FileRestoreResponse();
        response.setAttachmentRefId(attachmentRefId);
        response.setRestoredAt(LocalDateTime.now());
        audit("ADMIN", adminId, "FILE_ADMIN_RESTORED", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
            "{\"reason\":\"%s\"}".formatted(safeJsonText(request.getReason())));
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public FilePurgeResponse purgeAdminFile(Long attachmentRefId) {
        Long adminId = CurrentUserHolder.requireUserId();
        AttachmentRow attachment = attachmentRefRepository.getAttachmentOrThrow(attachmentRefId);
        ensureAdminFileAttachment(attachment);
        if (attachment.isDeleted() != null && attachment.isDeleted() == 1) {
            throw new BizException(404, "附件引用不存在");
        }
        if (attachment.deletedAt() == null) {
            throw new BizException(409, "请先将文件移入回收站");
        }
        int updated = jdbcTemplate.update(
            """
                UPDATE sys_attachment_ref
                SET is_deleted = 1, updated_by = ?
                WHERE id = ? AND is_deleted = 0 AND deleted_at IS NOT NULL
                """,
            adminId,
            attachmentRefId
        );
        if (updated == 0) {
            throw new BizException(404, "附件引用不存在");
        }
        storageUsageLedgerService.releaseUsage("GLOBAL", null, attachment.fileSize(), 1);
        int refCount = attachmentRefRepository.countActiveByFileObjectId(attachment.fileObjectId());
        boolean cleanupScheduled = fileObjectRepository.refreshRefCountAndCleanupAfter(
            attachment.fileObjectId(),
            refCount,
            LocalDateTime.now().plusDays(7)
        );

        FilePurgeResponse response = new FilePurgeResponse();
        response.setAttachmentRefId(attachmentRefId);
        response.setPurgedAt(LocalDateTime.now());
        response.setPhysicalCleanupScheduled(cleanupScheduled);
        audit("ADMIN", adminId, "FILE_ADMIN_PURGED", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
            "{\"physicalCleanupScheduled\":%s}".formatted(cleanupScheduled));
        return response;
    }

    private void ensureUploaderOwnsAttachment(Long userId, AttachmentRow attachment) {
        if (!"USER".equals(attachment.uploaderType()) || !userId.equals(attachment.uploaderId())) {
            throw new BizException(403, "无权访问该文件");
        }
    }

    private void ensureAdminFileAttachment(AttachmentRow attachment) {
        if (!"ADMIN_FILE".equals(attachment.ownerType()) || !"ADMIN".equals(attachment.uploaderType())) {
            throw new BizException(403, "后台接口仅允许治理后台文件");
        }
    }

    private void audit(String operatorType, Long operatorId, String actionType,
                       String targetType, Long targetId, String result, String detailJson) {
        jdbcTemplate.update("""
            INSERT INTO sys_audit_log
                (id, operator_type, operator_id, action_type, target_type, target_id, result, detail_json)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON))
            """,
            IdWorker.getId(),
            operatorType,
            operatorId,
            actionType,
            targetType,
            targetId,
            result,
            detailJson
        );
    }

    private String safeJsonText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private AdminFileResponse mapAdminFile(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        AdminFileResponse response = new AdminFileResponse();
        response.setAttachmentRefId(rs.getLong("attachment_ref_id"));
        response.setFileObjectId(rs.getLong("file_object_id"));
        response.setOwnerType(rs.getString("owner_type"));
        response.setOwnerId(rs.getObject("owner_id", Long.class));
        response.setOriginalFileName(rs.getString("original_file_name"));
        response.setDisplayName(rs.getString("display_name"));
        response.setUploaderType(rs.getString("uploader_type"));
        response.setUploaderId(rs.getObject("uploader_id", Long.class));
        response.setMimeType(rs.getString("mime_type"));
        response.setFileSize(rs.getLong("file_size"));
        response.setScanStatus(rs.getString("scan_status"));
        response.setDeletedAt(rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toLocalDateTime());
        response.setPurgeAfter(rs.getTimestamp("purge_after") == null ? null : rs.getTimestamp("purge_after").toLocalDateTime());
        response.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        response.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return response;
    }

}
