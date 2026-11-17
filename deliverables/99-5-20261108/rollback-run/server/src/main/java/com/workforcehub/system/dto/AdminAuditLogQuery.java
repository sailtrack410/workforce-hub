package com.workforcehub.system.dto;

import lombok.Data;

/**
 * 后台审计日志查询。
 */
@Data
public class AdminAuditLogQuery {

    private Long pageNo = 1L;
    private Long pageSize = 20L;
    private String operatorType;
    private String actionType;
    private String targetType;
}
