package com.workforcehub.file.repository;

import java.time.LocalDateTime;

public record AttachmentRow(
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
