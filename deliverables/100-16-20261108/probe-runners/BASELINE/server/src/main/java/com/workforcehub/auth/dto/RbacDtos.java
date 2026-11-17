package com.workforcehub.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台 RBAC DTO。
 */
public final class RbacDtos {

    private RbacDtos() {}

    @Data
    public static class RoleSaveRequest {
        @NotBlank(message = "角色编码不能为空")
        @Size(max = 64, message = "角色编码不能超过64个字符")
        private String roleCode;

        @NotBlank(message = "角色名称不能为空")
        @Size(max = 128, message = "角色名称不能超过128个字符")
        private String roleName;

        @Size(max = 32, message = "状态不能超过32个字符")
        private String status;
    }

    @Data
    public static class AssignRolePermissionsRequest {
        @NotNull(message = "权限ID不能为空")
        private List<Long> permissionIds;
    }

    @Data
    public static class AssignAdminRolesRequest {
        @NotNull(message = "角色ID不能为空")
        private List<Long> roleIds;
    }

    @Data
    public static class PermissionResponse {
        private Long permissionId;
        private String permCode;
        private String permName;
        private String moduleCode;
        private String actionCode;
        private String status;
    }

    @Data
    public static class RoleResponse {
        private Long roleId;
        private String roleCode;
        private String roleName;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<PermissionResponse> permissions;
    }

    @Data
    public static class AdminUserRoleResponse {
        private Long adminUserId;
        private String username;
        private String displayName;
        private List<RoleResponse> roles;
    }
}
