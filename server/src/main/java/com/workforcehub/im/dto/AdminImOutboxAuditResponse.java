package com.workforcehub.im.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台 IM outbox 操作审计响应。
 */
@Data
public class AdminImOutboxAuditResponse {

    private Long auditId;
    private String operatorType;
    private Long operatorId;
    private String actionType;
    private String targetType;
    private Long targetId;
    private String result;
    private String detailJson;
    private LocalDateTime occurredAt;
}
