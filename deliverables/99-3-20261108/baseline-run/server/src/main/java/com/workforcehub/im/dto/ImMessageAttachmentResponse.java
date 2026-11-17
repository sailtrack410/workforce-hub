package com.workforcehub.im.dto;

import lombok.Data;

/**
 * 员工端 IM 消息附件响应。
 */
@Data
public class ImMessageAttachmentResponse {

    private Long attachmentRefId;
    private String displayName;
    private String mimeType;
    private Long fileSize;
    private String contentSha256;
    private String scanStatus;
    private String scanStatusName;
}
