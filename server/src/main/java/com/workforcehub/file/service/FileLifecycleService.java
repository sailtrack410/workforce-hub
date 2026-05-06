package com.workforcehub.file.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.file.dto.FilePurgeResponse;
import com.workforcehub.file.dto.FileRestoreResponse;
import com.workforcehub.file.dto.FileTrashRequest;
import com.workforcehub.file.dto.FileTrashResponse;
import com.workforcehub.file.repository.AttachmentRefRepository;
import com.workforcehub.file.repository.AttachmentRow;
import com.workforcehub.file.repository.FileObjectRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 文件回收站生命周期服务，负责移入回收站、恢复和彻底删除。
 */
@Service
public class FileLifecycleService {

    private final JdbcTemplate jdbcTemplate;
    private final StorageUsageLedgerService storageUsageLedgerService;
    private final FileObjectRepository fileObjectRepository;
    private final AttachmentRefRepository attachmentRefRepository;
    private final FileQuotaPolicyService fileQuotaPolicyService;

    public FileLifecycleService(JdbcTemplate jdbcTemplate,
                                StorageUsageLedgerService storageUsageLedgerService,
                                FileObjectRepository fileObjectRepository,
                                AttachmentRefRepository attachmentRefRepository,
                                FileQuotaPolicyService fileQuotaPolicyService) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageUsageLedgerService = storageUsageLedgerService;
        this.fileObjectRepository = fileObjectRepository;
        this.attachmentRefRepository = attachmentRefRepository;
        this.fileQuotaPolicyService = fileQuotaPolicyService;
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
            return buildTrashResponse(attachmentRefId, attachment.deletedAt(), attachment.purgeAfter());
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
            return buildTrashResponse(attachmentRefId, latest.deletedAt(), latest.purgeAfter());
        }

        audit("USER", userId, "FILE_TRASHED", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
            "{\"reason\":\"%s\"}".formatted(safeJsonText(request.getReason())));
        return buildTrashResponse(attachmentRefId, deletedAt, purgeAfter);
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
            return buildRestoreResponse(attachmentRefId);
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
            return buildRestoreResponse(attachmentRefId);
        }

        audit("USER", userId, "FILE_RESTORED", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
            "{\"reason\":\"%s\"}".formatted(safeJsonText(request.getReason())));
        return buildRestoreResponse(attachmentRefId);
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
        boolean cleanupScheduled = refreshFileObjectCleanupState(attachment);

        audit("USER", userId, "FILE_PURGED", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
            "{\"physicalCleanupScheduled\":%s}".formatted(cleanupScheduled));
        return buildPurgeResponse(attachmentRefId, cleanupScheduled);
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
            return buildTrashResponse(attachmentRefId, attachment.deletedAt(), attachment.purgeAfter());
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
            return buildTrashResponse(attachmentRefId, latest.deletedAt(), latest.purgeAfter());
        }

        audit("ADMIN", adminId, "FILE_ADMIN_TRASHED", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
            "{\"reason\":\"%s\"}".formatted(safeJsonText(request.getReason())));
        return buildTrashResponse(attachmentRefId, deletedAt, purgeAfter);
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
            return buildRestoreResponse(attachmentRefId);
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
            return buildRestoreResponse(attachmentRefId);
        }

        audit("ADMIN", adminId, "FILE_ADMIN_RESTORED", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
            "{\"reason\":\"%s\"}".formatted(safeJsonText(request.getReason())));
        return buildRestoreResponse(attachmentRefId);
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
        boolean cleanupScheduled = refreshFileObjectCleanupState(attachment);

        audit("ADMIN", adminId, "FILE_ADMIN_PURGED", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
            "{\"physicalCleanupScheduled\":%s}".formatted(cleanupScheduled));
        return buildPurgeResponse(attachmentRefId, cleanupScheduled);
    }

    private void ensureUploaderOwnsAttachment(Long userId, AttachmentRow attachment) {
        if (!"PERSONAL_FILE".equals(attachment.ownerType())) {
            throw new BizException(403, "个人文件接口仅允许操作个人文件");
        }
        if (!"USER".equals(attachment.uploaderType()) || !userId.equals(attachment.uploaderId())) {
            throw new BizException(403, "无权访问该文件");
        }
    }

    private void ensureAdminFileAttachment(AttachmentRow attachment) {
        if (!"ADMIN_FILE".equals(attachment.ownerType()) || !"ADMIN".equals(attachment.uploaderType())) {
            throw new BizException(403, "后台接口仅允许治理后台文件");
        }
    }

    private boolean refreshFileObjectCleanupState(AttachmentRow attachment) {
        int refCount = attachmentRefRepository.countActiveByFileObjectId(attachment.fileObjectId());
        return fileObjectRepository.refreshRefCountAndCleanupAfter(
            attachment.fileObjectId(),
            refCount,
            LocalDateTime.now().plusDays(7)
        );
    }

    private FileTrashResponse buildTrashResponse(Long attachmentRefId, LocalDateTime deletedAt, LocalDateTime purgeAfter) {
        FileTrashResponse response = new FileTrashResponse();
        response.setAttachmentRefId(attachmentRefId);
        response.setDeletedAt(deletedAt);
        response.setPurgeAfter(purgeAfter);
        return response;
    }

    private FileRestoreResponse buildRestoreResponse(Long attachmentRefId) {
        FileRestoreResponse response = new FileRestoreResponse();
        response.setAttachmentRefId(attachmentRefId);
        response.setRestoredAt(LocalDateTime.now());
        return response;
    }

    private FilePurgeResponse buildPurgeResponse(Long attachmentRefId, boolean cleanupScheduled) {
        FilePurgeResponse response = new FilePurgeResponse();
        response.setAttachmentRefId(attachmentRefId);
        response.setPurgedAt(LocalDateTime.now());
        response.setPhysicalCleanupScheduled(cleanupScheduled);
        return response;
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
}
