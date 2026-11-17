package com.workforcehub.common.event.file;

import com.workforcehub.common.event.DomainEvent;

import java.time.LocalDateTime;

/**
 * 附件引用创建事件。
 */
public record FileUploadedEvent(
    Long attachmentRefId,
    String ownerType,
    Long ownerId,
    String displayName,
    String mimeType,
    Long fileSize,
    String uploaderType,
    Long uploaderId,
    String scanStatus,
    Long operatorId,
    LocalDateTime occurredAt
) implements DomainEvent {

    public static final String EVENT_NAME = "file.uploaded";

    @Override
    public String eventName() {
        return EVENT_NAME;
    }
}
