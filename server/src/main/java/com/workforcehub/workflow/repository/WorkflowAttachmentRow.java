package com.workforcehub.workflow.repository;

import java.time.LocalDateTime;

/**
 * 审批申请附件读模型。
 */
public record WorkflowAttachmentRow(
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
