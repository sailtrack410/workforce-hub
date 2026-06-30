package com.workforcehub.auth.controller;

import com.workforcehub.auth.dto.RbacDtos.AdminUserRoleResponse;
import com.workforcehub.auth.dto.RbacDtos.AssignAdminRolesRequest;
import com.workforcehub.auth.dto.RbacDtos.AssignRolePermissionsRequest;
import com.workforcehub.auth.dto.RbacDtos.PermissionResponse;
import com.workforcehub.auth.dto.RbacDtos.RoleResponse;
import com.workforcehub.auth.dto.RbacDtos.RoleSaveRequest;
import com.workforcehub.auth.service.RbacService;
import com.workforcehub.common.response.R;
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
@RestController
@RequestMapping("/api/admin/rbac")
public class AdminRbacController {

    private final RbacService rbacService;

    public AdminRbacController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @GetMapping("/permissions")
    public R<List<PermissionResponse>> permissions() {
        return R.ok(rbacService.listPermissions());
    }

    @GetMapping("/roles")
    public R<List<RoleResponse>> roles(@RequestParam(required = false) String keyword) {
        return R.ok(rbacService.listRoles(keyword));
    }

    @PostMapping("/roles")
    public R<RoleResponse> createRole(@Valid @RequestBody RoleSaveRequest request) {
        return R.ok(rbacService.createRole(request));
    }

    @GetMapping("/roles/{roleId}")
    public R<RoleResponse> role(@PathVariable Long roleId) {
        return R.ok(rbacService.getRole(roleId));
    }

    @PutMapping("/roles/{roleId}")
    public R<RoleResponse> updateRole(@PathVariable Long roleId,
                                      @Valid @RequestBody RoleSaveRequest request) {
        return R.ok(rbacService.updateRole(roleId, request));
    }

    @DeleteMapping("/roles/{roleId}")
    public R<Void> deleteRole(@PathVariable Long roleId) {
        rbacService.deleteRole(roleId);
        return R.ok();
    }

    @PutMapping("/roles/{roleId}/permissions")
    public R<RoleResponse> assignRolePermissions(@PathVariable Long roleId,
                                                 @Valid @RequestBody AssignRolePermissionsRequest request) {
        return R.ok(rbacService.assignRolePermissions(roleId, request));
    }

    @GetMapping("/admin-users")
    public R<List<AdminUserRoleResponse>> adminUsers() {
        return R.ok(rbacService.listAdminUsers());
    }

    @GetMapping("/admin-users/{adminUserId}/roles")
    public R<AdminUserRoleResponse> adminUserRoles(@PathVariable Long adminUserId) {
        return R.ok(rbacService.getAdminUserRoles(adminUserId));
    }

    @PutMapping("/admin-users/{adminUserId}/roles")
    public R<AdminUserRoleResponse> assignAdminRoles(@PathVariable Long adminUserId,
                                                    @Valid @RequestBody AssignAdminRolesRequest request) {
        return R.ok(rbacService.assignAdminRoles(adminUserId, request));
    }
}
