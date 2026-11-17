package com.workforcehub.file.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AttachmentRefResponse {

    private Long attachmentRefId;
    private Long fileObjectId;
    private String displayName;
    private String mimeType;
    private Long fileSize;
    private String contentSha256;
    private Boolean deduplicated;
    private String scanStatus;
    private String scanStatusName;
    private LocalDateTime createdAt;
}
