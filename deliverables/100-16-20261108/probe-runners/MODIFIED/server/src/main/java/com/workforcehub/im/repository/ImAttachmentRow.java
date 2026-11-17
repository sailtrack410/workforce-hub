package com.workforcehub.im.repository;

import java.time.LocalDateTime;

/**
 * IM 消息附件读模型。
 */
public record ImAttachmentRow(
    Long attachmentRefId,
    Long fileObjectId,
    String ownerType,
    Long ownerId,
    String displayName,
    String uploaderType,
    Long uploaderId,
    LocalDateTime deletedAt,
    Integer attachmentDeleted,
    Integer fileDeleted,
    String mimeType,
    Long fileSize,
    String contentSha256,
    String scanStatus
) {
}
