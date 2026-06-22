package com.workforcehub.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台角色响应。
 */
@Data
public class AdminRoleResponse {

    private Long roleId;
    private String roleCode;
    private String roleName;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
