package com.workforcehub.im.repository;

import java.time.LocalDateTime;

public record ImGroupFileRow(
    Long attachmentRefId,
    Long fileObjectId,
    Long conversationId,
    String displayName,
    String uploaderType,
    Long uploaderId,
    String uploaderName,
    LocalDateTime deletedAt,
    Integer attachmentDeleted,
    Integer fileDeleted,
    String mimeType,
    Long fileSize,
    String contentSha256,
    String scanStatus,
    LocalDateTime createdAt
) {
}
