package com.workforcehub.im.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工端群文件响应。
 */
@Data
public class ImGroupFileResponse {

    private Long attachmentRefId;
    private Long conversationId;
    private String displayName;
    private String mimeType;
    private Long fileSize;
    private String contentSha256;
    private String scanStatus;
    private String scanStatusName;
    private Long uploaderId;
    private String uploaderName;
    private LocalDateTime createdAt;
    private Boolean canDelete;
}
