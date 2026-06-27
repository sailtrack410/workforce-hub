package com.workforcehub.file.service;

import com.workforcehub.common.util.IdGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.audit.AuditLogWriter;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.common.util.TextValues;
import com.workforcehub.file.config.FileStorageProperties;
import com.workforcehub.file.dto.CreateUploadTicketRequest;
import com.workforcehub.file.dto.UploadTicketResponse;
import com.workforcehub.file.repository.AttachmentRefRepository;
import com.workforcehub.file.repository.FileObjectRepository;
import com.workforcehub.file.repository.FileObjectRow;
import com.workforcehub.file.storage.StorageProvider;
import com.workforcehub.file.storage.StorageProviderRegistry;
import com.workforcehub.file.storage.StorageUploadTarget;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 文件上传票据服务。
 */
@Service
public class FileUploadTicketService {

    private final JdbcTemplate jdbcTemplate;
    private final StorageProviderRegistry storageProviderRegistry;
    private final FileStorageProperties fileStorageProperties;
    private final ObjectMapper objectMapper;
    private final StorageUsageLedgerService storageUsageLedgerService;
    private final FileObjectRepository fileObjectRepository;
    private final AttachmentRefRepository attachmentRefRepository;
    private final FileQuotaPolicyService fileQuotaPolicyService;
    private final FileUploadRules fileUploadRules;
    private final AuditLogWriter auditLogWriter;

    public FileUploadTicketService(JdbcTemplate jdbcTemplate,
                                   StorageProviderRegistry storageProviderRegistry,
                                   FileStorageProperties fileStorageProperties,
                                   ObjectMapper objectMapper,
                                   StorageUsageLedgerService storageUsageLedgerService,
                                   FileObjectRepository fileObjectRepository,
                                   AttachmentRefRepository attachmentRefRepository,
                                   FileQuotaPolicyService fileQuotaPolicyService,
                                   FileUploadRules fileUploadRules,
                                   AuditLogWriter auditLogWriter) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageProviderRegistry = storageProviderRegistry;
        this.fileStorageProperties = fileStorageProperties;
        this.objectMapper = objectMapper;
        this.storageUsageLedgerService = storageUsageLedgerService;
        this.fileObjectRepository = fileObjectRepository;
        this.attachmentRefRepository = attachmentRefRepository;
        this.fileQuotaPolicyService = fileQuotaPolicyService;
        this.fileUploadRules = fileUploadRules;
        this.auditLogWriter = auditLogWriter;
    }

    @Transactional(rollbackFor = Exception.class)
    public UploadTicketResponse createEmployeeUploadTicket(CreateUploadTicketRequest request) {
        Long userId = CurrentUserHolder.requireUserId();
        fileUploadRules.validateEmployeeOwner(request.getOwnerType(), request.getOwnerId());
        UploadTicketResponse existingTicket = findUploadTicketByClientRequestId("USER", userId, request.getClientRequestId());
        if (existingTicket != null) {
            return existingTicket;
        }
        ensureUploadPolicy(request);
        fileUploadRules.ensureEmployeeQuota(request.getOwnerType(), userId, request.getFileSize());
        fileQuotaPolicyService.ensureGlobalQuota(request.getFileSize());

        FileObjectRow existing = fileObjectRepository.findReusableForUploader(
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
                UploadTicketResponse concurrentTicket = findUploadTicketByClientRequestId(
                    "USER",
                    userId,
                    request.getClientRequestId()
                );
                if (concurrentTicket != null) {
                    return concurrentTicket;
                }
                throw e;
            }
            fileObjectRepository.increaseRefCount(existing.id());
            fileUploadRules.increaseEmployeeUsage(request.getOwnerType(), userId, request.getFileSize());
            storageUsageLedgerService.increaseUsage("GLOBAL", null, request.getFileSize(), 1);
            auditLogWriter.write("USER", userId, "FILE_UPLOAD_DEDUP", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
                "{\"fileObjectId\":%s}".formatted(existing.id()));
            return deduplicatedResponse(deduplicatedTicketId, "UP" + deduplicatedTicketId, existing, attachmentRefId);
        }

        return createPendingUploadTicket("USER", userId, request, "FILE_UPLOAD_TICKET_CREATED");
    }

    @Transactional(rollbackFor = Exception.class)
    public UploadTicketResponse createAdminUploadTicket(CreateUploadTicketRequest request) {
        Long adminId = CurrentUserHolder.requireUserId();
        fileUploadRules.validateAdminOwner(request.getOwnerType(), request.getOwnerId());
        UploadTicketResponse existingTicket = findUploadTicketByClientRequestId("ADMIN", adminId, request.getClientRequestId());
        if (existingTicket != null) {
            return existingTicket;
        }
        ensureUploadPolicy(request);
        fileQuotaPolicyService.ensureGlobalQuota(request.getFileSize());

        FileObjectRow existing = fileObjectRepository.findReusableForUploader(
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
            fileObjectRepository.increaseRefCount(existing.id());
            storageUsageLedgerService.increaseUsage("GLOBAL", null, request.getFileSize(), 1);
            auditLogWriter.write("ADMIN", adminId, "FILE_ADMIN_UPLOAD_DEDUP", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
                "{\"fileObjectId\":%s}".formatted(existing.id()));
            return deduplicatedResponse(deduplicatedTicketId, "UP" + deduplicatedTicketId, existing, attachmentRefId);
        }

        return createPendingUploadTicket("ADMIN", adminId, request, "FILE_ADMIN_UPLOAD_TICKET_CREATED");
    }

    private UploadTicketResponse createPendingUploadTicket(String uploaderType,
                                                           Long uploaderId,
                                                           CreateUploadTicketRequest request,
                                                           String auditAction) {
        Long ticketId = IdGenerator.nextId();
        String ticketNo = "UP" + ticketId;
        String objectKey = buildObjectKey(request.getContentSha256(), request.getFileName(), ticketId);
        String providerCode = fileStorageProperties.getStorageProvider();
        StorageProvider provider = storageProviderRegistry.require(providerCode);
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
                    (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                """,
                ticketId,
                ticketNo,
                uploaderType,
                uploaderId,
                request.getOwnerType(),
                request.getOwnerId(),
                providerCode,
                bucketName,
                request.getFileName(),
                request.getMimeType(),
                request.getFileSize(),
                TextValues.blankToNull(request.getContentMd5()),
                request.getContentSha256().toLowerCase(),
                objectKey,
                TextValues.blankToNull(request.getClientRequestId()),
                uploadTarget.expireAt(),
                uploaderId
            );
        } catch (DuplicateKeyException e) {
            UploadTicketResponse concurrentTicket = findUploadTicketByClientRequestId(
                uploaderType,
                uploaderId,
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
        response.setStorageProvider(providerCode);
        response.setBucketName(bucketName);
        response.setObjectKey(objectKey);
        response.setUploadUrl(uploadTarget.uploadUrl());
        response.setHeaders(uploadTarget.headers());
        response.setExpireAt(uploadTarget.expireAt());
        auditLogWriter.write(uploaderType, uploaderId, auditAction, "UPLOAD_TICKET", ticketId, "SUCCESS",
            "{\"objectKey\":\"%s\"}".formatted(objectKey));
        return response;
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

    private Long createConfirmedDeduplicatedUploadTicket(String uploaderType,
                                                         Long uploaderId,
                                                         CreateUploadTicketRequest request,
                                                         FileObjectRow fileObject) {
        Long ticketId = IdGenerator.nextId();
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
            TextValues.blankToNull(request.getContentMd5()),
            request.getContentSha256().toLowerCase(),
            fileObject.objectKey(),
            TextValues.blankToNull(request.getClientRequestId()),
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
            uploaderId
        );
        return attachmentRefId;
    }

    private UploadTicketResponse findUploadTicketByClientRequestId(String uploaderType,
                                                                   Long uploaderId,
                                                                   String clientRequestId) {
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
                Long attachmentRefId = attachmentRefRepository.findIdByUploadTicket(uploadTicketId);
                if (attachmentRefId != null && "CONFIRMED".equals(rs.getString("status"))) {
                    FileObjectRow fileObject = fileObjectRepository.findActiveBySha256AndSize(
                        rs.getString("content_sha256"),
                        rs.getLong("file_size")
                    );
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

    private UploadTicketResponse deduplicatedResponse(Long uploadTicketId,
                                                      String ticketNo,
                                                      FileObjectRow fileObject,
                                                      Long attachmentRefId) {
        UploadTicketResponse response = new UploadTicketResponse();
        response.setUploadTicketId(uploadTicketId);
        response.setTicketNo(ticketNo);
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

    private record UploadPolicyRow(
        Long id,
        String ownerType,
        Long maxFileSizeBytes,
        String allowedExtensions,
        String allowedMimeTypes
    ) {}
}
