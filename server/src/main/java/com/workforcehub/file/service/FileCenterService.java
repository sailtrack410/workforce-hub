package com.workforcehub.file.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.workforcehub.file.storage.LocalStorageProvider;
import com.workforcehub.file.storage.StorageObjectResource;
import com.workforcehub.file.storage.StorageProvider;
import com.workforcehub.file.storage.StorageProviderRegistry;
import com.workforcehub.file.storage.StorageUploadTarget;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文件中心第一版服务。
 * 当前先落 LOCAL 主链路，Provider 字段保留 MINIO 扩展位。
 */
@Service
public class FileCenterService {

    private static final String PROVIDER_LOCAL = LocalStorageProvider.PROVIDER_CODE;
    private static final long DEFAULT_PERSONAL_QUOTA_BYTES = 10L * 1024L * 1024L * 1024L;
    private static final long DEFAULT_GLOBAL_QUOTA_BYTES = 400L * 1024L * 1024L * 1024L;

    private final JdbcTemplate jdbcTemplate;
    private final StorageProviderRegistry storageProviderRegistry;
    private final ObjectMapper objectMapper;
    private final DownloadTicketExecutionService downloadTicketExecutionService;
    private final StorageUsageLedgerService storageUsageLedgerService;

    public FileCenterService(JdbcTemplate jdbcTemplate,
                             StorageProviderRegistry storageProviderRegistry,
                             ObjectMapper objectMapper,
                             DownloadTicketExecutionService downloadTicketExecutionService,
                             StorageUsageLedgerService storageUsageLedgerService) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageProviderRegistry = storageProviderRegistry;
        this.objectMapper = objectMapper;
        this.downloadTicketExecutionService = downloadTicketExecutionService;
        this.storageUsageLedgerService = storageUsageLedgerService;
    }

    @Transactional(rollbackFor = Exception.class)
    public UploadTicketResponse createEmployeeUploadTicket(CreateUploadTicketRequest request) {
        Long userId = CurrentUserHolder.requireUserId();
        validateEmployeeOwner(request.getOwnerType(), request.getOwnerId());
        UploadTicketResponse existingTicket = findUploadTicketByClientRequestId(userId, request.getClientRequestId());
        if (existingTicket != null) {
            return existingTicket;
        }
        ensureUploadPolicy(request);
        ensurePersonalQuota(userId, request.getFileSize());
        ensureGlobalQuota(request.getFileSize());

        FileObjectRow existing = findReusableFileObjectForUploader(
            "USER",
            userId,
            request.getContentSha256(),
            request.getFileSize()
        );
        if (existing != null) {
            Long deduplicatedTicketId;
            Long attachmentRefId;
            try {
                deduplicatedTicketId = createConfirmedDeduplicatedUploadTicket("USER", userId, request, existing);
                attachmentRefId = createAttachmentRef(
                    request.getOwnerType(),
                    request.getOwnerId(),
                    existing.id(),
                    deduplicatedTicketId,
                    request.getFileName(),
                    request.getFileName(),
                    "USER",
                    userId
                );
            } catch (DuplicateKeyException e) {
                UploadTicketResponse concurrentTicket = findUploadTicketByClientRequestId(userId, request.getClientRequestId());
                if (concurrentTicket != null) {
                    return concurrentTicket;
                }
                throw e;
            }
            increaseFileRefCount(existing.id());
            storageUsageLedgerService.increaseUsage("USER", userId, request.getFileSize(), 1);
            storageUsageLedgerService.increaseUsage("GLOBAL", null, request.getFileSize(), 1);
            audit("USER", userId, "FILE_UPLOAD_DEDUP", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
                "{\"fileObjectId\":%s}".formatted(existing.id()));
            return deduplicatedResponse(deduplicatedTicketId, "UP" + deduplicatedTicketId, existing, attachmentRefId);
        }

        Long ticketId = IdWorker.getId();
        String ticketNo = "UP" + ticketId;
        String objectKey = buildObjectKey(request.getContentSha256(), request.getFileName(), ticketId);
        StorageProvider provider = storageProviderRegistry.require(PROVIDER_LOCAL);
        String bucketName = provider.defaultBucketName();
        StorageUploadTarget uploadTarget = provider.buildUploadTarget(bucketName, objectKey, request.getMimeType(), 60);

        try {
            jdbcTemplate.update("""
                INSERT INTO sys_upload_ticket
                    (id, ticket_no, uploader_type, uploader_id, biz_type, biz_id,
                     storage_provider, bucket_name, file_name, mime_type, file_size,
                     content_md5, content_sha256, object_key, client_request_id,
                     expire_at, status, created_by)
                VALUES
                    (?, ?, 'USER', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                """,
                ticketId,
                ticketNo,
                userId,
                request.getOwnerType(),
                request.getOwnerId(),
                PROVIDER_LOCAL,
                bucketName,
                request.getFileName(),
                request.getMimeType(),
                request.getFileSize(),
                blankToNull(request.getContentMd5()),
                request.getContentSha256().toLowerCase(),
                objectKey,
                blankToNull(request.getClientRequestId()),
                uploadTarget.expireAt(),
                userId
            );
        } catch (DuplicateKeyException e) {
            UploadTicketResponse concurrentTicket = findUploadTicketByClientRequestId(userId, request.getClientRequestId());
            if (concurrentTicket != null) {
                return concurrentTicket;
            }
            throw e;
        }

        UploadTicketResponse response = new UploadTicketResponse();
        response.setUploadTicketId(ticketId);
        response.setTicketNo(ticketNo);
        response.setDeduplicated(false);
        response.setAttachmentRefId(null);
        response.setStorageProvider(PROVIDER_LOCAL);
        response.setBucketName(bucketName);
        response.setObjectKey(objectKey);
        response.setUploadUrl(uploadTarget.uploadUrl());
        response.setHeaders(uploadTarget.headers());
        response.setExpireAt(uploadTarget.expireAt());
        audit("USER", userId, "FILE_UPLOAD_TICKET_CREATED", "UPLOAD_TICKET", ticketId, "SUCCESS",
            "{\"objectKey\":\"%s\"}".formatted(objectKey));
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public UploadTicketResponse createAdminUploadTicket(CreateUploadTicketRequest request) {
        Long adminId = CurrentUserHolder.requireUserId();
        validateAdminOwner(request.getOwnerType(), request.getOwnerId());
        UploadTicketResponse existingTicket = findUploadTicketByClientRequestId("ADMIN", adminId, request.getClientRequestId());
        if (existingTicket != null) {
            return existingTicket;
        }
        ensureUploadPolicy(request);
        ensureGlobalQuota(request.getFileSize());

        FileObjectRow existing = findReusableFileObjectForUploader(
            "ADMIN",
            adminId,
            request.getContentSha256(),
            request.getFileSize()
        );
        if (existing != null) {
            Long deduplicatedTicketId;
            Long attachmentRefId;
            try {
                deduplicatedTicketId = createConfirmedDeduplicatedUploadTicket("ADMIN", adminId, request, existing);
                attachmentRefId = createAttachmentRef(
                    request.getOwnerType(),
                    request.getOwnerId(),
                    existing.id(),
                    deduplicatedTicketId,
                    request.getFileName(),
                    request.getFileName(),
                    "ADMIN",
                    adminId
                );
            } catch (DuplicateKeyException e) {
                UploadTicketResponse concurrentTicket = findUploadTicketByClientRequestId(
                    "ADMIN",
                    adminId,
                    request.getClientRequestId()
                );
                if (concurrentTicket != null) {
                    return concurrentTicket;
                }
                throw e;
            }
            increaseFileRefCount(existing.id());
            storageUsageLedgerService.increaseUsage("GLOBAL", null, request.getFileSize(), 1);
            audit("ADMIN", adminId, "FILE_ADMIN_UPLOAD_DEDUP", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
                "{\"fileObjectId\":%s}".formatted(existing.id()));
            return deduplicatedResponse(deduplicatedTicketId, "UP" + deduplicatedTicketId, existing, attachmentRefId);
        }

        Long ticketId = IdWorker.getId();
        String ticketNo = "UP" + ticketId;
        String objectKey = buildObjectKey(request.getContentSha256(), request.getFileName(), ticketId);
        StorageProvider provider = storageProviderRegistry.require(PROVIDER_LOCAL);
        String bucketName = provider.defaultBucketName();
        StorageUploadTarget uploadTarget = provider.buildUploadTarget(bucketName, objectKey, request.getMimeType(), 60);

        try {
            jdbcTemplate.update("""
                INSERT INTO sys_upload_ticket
                    (id, ticket_no, uploader_type, uploader_id, biz_type, biz_id,
                     storage_provider, bucket_name, file_name, mime_type, file_size,
                     content_md5, content_sha256, object_key, client_request_id,
                     expire_at, status, created_by)
                VALUES
                    (?, ?, 'ADMIN', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                """,
                ticketId,
                ticketNo,
                adminId,
                request.getOwnerType(),
                request.getOwnerId(),
                PROVIDER_LOCAL,
                bucketName,
                request.getFileName(),
                request.getMimeType(),
                request.getFileSize(),
                blankToNull(request.getContentMd5()),
                request.getContentSha256().toLowerCase(),
                objectKey,
                blankToNull(request.getClientRequestId()),
                uploadTarget.expireAt(),
                adminId
            );
        } catch (DuplicateKeyException e) {
            UploadTicketResponse concurrentTicket = findUploadTicketByClientRequestId(
                "ADMIN",
                adminId,
                request.getClientRequestId()
            );
            if (concurrentTicket != null) {
                return concurrentTicket;
            }
            throw e;
        }

        UploadTicketResponse response = new UploadTicketResponse();
        response.setUploadTicketId(ticketId);
        response.setTicketNo(ticketNo);
        response.setDeduplicated(false);
        response.setAttachmentRefId(null);
        response.setStorageProvider(PROVIDER_LOCAL);
        response.setBucketName(bucketName);
        response.setObjectKey(objectKey);
        response.setUploadUrl(uploadTarget.uploadUrl());
        response.setHeaders(uploadTarget.headers());
        response.setExpireAt(uploadTarget.expireAt());
        audit("ADMIN", adminId, "FILE_ADMIN_UPLOAD_TICKET_CREATED", "UPLOAD_TICKET", ticketId, "SUCCESS",
            "{\"objectKey\":\"%s\"}".formatted(objectKey));
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public AttachmentRefResponse confirmEmployeeUpload(ConfirmUploadRequest request) {
        Long userId = CurrentUserHolder.requireUserId();
        UploadTicketRow ticket = getUploadTicketOrThrow(request.getUploadTicketId());
        if (!"USER".equals(ticket.uploaderType()) || !userId.equals(ticket.uploaderId())) {
            throw new BizException(403, "无权确认该上传票据");
        }
        Long existingRefId = findAttachmentRefIdByTicket(ticket.id());
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
        validateEmployeeOwner(request.getOwnerType(), request.getOwnerId());

        storageProviderRegistry.require(ticket.storageProvider())
            .verifyObject(ticket.bucketName(), request.getObjectKey(), request.getContentSha256(), request.getFileSize());
        ensurePersonalQuota(userId, request.getFileSize());
        ensureGlobalQuota(request.getFileSize());

        boolean deduplicated = false;
        FileObjectRow fileObject = findActiveFileObject(request.getContentSha256(), request.getFileSize());
        if (fileObject == null) {
            fileObject = createFileObject(ticket, request, userId);
        } else {
            deduplicated = true;
        }

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
                userId
            );
        } catch (DuplicateKeyException e) {
            Long concurrentRefId = findAttachmentRefIdByTicket(ticket.id());
            if (concurrentRefId != null) {
                markUploadTicketConfirmed(ticket.id());
                return buildAttachmentResponse(concurrentRefId, false);
            }
            throw e;
        }
        increaseFileRefCount(fileObject.id());
        storageUsageLedgerService.increaseUsage("USER", userId, request.getFileSize(), 1);
        storageUsageLedgerService.increaseUsage("GLOBAL", null, request.getFileSize(), 1);
        markUploadTicketConfirmed(ticket.id());
        audit("USER", userId, "FILE_UPLOAD_CONFIRMED", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
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
        Long existingRefId = findAttachmentRefIdByTicket(ticket.id());
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
        validateAdminOwner(request.getOwnerType(), request.getOwnerId());

        storageProviderRegistry.require(ticket.storageProvider())
            .verifyObject(ticket.bucketName(), request.getObjectKey(), request.getContentSha256(), request.getFileSize());
        ensureGlobalQuota(request.getFileSize());

        boolean deduplicated = false;
        FileObjectRow fileObject = findActiveFileObject(request.getContentSha256(), request.getFileSize());
        if (fileObject == null) {
            fileObject = createFileObject(ticket, request, adminId);
        } else {
            deduplicated = true;
        }

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
                adminId
            );
        } catch (DuplicateKeyException e) {
            Long concurrentRefId = findAttachmentRefIdByTicket(ticket.id());
            if (concurrentRefId != null) {
                markUploadTicketConfirmed(ticket.id());
                return buildAttachmentResponse(concurrentRefId, false);
            }
            throw e;
        }
        increaseFileRefCount(fileObject.id());
        storageUsageLedgerService.increaseUsage("GLOBAL", null, request.getFileSize(), 1);
        markUploadTicketConfirmed(ticket.id());
        audit("ADMIN", adminId, "FILE_ADMIN_UPLOAD_CONFIRMED", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
            "{\"fileObjectId\":%s,\"deduplicated\":%s}".formatted(fileObject.id(), deduplicated));
        return buildAttachmentResponse(attachmentRefId, deduplicated);
    }

    @Transactional(rollbackFor = Exception.class)
    public DownloadTicketResponse createEmployeeDownloadTicket(Long attachmentRefId, CreateDownloadTicketRequest request) {
        Long userId = CurrentUserHolder.requireUserId();
        AttachmentRow attachment = getAttachmentOrThrow(attachmentRefId);
        if (!"USER".equals(attachment.uploaderType()) || !userId.equals(attachment.uploaderId())) {
            throw new BizException(403, "无权访问该文件");
        }
        if (attachment.deletedAt() != null || attachment.isDeleted() != 0) {
            throw new BizException(409, "文件已删除");
        }
        if (!"SINGLE".equalsIgnoreCase(request.getDownloadType())) {
            throw new BizException(400, "第一版仅支持单文件下载");
        }

        Long ticketId = IdWorker.getId();
        String ticketNo = "DL" + ticketId;
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(10);
        String downloadUrl = "/api/employee/files/download/" + ticketNo;
        jdbcTemplate.update("""
            INSERT INTO sys_download_ticket
                (id, ticket_no, attachment_ref_id, requester_type, requester_id,
                 download_type, download_url, reason, expire_at, status)
            VALUES
                (?, ?, ?, 'USER', ?, 'SINGLE', ?, ?, ?, 'ACTIVE')
            """,
            ticketId,
            ticketNo,
            attachmentRefId,
            userId,
            downloadUrl,
            blankToNull(request.getReason()),
            expireAt
        );

        DownloadTicketResponse response = new DownloadTicketResponse();
        response.setDownloadTicketId(ticketId);
        response.setTicketNo(ticketNo);
        response.setDownloadUrl(downloadUrl);
        response.setExpireAt(expireAt);
        response.setFileName(attachment.displayName());
        response.setFileSize(attachment.fileSize());
        audit("USER", userId, "FILE_DOWNLOAD_TICKET_CREATED", "DOWNLOAD_TICKET", ticketId, "SUCCESS",
            "{\"attachmentRefId\":%s}".formatted(attachmentRefId));
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public DownloadFileResult downloadEmployeeFile(String ticketNo) {
        Long userId = CurrentUserHolder.requireUserId();
        DownloadTicketRow ticket = getDownloadTicketOrThrow(ticketNo);
        if (!"USER".equals(ticket.requesterType()) || !userId.equals(ticket.requesterId())) {
            throw new BizException(403, "无权使用该下载票据");
        }
        if (!"ACTIVE".equals(ticket.status())) {
            throw new BizException(409, "下载票据已使用或已失效");
        }
        if (ticket.expireAt().isBefore(LocalDateTime.now())) {
            downloadTicketExecutionService.expireActiveTicket(ticket.id());
            throw new BizException(410, "下载链接已过期，请重新申请");
        }

        DownloadAttachmentRow attachment = getDownloadAttachmentOrThrow(ticket.attachmentRefId());
        if (!"USER".equals(attachment.uploaderType()) || !userId.equals(attachment.uploaderId())) {
            throw new BizException(403, "无权访问该文件");
        }
        if (attachment.deletedAt() != null || attachment.attachmentDeleted() != 0 || attachment.fileDeleted() != 0) {
            throw new BizException(409, "文件已删除");
        }

        downloadTicketExecutionService.claimActiveTicket(ticket.id());
        StorageObjectResource resource = storageProviderRegistry.require(attachment.storageProvider())
            .loadObject(attachment.bucketName(), attachment.objectKey());
        audit("USER", userId, "FILE_DOWNLOAD_EXECUTED", "DOWNLOAD_TICKET", ticket.id(), "SUCCESS",
            "{\"attachmentRefId\":%s}".formatted(attachment.attachmentRefId()));
        return new DownloadFileResult(resource.resource(), attachment.displayName(), attachment.mimeType(), resource.size());
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

    @Transactional(rollbackFor = Exception.class)
    public DownloadTicketResponse createAdminDownloadTicket(Long attachmentRefId, CreateDownloadTicketRequest request) {
        Long adminId = CurrentUserHolder.requireUserId();
        AttachmentRow attachment = getAttachmentOrThrow(attachmentRefId);
        ensureAdminFileAttachment(attachment);
        if (attachment.deletedAt() != null || attachment.isDeleted() != 0) {
            throw new BizException(409, "文件已删除");
        }
        if (!"SINGLE".equalsIgnoreCase(request.getDownloadType())) {
            throw new BizException(400, "第一版仅支持单文件下载");
        }

        Long ticketId = IdWorker.getId();
        String ticketNo = "DL" + ticketId;
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(10);
        String downloadUrl = "/api/admin/files/download/" + ticketNo;
        jdbcTemplate.update("""
            INSERT INTO sys_download_ticket
                (id, ticket_no, attachment_ref_id, requester_type, requester_id,
                 download_type, download_url, reason, expire_at, status)
            VALUES
                (?, ?, ?, 'ADMIN', ?, 'SINGLE', ?, ?, ?, 'ACTIVE')
            """,
            ticketId,
            ticketNo,
            attachmentRefId,
            adminId,
            downloadUrl,
            blankToNull(request.getReason()),
            expireAt
        );

        DownloadTicketResponse response = new DownloadTicketResponse();
        response.setDownloadTicketId(ticketId);
        response.setTicketNo(ticketNo);
        response.setDownloadUrl(downloadUrl);
        response.setExpireAt(expireAt);
        response.setFileName(attachment.displayName());
        response.setFileSize(attachment.fileSize());
        audit("ADMIN", adminId, "FILE_ADMIN_DOWNLOAD_TICKET_CREATED", "DOWNLOAD_TICKET", ticketId, "SUCCESS",
            "{\"attachmentRefId\":%s}".formatted(attachmentRefId));
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public DownloadFileResult downloadAdminFile(String ticketNo) {
        Long adminId = CurrentUserHolder.requireUserId();
        DownloadTicketRow ticket = getDownloadTicketOrThrow(ticketNo);
        if (!"ADMIN".equals(ticket.requesterType()) || !adminId.equals(ticket.requesterId())) {
            throw new BizException(403, "无权使用该下载票据");
        }
        if (!"ACTIVE".equals(ticket.status())) {
            throw new BizException(409, "下载票据已使用或已失效");
        }
        if (ticket.expireAt().isBefore(LocalDateTime.now())) {
            downloadTicketExecutionService.expireActiveTicket(ticket.id());
            throw new BizException(410, "下载链接已过期，请重新申请");
        }

        DownloadAttachmentRow attachment = getDownloadAttachmentOrThrow(ticket.attachmentRefId());
        ensureAdminDownloadAttachment(attachment);
        if (attachment.deletedAt() != null || attachment.attachmentDeleted() != 0 || attachment.fileDeleted() != 0) {
            throw new BizException(409, "文件已删除");
        }

        downloadTicketExecutionService.claimActiveTicket(ticket.id());
        StorageObjectResource resource = storageProviderRegistry.require(attachment.storageProvider())
            .loadObject(attachment.bucketName(), attachment.objectKey());
        audit("ADMIN", adminId, "FILE_ADMIN_DOWNLOAD_EXECUTED", "DOWNLOAD_TICKET", ticket.id(), "SUCCESS",
            "{\"attachmentRefId\":%s}".formatted(attachment.attachmentRefId()));
        return new DownloadFileResult(resource.resource(), attachment.displayName(), attachment.mimeType(), resource.size());
    }

    public QuotaResponse getEmployeeQuota() {
        Long userId = CurrentUserHolder.requireUserId();
        long personalQuota = resolveQuota("USER", userId, resolveQuota("USER_DEFAULT", null, DEFAULT_PERSONAL_QUOTA_BYTES));
        long personalUsed = storageUsageLedgerService.usedBytes("USER", userId);
        long globalQuota = resolveQuota("GLOBAL", null, DEFAULT_GLOBAL_QUOTA_BYTES);
        long globalUsed = storageUsageLedgerService.usedBytes("GLOBAL", null);
        long cacheQuota = resolveQuota("TEMP_CACHE", null, 50L * 1024L * 1024L * 1024L);
        long cacheUsed = storageUsageLedgerService.cacheUsedBytes("TEMP_CACHE", null);

        QuotaResponse response = new QuotaResponse();
        response.setPersonalQuotaBytes(personalQuota);
        response.setPersonalUsedBytes(personalUsed);
        response.setPersonalUsagePercent(ratio(personalUsed, personalQuota));
        response.setOrgPoolQuotaBytes(globalQuota);
        response.setOrgPoolUsedBytes(globalUsed);
        response.setCacheQuotaBytes(cacheQuota);
        response.setCacheUsedBytes(cacheUsed);
        response.setUploadWritable(personalUsed < personalQuota && globalUsed < globalQuota);
        response.setBlockReason(resolveBlockReason(personalUsed, personalQuota, globalUsed, globalQuota));
        return response;
    }

    public StorageSummaryResponse getStorageSummary() {
        CurrentUserHolder.requireUserId();
        StorageProvider provider = storageProviderRegistry.require(PROVIDER_LOCAL);
        long globalQuota = resolveQuota("GLOBAL", null, DEFAULT_GLOBAL_QUOTA_BYTES);
        long globalUsed = storageUsageLedgerService.usedBytes("GLOBAL", null);
        long cacheQuota = resolveQuota("TEMP_CACHE", null, 50L * 1024L * 1024L * 1024L);
        long cacheUsed = storageUsageLedgerService.cacheUsedBytes("TEMP_CACHE", null);

        StorageSummaryResponse response = new StorageSummaryResponse();
        response.setOrgPoolQuotaBytes(globalQuota);
        response.setOrgPoolUsedBytes(globalUsed);
        response.setOrgPoolWarningThreshold(BigDecimal.valueOf(0.8));
        response.setOrgPoolBlockThreshold(BigDecimal.ONE);
        response.setCacheQuotaBytes(cacheQuota);
        response.setCacheUsedBytes(cacheUsed);
        response.setBackendFreeBytes(provider.usableFreeBytes());
        response.setBackendWritable(provider.usableFreeBytes() > 0 && globalUsed < globalQuota);
        response.setWriteProtectReason(globalUsed >= globalQuota ? "企业文件总空间不足" : null);
        response.setFileObjectCount(countLong("SELECT COUNT(*) FROM sys_file_object WHERE is_deleted = 0"));
        response.setAttachmentRefCount(countLong("SELECT COUNT(*) FROM sys_attachment_ref WHERE is_deleted = 0"));
        response.setRecycleBinCount(countLong("SELECT COUNT(*) FROM sys_attachment_ref WHERE deleted_at IS NOT NULL AND is_deleted = 0"));
        response.setPendingCleanupObjectCount(countLong("SELECT COUNT(*) FROM sys_file_object WHERE cleanup_after IS NOT NULL AND is_deleted = 0"));
        return response;
    }

    public List<QuotaPolicyResponse> listQuotaPolicies(String scopeType) {
        CurrentUserHolder.requireUserId();
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT id, policy_code, policy_name, scope_type, scope_id, quota_bytes,
                   cache_quota_bytes, warning_threshold, block_threshold, status, created_at, updated_at
            FROM sys_storage_quota_policy
            WHERE is_deleted = 0
            """);
        if (StringUtils.hasText(scopeType)) {
            validateScopeType(scopeType);
            sql.append(" AND scope_type = ?");
            args.add(scopeType);
        }
        sql.append(" ORDER BY updated_at DESC, id DESC");
        return jdbcTemplate.query(sql.toString(), this::mapQuotaPolicy, args.toArray());
    }

    @Transactional(rollbackFor = Exception.class)
    public QuotaPolicyResponse createQuotaPolicy(CreateQuotaPolicyRequest request) {
        Long adminId = CurrentUserHolder.requireUserId();
        validateQuotaPolicyPayload(
            request.getScopeType(),
            request.getScopeId(),
            request.getQuotaBytes(),
            request.getWarningThreshold(),
            request.getBlockThreshold(),
            request.getStatus()
        );
        ensureQuotaPolicyCodeNotExists(request.getPolicyCode());
        ensureQuotaPolicyScopeNotExists(request.getScopeType(), request.getScopeId(), null);
        Long policyId = IdWorker.getId();
        jdbcTemplate.update("""
            INSERT INTO sys_storage_quota_policy
                (id, policy_code, policy_name, scope_type, scope_id, quota_bytes,
                 cache_quota_bytes, warning_threshold, block_threshold, status, created_by)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            policyId,
            request.getPolicyCode(),
            request.getPolicyName(),
            request.getScopeType(),
            request.getScopeId(),
            request.getQuotaBytes(),
            request.getCacheQuotaBytes(),
            request.getWarningThreshold(),
            request.getBlockThreshold(),
            request.getStatus(),
            adminId
        );
        audit("ADMIN", adminId, "FILE_QUOTA_POLICY_CREATED", "STORAGE_QUOTA_POLICY", policyId, "SUCCESS",
            "{\"scopeType\":\"%s\",\"scopeId\":%s}".formatted(request.getScopeType(), request.getScopeId()));
        return getQuotaPolicyOrThrow(policyId);
    }

    @Transactional(rollbackFor = Exception.class)
    public QuotaPolicyResponse updateQuotaPolicy(Long policyId, UpdateQuotaPolicyRequest request) {
        Long adminId = CurrentUserHolder.requireUserId();
        QuotaPolicyRow existing = getQuotaPolicyRowOrThrow(policyId);
        validateQuotaPolicyPayload(
            existing.scopeType(),
            existing.scopeId(),
            request.getQuotaBytes(),
            request.getWarningThreshold(),
            request.getBlockThreshold(),
            request.getStatus()
        );
        jdbcTemplate.update("""
            UPDATE sys_storage_quota_policy
            SET policy_name = ?,
                quota_bytes = ?,
                cache_quota_bytes = ?,
                warning_threshold = ?,
                block_threshold = ?,
                status = ?,
                updated_by = ?,
                updated_at = NOW()
            WHERE id = ? AND is_deleted = 0
            """,
            request.getPolicyName(),
            request.getQuotaBytes(),
            request.getCacheQuotaBytes(),
            request.getWarningThreshold(),
            request.getBlockThreshold(),
            request.getStatus(),
            adminId,
            policyId
        );
        audit("ADMIN", adminId, "FILE_QUOTA_POLICY_UPDATED", "STORAGE_QUOTA_POLICY", policyId, "SUCCESS",
            "{\"scopeType\":\"%s\",\"scopeId\":%s}".formatted(existing.scopeType(), existing.scopeId()));
        return getQuotaPolicyOrThrow(policyId);
    }

    @Transactional(rollbackFor = Exception.class)
    public FileTrashResponse trashEmployeeFile(Long attachmentRefId, FileTrashRequest request) {
        Long userId = CurrentUserHolder.requireUserId();
        AttachmentRow attachment = getAttachmentOrThrow(attachmentRefId);
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
            AttachmentRow latest = getAttachmentOrThrow(attachmentRefId);
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
        AttachmentRow attachment = getAttachmentOrThrow(attachmentRefId);
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
        ensurePersonalQuota(userId, 0L);
        ensureGlobalQuota(0L);
        int updated = jdbcTemplate.update("""
            UPDATE sys_attachment_ref
            SET deleted_at = NULL, deleted_by = NULL, purge_after = NULL, updated_by = ?
            WHERE id = ? AND is_deleted = 0 AND deleted_at IS NOT NULL
            """, userId, attachmentRefId);
        if (updated == 0) {
            AttachmentRow latest = getAttachmentOrThrow(attachmentRefId);
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
        AttachmentRow attachment = getAttachmentOrThrow(attachmentRefId);
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
        Integer refCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_attachment_ref WHERE file_object_id = ? AND is_deleted = 0",
            Integer.class,
            attachment.fileObjectId()
        );
        boolean cleanupScheduled = refCount == null || refCount == 0;
        if (cleanupScheduled) {
            jdbcTemplate.update(
                "UPDATE sys_file_object SET ref_count = 0, cleanup_after = ? WHERE id = ?",
                LocalDateTime.now().plusDays(7),
                attachment.fileObjectId()
            );
        } else {
            jdbcTemplate.update("UPDATE sys_file_object SET ref_count = ? WHERE id = ?", refCount, attachment.fileObjectId());
        }

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
        AttachmentRow attachment = getAttachmentOrThrow(attachmentRefId);
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
            AttachmentRow latest = getAttachmentOrThrow(attachmentRefId);
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
        AttachmentRow attachment = getAttachmentOrThrow(attachmentRefId);
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
        ensureGlobalQuota(0L);
        int updated = jdbcTemplate.update("""
            UPDATE sys_attachment_ref
            SET deleted_at = NULL, deleted_by = NULL, purge_after = NULL, updated_by = ?
            WHERE id = ? AND is_deleted = 0 AND deleted_at IS NOT NULL
            """, adminId, attachmentRefId);
        if (updated == 0) {
            AttachmentRow latest = getAttachmentOrThrow(attachmentRefId);
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
        AttachmentRow attachment = getAttachmentOrThrow(attachmentRefId);
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
        Integer refCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_attachment_ref WHERE file_object_id = ? AND is_deleted = 0",
            Integer.class,
            attachment.fileObjectId()
        );
        boolean cleanupScheduled = refCount == null || refCount == 0;
        if (cleanupScheduled) {
            jdbcTemplate.update(
                "UPDATE sys_file_object SET ref_count = 0, cleanup_after = ? WHERE id = ?",
                LocalDateTime.now().plusDays(7),
                attachment.fileObjectId()
            );
        } else {
            jdbcTemplate.update("UPDATE sys_file_object SET ref_count = ? WHERE id = ?", refCount, attachment.fileObjectId());
        }

        FilePurgeResponse response = new FilePurgeResponse();
        response.setAttachmentRefId(attachmentRefId);
        response.setPurgedAt(LocalDateTime.now());
        response.setPhysicalCleanupScheduled(cleanupScheduled);
        audit("ADMIN", adminId, "FILE_ADMIN_PURGED", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
            "{\"physicalCleanupScheduled\":%s}".formatted(cleanupScheduled));
        return response;
    }

    private void validateEmployeeOwner(String ownerType, Long ownerId) {
        if (!"PERSONAL_FILE".equals(ownerType)) {
            throw new BizException(400, "员工端第一版仅支持个人文件");
        }
        if (ownerId != null) {
            throw new BizException(400, "个人文件不应指定业务归属ID");
        }
    }

    private void validateAdminOwner(String ownerType, Long ownerId) {
        if (!"ADMIN_FILE".equals(ownerType)) {
            throw new BizException(400, "后台第一版仅支持后台治理文件");
        }
        if (ownerId != null) {
            throw new BizException(400, "后台治理文件不应指定业务归属ID");
        }
    }

    private void ensureUploadPolicy(CreateUploadTicketRequest request) {
        UploadPolicyRow policy = getUploadPolicyOrThrow(request.getOwnerType());
        if (request.getFileSize() > policy.maxFileSizeBytes()) {
            throw new BizException(413, "文件超过当前场景大小限制");
        }

        String extension = extractExt(request.getFileName());
        List<String> allowedExtensions = parseJsonList(policy.allowedExtensions(), "扩展名");
        if (!allowedExtensions.isEmpty()
            && (!StringUtils.hasText(extension) || !allowedExtensions.contains(extension.toLowerCase()))) {
            throw new BizException(415, "当前场景不支持该文件类型");
        }

        String mimeType = normalizeMimeType(request.getMimeType());
        List<String> allowedMimeTypes = parseJsonList(policy.allowedMimeTypes(), "MIME类型");
        if (!allowedMimeTypes.isEmpty() && !allowedMimeTypes.contains(mimeType)) {
            throw new BizException(415, "当前场景不支持该文件类型");
        }
    }

    private UploadPolicyRow getUploadPolicyOrThrow(String ownerType) {
        List<UploadPolicyRow> rows = jdbcTemplate.query("""
            SELECT id, owner_type, max_file_size_bytes, allowed_extensions, allowed_mime_types
            FROM sys_file_upload_policy
            WHERE owner_type = ? AND status = 'ENABLED' AND is_deleted = 0
            ORDER BY updated_at DESC
            LIMIT 1
            """,
            (rs, rowNum) -> new UploadPolicyRow(
                rs.getLong("id"),
                rs.getString("owner_type"),
                rs.getLong("max_file_size_bytes"),
                rs.getString("allowed_extensions"),
                rs.getString("allowed_mime_types")
            ),
            ownerType
        );
        if (rows.isEmpty()) {
            throw new BizException(409, "当前场景未配置上传策略");
        }
        return rows.get(0);
    }

    private List<String> parseJsonList(String json, String policyFieldName) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {})
                .stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase())
                .distinct()
                .toList();
        } catch (JsonProcessingException e) {
            throw new BizException(500, "上传策略" + policyFieldName + "配置格式不正确");
        }
    }

    private String normalizeMimeType(String mimeType) {
        String normalized = mimeType.trim().toLowerCase();
        int parameterIndex = normalized.indexOf(';');
        return parameterIndex < 0 ? normalized : normalized.substring(0, parameterIndex).trim();
    }

    private FileObjectRow createFileObject(UploadTicketRow ticket, ConfirmUploadRequest request, Long userId) {
        Long fileObjectId = IdWorker.getId();
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
                blankToNull(ticket.contentMd5()),
                request.getContentSha256().toLowerCase(),
                userId
            );
            return getFileObjectOrThrow(fileObjectId);
        } catch (DuplicateKeyException e) {
            FileObjectRow existing = findActiveFileObject(request.getContentSha256(), request.getFileSize());
            if (existing == null) {
                throw e;
            }
            return existing;
        }
    }

    private Long createConfirmedDeduplicatedUploadTicket(String uploaderType,
                                                         Long uploaderId,
                                                         CreateUploadTicketRequest request,
                                                         FileObjectRow fileObject) {
        Long ticketId = IdWorker.getId();
        jdbcTemplate.update("""
            INSERT INTO sys_upload_ticket
                (id, ticket_no, uploader_type, uploader_id, biz_type, biz_id,
                 storage_provider, bucket_name, file_name, mime_type, file_size,
                 content_md5, content_sha256, object_key, client_request_id,
                 expire_at, status, confirmed_at, created_by)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CONFIRMED', NOW(), ?)
            """,
            ticketId,
            "UP" + ticketId,
            uploaderType,
            uploaderId,
            request.getOwnerType(),
            request.getOwnerId(),
            fileObject.storageProvider(),
            fileObject.bucketName(),
            request.getFileName(),
            request.getMimeType(),
            request.getFileSize(),
            blankToNull(request.getContentMd5()),
            request.getContentSha256().toLowerCase(),
            fileObject.objectKey(),
            blankToNull(request.getClientRequestId()),
            LocalDateTime.now(),
            uploaderId
        );
        return ticketId;
    }

    private Long createAttachmentRef(String ownerType,
                                     Long ownerId,
                                     Long fileObjectId,
                                     Long sourceUploadTicketId,
                                     String originalFileName,
                                     String displayName,
                                     String uploaderType,
                                     Long uploaderId) {
        Long attachmentRefId = IdWorker.getId();
        jdbcTemplate.update("""
            INSERT INTO sys_attachment_ref
                (id, owner_type, owner_id, file_object_id, source_upload_ticket_id, original_file_name,
                 display_name, uploader_type, uploader_id, visibility_scope, created_by)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, ?, ?, 'OWNER', ?)
            """,
            attachmentRefId,
            ownerType,
            ownerId,
            fileObjectId,
            sourceUploadTicketId,
            originalFileName,
            displayName,
            uploaderType,
            uploaderId,
            uploaderId
        );
        return attachmentRefId;
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

    private void ensureAdminDownloadAttachment(DownloadAttachmentRow attachment) {
        if (!"ADMIN_FILE".equals(attachment.ownerType()) || !"ADMIN".equals(attachment.uploaderType())) {
            throw new BizException(403, "后台接口仅允许治理后台文件");
        }
    }

    private void ensurePersonalQuota(Long userId, Long incomingBytes) {
        long quota = resolveQuota("USER", userId, resolveQuota("USER_DEFAULT", null, DEFAULT_PERSONAL_QUOTA_BYTES));
        long used = storageUsageLedgerService.usedBytes("USER", userId);
        if (used + incomingBytes > quota) {
            throw new BizException(507, "个人云空间不足，请清理文件或联系管理员扩容");
        }
    }

    private void ensureGlobalQuota(Long incomingBytes) {
        long quota = resolveQuota("GLOBAL", null, DEFAULT_GLOBAL_QUOTA_BYTES);
        long used = storageUsageLedgerService.usedBytes("GLOBAL", null);
        if (used + incomingBytes > quota) {
            throw new BizException(507, "企业文件总空间不足，请联系系统管理员扩容");
        }
    }

    private long resolveQuota(String scopeType, Long scopeId, long defaultValue) {
        List<Long> values = jdbcTemplate.query("""
            SELECT quota_bytes FROM sys_storage_quota_policy
            WHERE scope_type = ? AND ((scope_id IS NULL AND ? IS NULL) OR scope_id = ?)
              AND status = 'ENABLED' AND is_deleted = 0
            ORDER BY updated_at DESC
            LIMIT 1
            """,
            (rs, rowNum) -> rs.getLong("quota_bytes"),
            scopeType,
            scopeId,
            scopeId
        );
        return values.isEmpty() ? defaultValue : values.get(0);
    }

    private void validateQuotaPolicyPayload(String scopeType,
                                            Long scopeId,
                                            Long quotaBytes,
                                            BigDecimal warningThreshold,
                                            BigDecimal blockThreshold,
                                            String status) {
        validateScopeType(scopeType);
        if (requiresScopeId(scopeType) && scopeId == null) {
            throw new BizException(400, "当前作用域必须指定 scopeId");
        }
        if (!requiresScopeId(scopeType) && scopeId != null) {
            throw new BizException(400, "当前作用域不应指定 scopeId");
        }
        if (!List.of("ENABLED", "DISABLED").contains(status)) {
            throw new BizException(400, "策略状态仅支持 ENABLED 或 DISABLED");
        }
        if (warningThreshold.compareTo(blockThreshold) >= 0) {
            throw new BizException(400, "预警阈值必须小于阻断阈值");
        }
        long usedBytes = storageUsageLedgerService.usedBytes(scopeType, scopeId);
        if (quotaBytes < usedBytes) {
            throw new BizException(409, "配额不能低于当前已用量");
        }
    }

    private void validateScopeType(String scopeType) {
        if (!List.of("GLOBAL", "USER_DEFAULT", "USER", "DEPARTMENT", "ROLE", "GROUP", "KNOWLEDGE_SPACE", "TEMP_CACHE")
            .contains(scopeType)) {
            throw new BizException(400, "不支持的配额作用域类型");
        }
    }

    private void ensureQuotaPolicyScopeNotExists(String scopeType, Long scopeId, Long exceptPolicyId) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_storage_quota_policy
            WHERE scope_type = ?
              AND ((scope_id IS NULL AND ? IS NULL) OR scope_id = ?)
              AND (? IS NULL OR id <> ?)
              AND is_deleted = 0
            """,
            Integer.class,
            scopeType,
            scopeId,
            scopeId,
            exceptPolicyId,
            exceptPolicyId
        );
        if (count != null && count > 0) {
            throw new BizException(409, "同一作用域已存在配额策略");
        }
    }

    private void ensureQuotaPolicyCodeNotExists(String policyCode) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_storage_quota_policy
            WHERE policy_code = ? AND is_deleted = 0
            """,
            Integer.class,
            policyCode
        );
        if (count != null && count > 0) {
            throw new BizException(409, "配额策略编码已存在");
        }
    }

    private boolean requiresScopeId(String scopeType) {
        return List.of("USER", "DEPARTMENT", "ROLE", "GROUP", "KNOWLEDGE_SPACE").contains(scopeType);
    }

    private QuotaPolicyResponse getQuotaPolicyOrThrow(Long policyId) {
        QuotaPolicyRow row = getQuotaPolicyRowOrThrow(policyId);
        return toQuotaPolicyResponse(row);
    }

    private QuotaPolicyRow getQuotaPolicyRowOrThrow(Long policyId) {
        List<QuotaPolicyRow> rows = jdbcTemplate.query("""
            SELECT id, policy_code, policy_name, scope_type, scope_id, quota_bytes,
                   cache_quota_bytes, warning_threshold, block_threshold, status, created_at, updated_at
            FROM sys_storage_quota_policy
            WHERE id = ? AND is_deleted = 0
            """,
            this::mapQuotaPolicyRow,
            policyId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "配额策略不存在");
        }
        return rows.get(0);
    }

    private QuotaPolicyResponse mapQuotaPolicy(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return toQuotaPolicyResponse(mapQuotaPolicyRow(rs, rowNum));
    }

    private QuotaPolicyRow mapQuotaPolicyRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new QuotaPolicyRow(
            rs.getLong("id"),
            rs.getString("policy_code"),
            rs.getString("policy_name"),
            rs.getString("scope_type"),
            rs.getObject("scope_id", Long.class),
            rs.getLong("quota_bytes"),
            rs.getObject("cache_quota_bytes", Long.class),
            rs.getBigDecimal("warning_threshold"),
            rs.getBigDecimal("block_threshold"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private QuotaPolicyResponse toQuotaPolicyResponse(QuotaPolicyRow row) {
        QuotaPolicyResponse response = new QuotaPolicyResponse();
        response.setPolicyId(row.id());
        response.setPolicyCode(row.policyCode());
        response.setPolicyName(row.policyName());
        response.setScopeType(row.scopeType());
        response.setScopeId(row.scopeId());
        response.setQuotaBytes(row.quotaBytes());
        response.setCacheQuotaBytes(row.cacheQuotaBytes());
        response.setWarningThreshold(row.warningThreshold());
        response.setBlockThreshold(row.blockThreshold());
        response.setStatus(row.status());
        response.setCreatedAt(row.createdAt());
        response.setUpdatedAt(row.updatedAt());
        return response;
    }

    private BigDecimal ratio(long used, long quota) {
        if (quota <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(used)
            .divide(BigDecimal.valueOf(quota), 4, RoundingMode.HALF_UP);
    }

    private String resolveBlockReason(long personalUsed, long personalQuota, long globalUsed, long globalQuota) {
        if (personalUsed >= personalQuota) {
            return "个人云空间不足，请清理文件或联系管理员扩容";
        }
        if (globalUsed >= globalQuota) {
            return "企业文件总空间不足，请联系系统管理员扩容";
        }
        return null;
    }

    private long countLong(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
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

    private UploadTicketResponse deduplicatedResponse(FileObjectRow fileObject, Long attachmentRefId) {
        UploadTicketResponse response = new UploadTicketResponse();
        response.setUploadTicketId(null);
        response.setTicketNo(null);
        response.setDeduplicated(true);
        response.setAttachmentRefId(attachmentRefId);
        response.setStorageProvider(fileObject.storageProvider());
        response.setBucketName(fileObject.bucketName());
        response.setObjectKey(fileObject.objectKey());
        response.setUploadUrl(null);
        response.setHeaders(Map.of());
        response.setExpireAt(null);
        return response;
    }

    private UploadTicketResponse deduplicatedResponse(Long uploadTicketId,
                                                      String ticketNo,
                                                      FileObjectRow fileObject,
                                                      Long attachmentRefId) {
        UploadTicketResponse response = deduplicatedResponse(fileObject, attachmentRefId);
        response.setUploadTicketId(uploadTicketId);
        response.setTicketNo(ticketNo);
        return response;
    }

    private void markUploadTicketConfirmed(Long ticketId) {
        jdbcTemplate.update(
            "UPDATE sys_upload_ticket SET status = 'CONFIRMED', confirmed_at = NOW(), updated_at = NOW() WHERE id = ?",
            ticketId
        );
    }

    private AttachmentRefResponse buildAttachmentResponse(Long attachmentRefId, boolean deduplicated) {
        AttachmentRow row = getAttachmentOrThrow(attachmentRefId);
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

    private DownloadTicketRow getDownloadTicketOrThrow(String ticketNo) {
        List<DownloadTicketRow> rows = jdbcTemplate.query("""
            SELECT id, ticket_no, attachment_ref_id, requester_type, requester_id,
                   download_type, expire_at, status
            FROM sys_download_ticket
            WHERE ticket_no = ? AND is_deleted = 0
            """,
            (rs, rowNum) -> new DownloadTicketRow(
                rs.getLong("id"),
                rs.getString("ticket_no"),
                rs.getLong("attachment_ref_id"),
                rs.getString("requester_type"),
                rs.getLong("requester_id"),
                rs.getString("download_type"),
                rs.getTimestamp("expire_at").toLocalDateTime(),
                rs.getString("status")
            ),
            ticketNo
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "下载票据不存在");
        }
        return rows.get(0);
    }

    private DownloadAttachmentRow getDownloadAttachmentOrThrow(Long attachmentRefId) {
        List<DownloadAttachmentRow> rows = jdbcTemplate.query("""
            SELECT ar.id AS attachment_ref_id, ar.owner_type, ar.display_name, ar.uploader_type, ar.uploader_id,
                   ar.deleted_at, ar.is_deleted AS attachment_deleted,
                   fo.storage_provider, fo.bucket_name, fo.object_key, fo.mime_type, fo.file_size,
                   fo.is_deleted AS file_deleted
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE ar.id = ?
            """,
            (rs, rowNum) -> new DownloadAttachmentRow(
                rs.getLong("attachment_ref_id"),
                rs.getString("owner_type"),
                rs.getString("display_name"),
                rs.getString("uploader_type"),
                rs.getLong("uploader_id"),
                rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toLocalDateTime(),
                rs.getInt("attachment_deleted"),
                rs.getString("storage_provider"),
                rs.getString("bucket_name"),
                rs.getString("object_key"),
                rs.getString("mime_type"),
                rs.getLong("file_size"),
                rs.getInt("file_deleted")
            ),
            attachmentRefId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "附件引用不存在");
        }
        return rows.get(0);
    }

    private AttachmentRow getAttachmentOrThrow(Long attachmentRefId) {
        List<AttachmentRow> rows = jdbcTemplate.query("""
            SELECT ar.id, ar.file_object_id, ar.owner_type, ar.owner_id, ar.display_name, ar.uploader_type, ar.uploader_id,
                   ar.deleted_at, ar.purge_after, ar.created_at, ar.is_deleted,
                   fo.mime_type, fo.file_size, fo.content_sha256, fo.scan_status
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE ar.id = ?
            """,
            (rs, rowNum) -> new AttachmentRow(
                rs.getLong("id"),
                rs.getLong("file_object_id"),
                rs.getString("owner_type"),
                rs.getObject("owner_id", Long.class),
                rs.getString("display_name"),
                rs.getString("uploader_type"),
                rs.getLong("uploader_id"),
                rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toLocalDateTime(),
                rs.getTimestamp("purge_after") == null ? null : rs.getTimestamp("purge_after").toLocalDateTime(),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getInt("is_deleted"),
                rs.getString("mime_type"),
                rs.getLong("file_size"),
                rs.getString("content_sha256"),
                rs.getString("scan_status")
            ),
            attachmentRefId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "附件引用不存在");
        }
        return rows.get(0);
    }

    private FileObjectRow getFileObjectOrThrow(Long fileObjectId) {
        List<FileObjectRow> rows = jdbcTemplate.query("""
            SELECT id, storage_provider, bucket_name, object_key, file_size, content_sha256
            FROM sys_file_object
            WHERE id = ? AND is_deleted = 0
            """,
            (rs, rowNum) -> new FileObjectRow(
                rs.getLong("id"),
                rs.getString("storage_provider"),
                rs.getString("bucket_name"),
                rs.getString("object_key"),
                rs.getLong("file_size"),
                rs.getString("content_sha256")
            ),
            fileObjectId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "文件对象不存在");
        }
        return rows.get(0);
    }

    private FileObjectRow findActiveFileObject(String contentSha256, Long fileSize) {
        List<FileObjectRow> rows = jdbcTemplate.query("""
            SELECT id, storage_provider, bucket_name, object_key, file_size, content_sha256
            FROM sys_file_object
            WHERE content_sha256 = ? AND file_size = ? AND is_deleted = 0
            LIMIT 1
            """,
            (rs, rowNum) -> new FileObjectRow(
                rs.getLong("id"),
                rs.getString("storage_provider"),
                rs.getString("bucket_name"),
                rs.getString("object_key"),
                rs.getLong("file_size"),
                rs.getString("content_sha256")
            ),
            contentSha256.toLowerCase(),
            fileSize
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private FileObjectRow findReusableFileObjectForUploader(String uploaderType,
                                                            Long uploaderId,
                                                            String contentSha256,
                                                            Long fileSize) {
        List<FileObjectRow> rows = jdbcTemplate.query("""
            SELECT fo.id, fo.storage_provider, fo.bucket_name, fo.object_key, fo.file_size, fo.content_sha256
            FROM sys_file_object fo
            JOIN sys_attachment_ref ar ON ar.file_object_id = fo.id
            WHERE fo.content_sha256 = ?
              AND fo.file_size = ?
              AND fo.is_deleted = 0
              AND ar.uploader_type = ?
              AND ar.uploader_id = ?
              AND ar.deleted_at IS NULL
              AND ar.is_deleted = 0
            ORDER BY ar.created_at DESC, ar.id DESC
            LIMIT 1
            """,
            (rs, rowNum) -> new FileObjectRow(
                rs.getLong("id"),
                rs.getString("storage_provider"),
                rs.getString("bucket_name"),
                rs.getString("object_key"),
                rs.getLong("file_size"),
                rs.getString("content_sha256")
            ),
            contentSha256.toLowerCase(),
            fileSize,
            uploaderType,
            uploaderId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Long findAttachmentRefIdByTicket(Long uploadTicketId) {
        return jdbcTemplate.query("""
            SELECT id
            FROM sys_attachment_ref
            WHERE source_upload_ticket_id = ? AND is_deleted = 0
            ORDER BY created_at DESC
            LIMIT 1
            """, rs -> rs.next() ? rs.getLong("id") : null, uploadTicketId);
    }

    private UploadTicketResponse findUploadTicketByClientRequestId(Long userId, String clientRequestId) {
        return findUploadTicketByClientRequestId("USER", userId, clientRequestId);
    }

    private UploadTicketResponse findUploadTicketByClientRequestId(String uploaderType, Long uploaderId, String clientRequestId) {
        if (!StringUtils.hasText(clientRequestId)) {
            return null;
        }
        List<UploadTicketResponse> rows = jdbcTemplate.query("""
            SELECT id, ticket_no, storage_provider, bucket_name, object_key, mime_type,
                   file_size, content_sha256, expire_at, status
            FROM sys_upload_ticket
            WHERE uploader_type = ? AND uploader_id = ? AND client_request_id = ?
              AND is_deleted = 0
            ORDER BY created_at DESC
            LIMIT 1
            """,
            (rs, rowNum) -> {
                Long uploadTicketId = rs.getLong("id");
                Long attachmentRefId = findAttachmentRefIdByTicket(uploadTicketId);
                if (attachmentRefId != null && "CONFIRMED".equals(rs.getString("status"))) {
                    FileObjectRow fileObject = findActiveFileObject(rs.getString("content_sha256"), rs.getLong("file_size"));
                    if (fileObject != null) {
                        return deduplicatedResponse(uploadTicketId, rs.getString("ticket_no"), fileObject, attachmentRefId);
                    }
                }
                UploadTicketResponse response = new UploadTicketResponse();
                response.setUploadTicketId(uploadTicketId);
                response.setTicketNo(rs.getString("ticket_no"));
                response.setDeduplicated(false);
                response.setAttachmentRefId(null);
                response.setStorageProvider(rs.getString("storage_provider"));
                response.setBucketName(rs.getString("bucket_name"));
                response.setObjectKey(rs.getString("object_key"));
                StorageUploadTarget uploadTarget = storageProviderRegistry.require(rs.getString("storage_provider"))
                    .buildUploadTarget(rs.getString("bucket_name"), rs.getString("object_key"), rs.getString("mime_type"), 60);
                response.setUploadUrl(uploadTarget.uploadUrl());
                response.setHeaders(uploadTarget.headers());
                response.setExpireAt(rs.getTimestamp("expire_at").toLocalDateTime());
                return response;
            },
            uploaderType,
            uploaderId,
            clientRequestId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void increaseFileRefCount(Long fileObjectId) {
        jdbcTemplate.update("UPDATE sys_file_object SET ref_count = ref_count + 1 WHERE id = ?", fileObjectId);
    }

    private String buildObjectKey(String contentSha256, String fileName, Long uploadTicketId) {
        String ext = extractExt(fileName);
        String suffix = StringUtils.hasText(ext) ? "." + ext : "";
        LocalDateTime now = LocalDateTime.now();
        return "uploads/%d/%02d/%02d/%s/%s%s".formatted(
            now.getYear(),
            now.getMonthValue(),
            now.getDayOfMonth(),
            contentSha256.substring(0, 16),
            uploadTicketId,
            suffix
        );
    }

    private String extractExt(String fileName) {
        int dotIndex = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dotIndex + 1).toLowerCase();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private boolean sameId(Long left, Long right) {
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

    private record FileObjectRow(
        Long id,
        String storageProvider,
        String bucketName,
        String objectKey,
        Long fileSize,
        String contentSha256
    ) {}

    private record DownloadTicketRow(
        Long id,
        String ticketNo,
        Long attachmentRefId,
        String requesterType,
        Long requesterId,
        String downloadType,
        LocalDateTime expireAt,
        String status
    ) {}

    private record DownloadAttachmentRow(
        Long attachmentRefId,
        String ownerType,
        String displayName,
        String uploaderType,
        Long uploaderId,
        LocalDateTime deletedAt,
        Integer attachmentDeleted,
        String storageProvider,
        String bucketName,
        String objectKey,
        String mimeType,
        Long fileSize,
        Integer fileDeleted
    ) {}

    private record QuotaPolicyRow(
        Long id,
        String policyCode,
        String policyName,
        String scopeType,
        Long scopeId,
        Long quotaBytes,
        Long cacheQuotaBytes,
        BigDecimal warningThreshold,
        BigDecimal blockThreshold,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    private record UploadPolicyRow(
        Long id,
        String ownerType,
        Long maxFileSizeBytes,
        String allowedExtensions,
        String allowedMimeTypes
    ) {}

    private record AttachmentRow(
        Long id,
        Long fileObjectId,
        String ownerType,
        Long ownerId,
        String displayName,
        String uploaderType,
        Long uploaderId,
        LocalDateTime deletedAt,
        LocalDateTime purgeAfter,
        LocalDateTime createdAt,
        Integer isDeleted,
        String mimeType,
        Long fileSize,
        String contentSha256,
        String scanStatus
    ) {}

}
