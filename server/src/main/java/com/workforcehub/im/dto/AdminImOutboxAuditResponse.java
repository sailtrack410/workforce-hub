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
    private String operatorTypeName;
    private Long operatorId;
    private String operatorName;
    private String actionType;
    private String actionTypeName;
    private String targetType;
    private String targetTypeName;
    private Long targetId;
    private String targetDisplayName;
    private String result;
    private String resultName;
    private String detailJson;
    private LocalDateTime occurredAt;
}
