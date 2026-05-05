package com.workforcehub.file.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminFileResponse {

    private Long attachmentRefId;
    private Long fileObjectId;
    private String ownerType;
    private Long ownerId;
    private String originalFileName;
    private String displayName;
    private String uploaderType;
    private Long uploaderId;
    private String mimeType;
    private Long fileSize;
    private String scanStatus;
    private LocalDateTime deletedAt;
    private LocalDateTime purgeAfter;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
