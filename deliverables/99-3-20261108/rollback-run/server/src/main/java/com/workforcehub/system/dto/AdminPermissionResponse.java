package com.workforcehub.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台权限点响应。
 */
@Data
public class AdminPermissionResponse {

    private Long permissionId;
    private String permCode;
    private String permName;
    private String moduleCode;
    private String moduleName;
    private String actionCode;
    private String actionName;
    private String status;
    private String statusName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
