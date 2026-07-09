package com.workforcehub.workflow.dto;

import lombok.Data;

/**
 * 审批申请附件响应。
 */
@Data
public class WorkflowRequestAttachmentResponse {

    private Long attachmentRefId;
    private String displayName;
    private String mimeType;
    private Long fileSize;
    private String contentSha256;
    private String scanStatus;
    private String scanStatusName;
}
