package com.workforcehub.file.repository;

import java.time.LocalDateTime;

public record DownloadAttachmentRow(
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
