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
    private Long operatorId;
    private String actionType;
    private String targetType;
    private Long targetId;
    private String result;
    private String detailJson;
    private LocalDateTime occurredAt;
}
