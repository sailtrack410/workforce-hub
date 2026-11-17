package com.workforcehub.auth.controller;

import com.workforcehub.auth.dto.RbacDtos.AdminUserRoleResponse;
import com.workforcehub.auth.dto.RbacDtos.AssignAdminRolesRequest;
import com.workforcehub.auth.dto.RbacDtos.AssignRolePermissionsRequest;
import com.workforcehub.auth.dto.RbacDtos.PermissionResponse;
import com.workforcehub.auth.dto.RbacDtos.RoleResponse;
import com.workforcehub.auth.dto.RbacDtos.RoleSaveRequest;
import com.workforcehub.auth.service.RbacService;
import com.workforcehub.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台角色与权限管理接口。
 */
// RBAC 接口：角色 CRUD、权限列表、角色权限分配、用户角色绑定
@RestController
@RequestMapping("/api/admin/rbac")
public class AdminRbacController {

    private final RbacService rbacService;

    public AdminRbacController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @Operation(summary = "查询权限列表", operationId = "listRbacPermissions")
    @GetMapping("/permissions")
    public R<List<PermissionResponse>> permissions() {
        return R.ok(rbacService.listPermissions());
    }

    @Operation(summary = "查询角色列表", operationId = "listRbacRoles")
    @GetMapping("/roles")
    public R<List<RoleResponse>> roles(@RequestParam(required = false) String keyword) {
        return R.ok(rbacService.listRoles(keyword));
    }

    @Operation(summary = "创建角色", operationId = "createRbacRole")
    @PostMapping("/roles")
    public R<RoleResponse> createRole(@Valid @RequestBody RoleSaveRequest request) {
        return R.ok(rbacService.createRole(request));
    }

    @Operation(summary = "查询角色详情", operationId = "getRbacRole")
    @GetMapping("/roles/{roleId}")
    public R<RoleResponse> role(@PathVariable Long roleId) {
        return R.ok(rbacService.getRole(roleId));
    }

    @Operation(summary = "更新角色", operationId = "updateRbacRole")
    @PutMapping("/roles/{roleId}")
    public R<RoleResponse> updateRole(@PathVariable Long roleId,
                                      @Valid @RequestBody RoleSaveRequest request) {
        return R.ok(rbacService.updateRole(roleId, request));
    }

    @Operation(summary = "删除角色", operationId = "deleteRbacRole")
    @DeleteMapping("/roles/{roleId}")
    public R<Void> deleteRole(@PathVariable Long roleId) {
        rbacService.deleteRole(roleId);
        return R.ok();
    }

    @Operation(summary = "分配角色权限", operationId = "assignRbacRolePermissions")
    @PutMapping("/roles/{roleId}/permissions")
    public R<RoleResponse> assignRolePermissions(@PathVariable Long roleId,
                                                 @Valid @RequestBody AssignRolePermissionsRequest request) {
        return R.ok(rbacService.assignRolePermissions(roleId, request));
    }

    @Operation(summary = "查询管理员用户列表", operationId = "listRbacAdminUsers")
    @GetMapping("/admin-users")
    public R<List<AdminUserRoleResponse>> adminUsers() {
        return R.ok(rbacService.listAdminUsers());
    }

    @Operation(summary = "查询管理员用户角色", operationId = "getRbacAdminUserRoles")
    @GetMapping("/admin-users/{adminUserId}/roles")
    public R<AdminUserRoleResponse> adminUserRoles(@PathVariable Long adminUserId) {
        return R.ok(rbacService.getAdminUserRoles(adminUserId));
    }

    @Operation(summary = "分配管理员用户角色", operationId = "assignRbacAdminUserRoles")
    @PutMapping("/admin-users/{adminUserId}/roles")
    public R<AdminUserRoleResponse> assignAdminRoles(@PathVariable Long adminUserId,
                                                    @Valid @RequestBody AssignAdminRolesRequest request) {
        return R.ok(rbacService.assignAdminRoles(adminUserId, request));
    }
}
