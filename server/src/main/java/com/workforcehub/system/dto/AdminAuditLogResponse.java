package com.workforcehub.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台审计日志响应。
 */
@Data
public class AdminAuditLogResponse {

    private Long auditLogId;
    private String operatorType;
    private String operatorTypeName;
    private Long operatorId;
    private String operatorName;
    private String operatorAccount;
    private String actionType;
    private String actionTypeName;
    private String targetType;
    private String targetTypeName;
    private Long targetId;
    private String targetDisplayName;
    private String targetNo;
    private String result;
    private String resultName;
    private String detailJson;
    private LocalDateTime occurredAt;
}
