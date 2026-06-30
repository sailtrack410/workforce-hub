package com.workforcehub.file.service;

import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.common.audit.AuditLogWriter;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.common.util.TextValues;
import com.workforcehub.file.dto.AttachmentRefResponse;
import com.workforcehub.file.dto.ConfirmUploadRequest;
import com.workforcehub.file.repository.AttachmentRefRepository;
import com.workforcehub.file.repository.AttachmentRow;
import com.workforcehub.file.repository.FileObjectRepository;
import com.workforcehub.file.repository.FileObjectRow;
import com.workforcehub.file.storage.StorageProviderRegistry;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件上传确认服务。
 * 负责把已上传对象确认成附件引用，并完成去重、配额记账与审计。
 */
@Service
public class FileUploadConfirmService {

    private static final String CLEANUP_DEDUPLICATED_UPLOAD_OBJECT = "DEDUPLICATED_UPLOAD_OBJECT";

    private final JdbcTemplate jdbcTemplate;
    private final StorageProviderRegistry storageProviderRegistry;
    private final StorageUsageLedgerService storageUsageLedgerService;
    private final FileObjectRepository fileObjectRepository;
    private final AttachmentRefRepository attachmentRefRepository;
    private final FileQuotaPolicyService fileQuotaPolicyService;
    private final FileUploadRules fileUploadRules;
    private final AuditLogWriter auditLogWriter;

    public FileUploadConfirmService(JdbcTemplate jdbcTemplate,
                                    StorageProviderRegistry storageProviderRegistry,
                                    StorageUsageLedgerService storageUsageLedgerService,
                                    FileObjectRepository fileObjectRepository,
                                    AttachmentRefRepository attachmentRefRepository,
                                    FileQuotaPolicyService fileQuotaPolicyService,
                                    FileUploadRules fileUploadRules,
                                    AuditLogWriter auditLogWriter) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageProviderRegistry = storageProviderRegistry;
        this.storageUsageLedgerService = storageUsageLedgerService;
        this.fileObjectRepository = fileObjectRepository;
        this.attachmentRefRepository = attachmentRefRepository;
        this.fileQuotaPolicyService = fileQuotaPolicyService;
        this.fileUploadRules = fileUploadRules;
        this.auditLogWriter = auditLogWriter;
    }

    @Transactional(rollbackFor = Exception.class)
    public AttachmentRefResponse confirmEmployeeUpload(ConfirmUploadRequest request) {
        Long userId = CurrentUserHolder.requireUserId();
        UploadTicketRow ticket = getUploadTicketOrThrow(request.getUploadTicketId());
        if (!"USER".equals(ticket.uploaderType()) || !userId.equals(ticket.uploaderId())) {
            throw new BizException(403, "无权确认该上传票据");
        }
        Long existingRefId = attachmentRefRepository.findIdByUploadTicket(ticket.id());
        if (existingRefId != null) {
            markUploadTicketConfirmed(ticket.id());
            return buildAttachmentResponse(existingRefId, false);
        }
        if ("CONFIRMED".equals(ticket.status())) {
            throw new BizException(409, "上传票据已确认");
        }
        if (ticket.expireAt().isBefore(LocalDateTime.now())) {
            throw new BizException(409, "上传票据已过期");
        }
        if (!ticket.objectKey().equals(request.getObjectKey())) {
            throw new BizException(422, "文件对象不匹配，请重新上传");
        }
        if (!ticket.contentSha256().equalsIgnoreCase(request.getContentSha256())
            || !ticket.fileSize().equals(request.getFileSize())) {
            throw new BizException(422, "文件校验失败，请重新上传");
        }
        if (!ticket.bizType().equals(request.getOwnerType()) || !sameId(ticket.bizId(), request.getOwnerId())) {
            throw new BizException(422, "业务归属不匹配，请重新申请上传票据");
        }
        fileUploadRules.validateEmployeeOwner(request.getOwnerType(), request.getOwnerId());
        fileUploadRules.validateEmployeeFolder(request.getOwnerType(), userId, request.getFolderId());

        storageProviderRegistry.require(ticket.storageProvider())
            .verifyObject(ticket.bucketName(), request.getObjectKey(), request.getContentSha256(), request.getFileSize());
        fileUploadRules.ensureEmployeeQuota(request.getOwnerType(), userId, request.getFileSize());
        fileQuotaPolicyService.ensureGlobalQuota(request.getFileSize());

        boolean deduplicated = false;
        FileObjectRow fileObject = fileObjectRepository.findActiveBySha256AndSize(
            request.getContentSha256(),
            request.getFileSize()
        );
        if (fileObject == null) {
            fileObject = createFileObject(ticket, request, userId);
        } else {
            deduplicated = true;
        }
        deduplicated = isDeduplicatedUploadObject(ticket, fileObject);

        Long attachmentRefId;
        try {
            attachmentRefId = createAttachmentRef(
                request.getOwnerType(),
                request.getOwnerId(),
                fileObject.id(),
                ticket.id(),
                ticket.fileName(),
                request.getDisplayName(),
                "USER",
                userId,
                request.getFolderId()
            );
        } catch (DuplicateKeyException e) {
            Long concurrentRefId = attachmentRefRepository.findIdByUploadTicket(ticket.id());
            if (concurrentRefId != null) {
                markUploadTicketConfirmed(ticket.id());
                return buildAttachmentResponse(concurrentRefId, false);
            }
            throw e;
        }
        fileObjectRepository.increaseRefCount(fileObject.id());
        fileUploadRules.increaseEmployeeUsage(request.getOwnerType(), userId, request.getFileSize());
        storageUsageLedgerService.increaseUsage("GLOBAL", null, request.getFileSize(), 1);
        if (deduplicated) {
            scheduleDeduplicatedUploadObjectCleanup(ticket, fileObject);
        }
        markUploadTicketConfirmed(ticket.id());
        auditLogWriter.write("USER", userId, "FILE_UPLOAD_CONFIRMED", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
            "{\"fileObjectId\":%s,\"deduplicated\":%s}".formatted(fileObject.id(), deduplicated));
        return buildAttachmentResponse(attachmentRefId, deduplicated);
    }

    @Transactional(rollbackFor = Exception.class)
    public AttachmentRefResponse confirmAdminUpload(ConfirmUploadRequest request) {
        Long adminId = CurrentUserHolder.requireUserId();
        UploadTicketRow ticket = getUploadTicketOrThrow(request.getUploadTicketId());
        if (!"ADMIN".equals(ticket.uploaderType()) || !adminId.equals(ticket.uploaderId())) {
            throw new BizException(403, "无权确认该上传票据");
        }
        Long existingRefId = attachmentRefRepository.findIdByUploadTicket(ticket.id());
        if (existingRefId != null) {
            markUploadTicketConfirmed(ticket.id());
            return buildAttachmentResponse(existingRefId, false);
        }
        if ("CONFIRMED".equals(ticket.status())) {
            throw new BizException(409, "上传票据已确认");
        }
        if (ticket.expireAt().isBefore(LocalDateTime.now())) {
            throw new BizException(409, "上传票据已过期");
        }
        if (!ticket.objectKey().equals(request.getObjectKey())) {
            throw new BizException(422, "文件对象不匹配，请重新上传");
        }
        if (!ticket.contentSha256().equalsIgnoreCase(request.getContentSha256())
            || !ticket.fileSize().equals(request.getFileSize())) {
            throw new BizException(422, "文件校验失败，请重新上传");
        }
        if (!ticket.bizType().equals(request.getOwnerType()) || !sameId(ticket.bizId(), request.getOwnerId())) {
            throw new BizException(422, "业务归属不匹配，请重新申请上传票据");
        }
        fileUploadRules.validateAdminOwner(request.getOwnerType(), request.getOwnerId());

        storageProviderRegistry.require(ticket.storageProvider())
            .verifyObject(ticket.bucketName(), request.getObjectKey(), request.getContentSha256(), request.getFileSize());
        fileQuotaPolicyService.ensureGlobalQuota(request.getFileSize());

        boolean deduplicated = false;
        FileObjectRow fileObject = fileObjectRepository.findActiveBySha256AndSize(
            request.getContentSha256(),
            request.getFileSize()
        );
        if (fileObject == null) {
            fileObject = createFileObject(ticket, request, adminId);
        } else {
            deduplicated = true;
        }
        deduplicated = isDeduplicatedUploadObject(ticket, fileObject);

        Long attachmentRefId;
        try {
            attachmentRefId = createAttachmentRef(
                request.getOwnerType(),
                request.getOwnerId(),
                fileObject.id(),
                ticket.id(),
                ticket.fileName(),
                request.getDisplayName(),
                "ADMIN",
                adminId,
                null
            );
        } catch (DuplicateKeyException e) {
            Long concurrentRefId = attachmentRefRepository.findIdByUploadTicket(ticket.id());
            if (concurrentRefId != null) {
                markUploadTicketConfirmed(ticket.id());
                return buildAttachmentResponse(concurrentRefId, false);
            }
            throw e;
        }
        fileObjectRepository.increaseRefCount(fileObject.id());
        storageUsageLedgerService.increaseUsage("GLOBAL", null, request.getFileSize(), 1);
        if (deduplicated) {
            scheduleDeduplicatedUploadObjectCleanup(ticket, fileObject);
        }
        markUploadTicketConfirmed(ticket.id());
        auditLogWriter.write("ADMIN", adminId, "FILE_ADMIN_UPLOAD_CONFIRMED", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
            "{\"fileObjectId\":%s,\"deduplicated\":%s}".formatted(fileObject.id(), deduplicated));
        return buildAttachmentResponse(attachmentRefId, deduplicated);
    }

    private FileObjectRow createFileObject(UploadTicketRow ticket, ConfirmUploadRequest request, Long userId) {
        Long fileObjectId = IdGenerator.nextId();
        String fileExt = extractExt(ticket.fileName());
        try {
            jdbcTemplate.update("""
                INSERT INTO sys_file_object
                    (id, storage_provider, bucket_name, object_key, file_name, file_ext,
                     mime_type, file_size, file_hash, content_md5, content_sha256,
                     ref_count, upload_status, scan_status, created_by)
                VALUES
                    (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'UPLOADED', 'PENDING', ?)
                """,
                fileObjectId,
                ticket.storageProvider(),
                ticket.bucketName(),
                ticket.objectKey(),
                ticket.fileName(),
                fileExt,
                ticket.mimeType(),
                ticket.fileSize(),
                request.getContentSha256().toLowerCase(),
                TextValues.blankToNull(ticket.contentMd5()),
                request.getContentSha256().toLowerCase(),
                userId
            );
            return fileObjectRepository.getFileObjectOrThrow(fileObjectId);
        } catch (DuplicateKeyException e) {
            FileObjectRow existing = fileObjectRepository.findActiveBySha256AndSize(
                request.getContentSha256(),
                request.getFileSize()
            );
            if (existing == null) {
                throw e;
            }
            return existing;
        }
    }

    private Long createAttachmentRef(String ownerType,
                                     Long ownerId,
                                     Long fileObjectId,
                                     Long sourceUploadTicketId,
                                     String originalFileName,
                                     String displayName,
                                     String uploaderType,
                                     Long uploaderId,
                                     Long folderId) {
        Long attachmentRefId = IdGenerator.nextId();
        attachmentRefRepository.create(
            attachmentRefId,
            ownerType,
            ownerId,
            fileObjectId,
            sourceUploadTicketId,
            originalFileName,
            displayName,
            uploaderType,
            uploaderId,
            folderId
        );
        return attachmentRefId;
    }

    private void scheduleDeduplicatedUploadObjectCleanup(UploadTicketRow ticket, FileObjectRow fileObject) {
        if (sameStorageObject(ticket, fileObject)) {
            return;
        }
        jdbcTemplate.update("""
            INSERT INTO sys_file_cleanup_job_detail
                (id, job_run_id, item_type, target_type, target_id,
                 storage_provider, bucket_name, object_key, status)
            VALUES
                (?, ?, ?, 'UPLOAD_TICKET', ?, ?, ?, ?, 'PENDING')
            """,
            IdGenerator.nextId(),
            "FILE-CLEAN-DEDUP-" + ticket.id(),
            CLEANUP_DEDUPLICATED_UPLOAD_OBJECT,
            ticket.id(),
            ticket.storageProvider(),
            ticket.bucketName(),
            ticket.objectKey()
        );
    }

    private boolean isDeduplicatedUploadObject(UploadTicketRow ticket, FileObjectRow fileObject) {
        return !sameStorageObject(ticket, fileObject);
    }

    private void markUploadTicketConfirmed(Long ticketId) {
        jdbcTemplate.update(
            "UPDATE sys_upload_ticket SET status = 'CONFIRMED', confirmed_at = NOW(), updated_at = NOW() WHERE id = ?",
            ticketId
        );
    }

    private AttachmentRefResponse buildAttachmentResponse(Long attachmentRefId, boolean deduplicated) {
        AttachmentRow row = attachmentRefRepository.getAttachmentOrThrow(attachmentRefId);
        AttachmentRefResponse response = new AttachmentRefResponse();
        response.setAttachmentRefId(row.id());
        response.setFileObjectId(row.fileObjectId());
        response.setDisplayName(row.displayName());
        response.setMimeType(row.mimeType());
        response.setFileSize(row.fileSize());
        response.setContentSha256(row.contentSha256());
        response.setDeduplicated(deduplicated);
        response.setScanStatus(row.scanStatus());
        response.setCreatedAt(row.createdAt());
        return response;
    }

    private UploadTicketRow getUploadTicketOrThrow(Long ticketId) {
        List<UploadTicketRow> rows = jdbcTemplate.query("""
            SELECT id, ticket_no, uploader_type, uploader_id, biz_type, biz_id,
                   storage_provider, bucket_name, file_name, mime_type, file_size,
                   content_md5, content_sha256, object_key, expire_at, status
            FROM sys_upload_ticket
            WHERE id = ? AND is_deleted = 0
            """,
            (rs, rowNum) -> new UploadTicketRow(
                rs.getLong("id"),
                rs.getString("ticket_no"),
                rs.getString("uploader_type"),
                rs.getLong("uploader_id"),
                rs.getString("biz_type"),
                rs.getObject("biz_id", Long.class),
                rs.getString("storage_provider"),
                rs.getString("bucket_name"),
                rs.getString("file_name"),
                rs.getString("mime_type"),
                rs.getLong("file_size"),
                rs.getString("content_md5"),
                rs.getString("content_sha256"),
                rs.getString("object_key"),
                rs.getTimestamp("expire_at").toLocalDateTime(),
                rs.getString("status")
            ),
            ticketId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "上传票据不存在");
        }
        return rows.get(0);
    }

    private String extractExt(String fileName) {
        int dotIndex = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dotIndex + 1).toLowerCase();
    }

    private boolean sameId(Long left, Long right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean sameStorageObject(UploadTicketRow ticket, FileObjectRow fileObject) {
        return sameText(ticket.storageProvider(), fileObject.storageProvider())
            && sameText(ticket.bucketName(), fileObject.bucketName())
            && sameText(ticket.objectKey(), fileObject.objectKey());
    }

    private boolean sameText(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private record UploadTicketRow(
        Long id,
        String ticketNo,
        String uploaderType,
        Long uploaderId,
        String bizType,
        Long bizId,
        String storageProvider,
        String bucketName,
        String fileName,
        String mimeType,
        Long fileSize,
        String contentMd5,
        String contentSha256,
        String objectKey,
        LocalDateTime expireAt,
        String status
    ) {}
}
