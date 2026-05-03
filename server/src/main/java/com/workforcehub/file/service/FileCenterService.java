package com.workforcehub.file.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.file.dto.AttachmentRefResponse;
import com.workforcehub.file.dto.ConfirmUploadRequest;
import com.workforcehub.file.dto.CreateDownloadTicketRequest;
import com.workforcehub.file.dto.CreateUploadTicketRequest;
import com.workforcehub.file.dto.DownloadTicketResponse;
import com.workforcehub.file.dto.FilePurgeResponse;
import com.workforcehub.file.dto.FileRestoreResponse;
import com.workforcehub.file.dto.FileTrashRequest;
import com.workforcehub.file.dto.FileTrashResponse;
import com.workforcehub.file.dto.QuotaResponse;
import com.workforcehub.file.dto.StorageSummaryResponse;
import com.workforcehub.file.dto.UploadTicketResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 文件中心第一版服务。
 * 当前先落 LOCAL 主链路，Provider 字段保留 MINIO 扩展位。
 */
@Service
public class FileCenterService {

    private static final String PROVIDER_LOCAL = "LOCAL";
    private static final String BUCKET_LOCAL = "local";
    private static final long DEFAULT_PERSONAL_QUOTA_BYTES = 10L * 1024L * 1024L * 1024L;
    private static final long DEFAULT_GLOBAL_QUOTA_BYTES = 400L * 1024L * 1024L * 1024L;

    private final JdbcTemplate jdbcTemplate;
    private final Path localRoot;

    public FileCenterService(JdbcTemplate jdbcTemplate,
                             @Value("${workforce.file.local-root:target/test-file-storage}") String localRoot) {
        this.jdbcTemplate = jdbcTemplate;
        this.localRoot = Path.of(localRoot);
    }

    @Transactional(rollbackFor = Exception.class)
    public UploadTicketResponse createEmployeeUploadTicket(CreateUploadTicketRequest request) {
        Long userId = CurrentUserHolder.requireUserId();
        validateOwnerType(request.getOwnerType());
        ensurePersonalQuota(userId, request.getFileSize());
        ensureGlobalQuota(request.getFileSize());
        UploadTicketResponse existingTicket = findUploadTicketByClientRequestId(userId, request.getClientRequestId());
        if (existingTicket != null) {
            return existingTicket;
        }

        FileObjectRow existing = findActiveFileObject(request.getContentSha256(), request.getFileSize());
        if (existing != null) {
            Long attachmentRefId = createAttachmentRef(
                request.getOwnerType(),
                request.getOwnerId(),
                existing.id(),
                null,
                request.getFileName(),
                request.getFileName(),
                "USER",
                userId
            );
            increaseFileRefCount(existing.id());
            increaseUsage("USER", userId, request.getFileSize(), 1);
            increaseUsage("GLOBAL", null, request.getFileSize(), 1);
            audit("USER", userId, "FILE_UPLOAD_DEDUP", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
                "{\"fileObjectId\":%s}".formatted(existing.id()));
            return deduplicatedResponse(existing, attachmentRefId);
        }

        Long ticketId = IdWorker.getId();
        String ticketNo = "UP" + ticketId;
        String objectKey = buildObjectKey(request.getContentSha256(), request.getFileName());
        LocalDateTime expireAt = LocalDateTime.now().plusHours(1);

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
            BUCKET_LOCAL,
            request.getFileName(),
            request.getMimeType(),
            request.getFileSize(),
            blankToNull(request.getContentMd5()),
            request.getContentSha256().toLowerCase(),
            objectKey,
            blankToNull(request.getClientRequestId()),
            expireAt,
            userId
        );

        UploadTicketResponse response = new UploadTicketResponse();
        response.setUploadTicketId(ticketId);
        response.setTicketNo(ticketNo);
        response.setDeduplicated(false);
        response.setAttachmentRefId(null);
        response.setStorageProvider(PROVIDER_LOCAL);
        response.setBucketName(BUCKET_LOCAL);
        response.setObjectKey(objectKey);
        response.setUploadUrl(localRoot.resolve(objectKey).toString());
        response.setHeaders(Map.of("Content-Type", request.getMimeType()));
        response.setExpireAt(expireAt);
        audit("USER", userId, "FILE_UPLOAD_TICKET_CREATED", "UPLOAD_TICKET", ticketId, "SUCCESS",
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
        if ("CONFIRMED".equals(ticket.status())) {
            Long existingRefId = findAttachmentRefIdByTicket(ticket.id());
            if (existingRefId != null) {
                return buildAttachmentResponse(existingRefId, false);
            }
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

        verifyLocalObject(request.getObjectKey(), request.getContentSha256(), request.getFileSize());
        ensurePersonalQuota(userId, request.getFileSize());
        ensureGlobalQuota(request.getFileSize());

        boolean deduplicated = false;
        FileObjectRow fileObject = findActiveFileObject(request.getContentSha256(), request.getFileSize());
        if (fileObject == null) {
            fileObject = createFileObject(ticket, request, userId);
        } else {
            deduplicated = true;
        }

        Long attachmentRefId = createAttachmentRef(
            request.getOwnerType(),
            request.getOwnerId(),
            fileObject.id(),
            ticket.id(),
            ticket.fileName(),
            request.getDisplayName(),
            "USER",
            userId
        );
        increaseFileRefCount(fileObject.id());
        increaseUsage("USER", userId, request.getFileSize(), 1);
        increaseUsage("GLOBAL", null, request.getFileSize(), 1);
        jdbcTemplate.update(
            "UPDATE sys_upload_ticket SET status = 'CONFIRMED', confirmed_at = NOW(), updated_at = NOW() WHERE id = ?",
            ticket.id()
        );
        audit("USER", userId, "FILE_UPLOAD_CONFIRMED", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
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

    public QuotaResponse getEmployeeQuota() {
        Long userId = CurrentUserHolder.requireUserId();
        long personalQuota = resolveQuota("USER", userId, resolveQuota("USER_DEFAULT", null, DEFAULT_PERSONAL_QUOTA_BYTES));
        long personalUsed = resolveUsedBytes("USER", userId);
        long globalQuota = resolveQuota("GLOBAL", null, DEFAULT_GLOBAL_QUOTA_BYTES);
        long globalUsed = resolveUsedBytes("GLOBAL", null);
        long cacheQuota = resolveQuota("TEMP_CACHE", null, 50L * 1024L * 1024L * 1024L);
        long cacheUsed = resolveCacheUsedBytes("TEMP_CACHE", null);

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
        long globalQuota = resolveQuota("GLOBAL", null, DEFAULT_GLOBAL_QUOTA_BYTES);
        long globalUsed = resolveUsedBytes("GLOBAL", null);
        long cacheQuota = resolveQuota("TEMP_CACHE", null, 50L * 1024L * 1024L * 1024L);
        long cacheUsed = resolveCacheUsedBytes("TEMP_CACHE", null);

        StorageSummaryResponse response = new StorageSummaryResponse();
        response.setOrgPoolQuotaBytes(globalQuota);
        response.setOrgPoolUsedBytes(globalUsed);
        response.setOrgPoolWarningThreshold(BigDecimal.valueOf(0.8));
        response.setOrgPoolBlockThreshold(BigDecimal.ONE);
        response.setCacheQuotaBytes(cacheQuota);
        response.setCacheUsedBytes(cacheUsed);
        response.setBackendFreeBytes(localRoot.toFile().getFreeSpace());
        response.setBackendWritable(localRoot.toFile().getFreeSpace() > 0 && globalUsed < globalQuota);
        response.setWriteProtectReason(globalUsed >= globalQuota ? "企业文件总空间不足" : null);
        response.setFileObjectCount(countLong("SELECT COUNT(*) FROM sys_file_object WHERE is_deleted = 0"));
        response.setAttachmentRefCount(countLong("SELECT COUNT(*) FROM sys_attachment_ref WHERE is_deleted = 0"));
        response.setRecycleBinCount(countLong("SELECT COUNT(*) FROM sys_attachment_ref WHERE deleted_at IS NOT NULL AND is_deleted = 0"));
        response.setPendingCleanupObjectCount(countLong("SELECT COUNT(*) FROM sys_file_object WHERE cleanup_after IS NOT NULL AND is_deleted = 0"));
        return response;
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
        jdbcTemplate.update("""
            UPDATE sys_attachment_ref
            SET deleted_at = ?, deleted_by = ?, purge_after = ?, updated_by = ?
            WHERE id = ? AND is_deleted = 0
            """, deletedAt, userId, purgeAfter, userId, attachmentRefId);

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
        jdbcTemplate.update("""
            UPDATE sys_attachment_ref
            SET deleted_at = NULL, deleted_by = NULL, purge_after = NULL, updated_by = ?
            WHERE id = ? AND is_deleted = 0
            """, userId, attachmentRefId);

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
        jdbcTemplate.update(
            "UPDATE sys_attachment_ref SET is_deleted = 1, updated_by = ? WHERE id = ? AND is_deleted = 0",
            userId,
            attachmentRefId
        );
        releaseUsage("USER", userId, attachment.fileSize(), 1);
        releaseUsage("GLOBAL", null, attachment.fileSize(), 1);
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

    private void validateOwnerType(String ownerType) {
        if (!StringUtils.hasText(ownerType)) {
            throw new BizException(400, "文件归属类型不能为空");
        }
        if (!List.of("PERSONAL_FILE", "IM_MESSAGE", "IM_GROUP_FILE", "DOC_DOCUMENT", "KB_DOCUMENT", "WF_REQUEST", "AGENT_TEMP")
            .contains(ownerType)) {
            throw new BizException(400, "不支持的文件归属类型");
        }
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

    private void verifyLocalObject(String objectKey, String expectedSha256, Long expectedSize) {
        Path path = localRoot.resolve(objectKey);
        if (!Files.exists(path)) {
            throw new BizException(422, "文件对象不存在，请重新上传");
        }
        try {
            if (Files.size(path) != expectedSize) {
                throw new BizException(422, "文件校验失败，请重新上传");
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = Files.newInputStream(path)) {
                inputStream.transferTo(new DigestOutputStream(digest));
            }
            String actualSha256 = HexFormat.of().formatHex(digest.digest());
            if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
                throw new BizException(422, "文件校验失败，请重新上传");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "文件服务异常，请稍后重试");
        }
    }

    private void ensureUploaderOwnsAttachment(Long userId, AttachmentRow attachment) {
        if (!"USER".equals(attachment.uploaderType()) || !userId.equals(attachment.uploaderId())) {
            throw new BizException(403, "无权访问该文件");
        }
    }

    private void ensurePersonalQuota(Long userId, Long incomingBytes) {
        long quota = resolveQuota("USER", userId, resolveQuota("USER_DEFAULT", null, DEFAULT_PERSONAL_QUOTA_BYTES));
        long used = resolveUsedBytes("USER", userId);
        if (used + incomingBytes > quota) {
            throw new BizException(507, "个人云空间不足，请清理文件或联系管理员扩容");
        }
    }

    private void ensureGlobalQuota(Long incomingBytes) {
        long quota = resolveQuota("GLOBAL", null, DEFAULT_GLOBAL_QUOTA_BYTES);
        long used = resolveUsedBytes("GLOBAL", null);
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

    private long resolveUsedBytes(String scopeType, Long scopeId) {
        Long used = jdbcTemplate.query("""
            SELECT used_bytes FROM sys_storage_usage
            WHERE scope_type = ? AND ((scope_id IS NULL AND ? IS NULL) OR scope_id = ?)
            """,
            rs -> rs.next() ? rs.getLong("used_bytes") : null,
            scopeType,
            scopeId,
            scopeId
        );
        return used == null ? 0L : used;
    }

    private long resolveCacheUsedBytes(String scopeType, Long scopeId) {
        Long used = jdbcTemplate.query("""
            SELECT cache_used_bytes FROM sys_storage_usage
            WHERE scope_type = ? AND ((scope_id IS NULL AND ? IS NULL) OR scope_id = ?)
            """,
            rs -> rs.next() ? rs.getLong("cache_used_bytes") : null,
            scopeType,
            scopeId,
            scopeId
        );
        return used == null ? 0L : used;
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

    private void increaseUsage(String scopeType, Long scopeId, Long bytes, int fileCount) {
        Long existingId = jdbcTemplate.query("""
            SELECT id FROM sys_storage_usage
            WHERE scope_type = ? AND ((scope_id IS NULL AND ? IS NULL) OR scope_id = ?)
            """,
            rs -> rs.next() ? rs.getLong("id") : null,
            scopeType,
            scopeId,
            scopeId
        );
        if (existingId == null) {
            jdbcTemplate.update("""
                INSERT INTO sys_storage_usage
                    (id, scope_type, scope_id, used_bytes, cache_used_bytes, file_count)
                VALUES
                    (?, ?, ?, ?, 0, ?)
                """, IdWorker.getId(), scopeType, scopeId, bytes, fileCount);
        } else {
            jdbcTemplate.update("""
                UPDATE sys_storage_usage
                SET used_bytes = used_bytes + ?, file_count = file_count + ?
                WHERE id = ?
                """, bytes, fileCount, existingId);
        }
    }

    private void releaseUsage(String scopeType, Long scopeId, Long bytes, int fileCount) {
        jdbcTemplate.update("""
            UPDATE sys_storage_usage
            SET used_bytes = GREATEST(used_bytes - ?, 0),
                file_count = GREATEST(file_count - ?, 0)
            WHERE scope_type = ? AND ((scope_id IS NULL AND ? IS NULL) OR scope_id = ?)
            """,
            bytes,
            fileCount,
            scopeType,
            scopeId,
            scopeId
        );
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

    private AttachmentRow getAttachmentOrThrow(Long attachmentRefId) {
        List<AttachmentRow> rows = jdbcTemplate.query("""
            SELECT ar.id, ar.file_object_id, ar.display_name, ar.uploader_type, ar.uploader_id,
                   ar.deleted_at, ar.purge_after, ar.created_at, ar.is_deleted,
                   fo.mime_type, fo.file_size, fo.content_sha256, fo.scan_status
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE ar.id = ?
            """,
            (rs, rowNum) -> new AttachmentRow(
                rs.getLong("id"),
                rs.getLong("file_object_id"),
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
        if (!StringUtils.hasText(clientRequestId)) {
            return null;
        }
        List<UploadTicketResponse> rows = jdbcTemplate.query("""
            SELECT id, ticket_no, storage_provider, bucket_name, object_key, mime_type, expire_at
            FROM sys_upload_ticket
            WHERE uploader_type = 'USER' AND uploader_id = ? AND client_request_id = ?
              AND is_deleted = 0
            ORDER BY created_at DESC
            LIMIT 1
            """,
            (rs, rowNum) -> {
                UploadTicketResponse response = new UploadTicketResponse();
                response.setUploadTicketId(rs.getLong("id"));
                response.setTicketNo(rs.getString("ticket_no"));
                response.setDeduplicated(false);
                response.setAttachmentRefId(null);
                response.setStorageProvider(rs.getString("storage_provider"));
                response.setBucketName(rs.getString("bucket_name"));
                response.setObjectKey(rs.getString("object_key"));
                response.setUploadUrl(localRoot.resolve(rs.getString("object_key")).toString());
                response.setHeaders(Map.of("Content-Type", rs.getString("mime_type")));
                response.setExpireAt(rs.getTimestamp("expire_at").toLocalDateTime());
                return response;
            },
            userId,
            clientRequestId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void increaseFileRefCount(Long fileObjectId) {
        jdbcTemplate.update("UPDATE sys_file_object SET ref_count = ref_count + 1 WHERE id = ?", fileObjectId);
    }

    private String buildObjectKey(String contentSha256, String fileName) {
        String ext = extractExt(fileName);
        String suffix = StringUtils.hasText(ext) ? "." + ext : "";
        LocalDateTime now = LocalDateTime.now();
        return "uploads/%d/%02d/%02d/%s%s".formatted(
            now.getYear(),
            now.getMonthValue(),
            now.getDayOfMonth(),
            contentSha256.substring(0, 16),
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

    private record AttachmentRow(
        Long id,
        Long fileObjectId,
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

    private static final class DigestOutputStream extends java.io.OutputStream {
        private final MessageDigest digest;

        private DigestOutputStream(MessageDigest digest) {
            this.digest = digest;
        }

        @Override
        public void write(int b) {
            digest.update((byte) b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            digest.update(b, off, len);
        }
    }
}
