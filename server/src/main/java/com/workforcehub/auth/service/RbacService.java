package com.workforcehub.auth.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.workforcehub.auth.dto.RbacDtos.AdminUserRoleResponse;
import com.workforcehub.auth.dto.RbacDtos.AssignAdminRolesRequest;
import com.workforcehub.auth.dto.RbacDtos.AssignRolePermissionsRequest;
import com.workforcehub.auth.dto.RbacDtos.PermissionResponse;
import com.workforcehub.auth.dto.RbacDtos.RoleResponse;
import com.workforcehub.auth.dto.RbacDtos.RoleSaveRequest;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 后台 RBAC 管理服务。
 */
@Service
public class RbacService {

    private final JdbcTemplate jdbcTemplate;

    public RbacService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PermissionResponse> listPermissions() {
        return jdbcTemplate.query("""
            SELECT id, perm_code, perm_name, module_code, action_code, status
            FROM admin_permission
            WHERE is_deleted = 0
            ORDER BY module_code, action_code, id
            """, (rs, rowNum) -> toPermissionResponse(rs));
    }

    public List<RoleResponse> listRoles(String keyword) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT id, role_code, role_name, status, created_at, updated_at
            FROM admin_role
            WHERE is_deleted = 0
            """);
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (role_code LIKE ? OR role_name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY created_at DESC, id DESC");
        List<RoleResponse> roles = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> toRoleResponse(rs), args.toArray());
        roles.forEach(role -> role.setPermissions(listRolePermissions(role.getRoleId())));
        return roles;
    }

    public RoleResponse getRole(Long roleId) {
        RoleResponse role = findRole(roleId);
        role.setPermissions(listRolePermissions(roleId));
        return role;
    }

    @Transactional
    public RoleResponse createRole(RoleSaveRequest request) {
        validateStatus(request.getStatus());
        if (existsRoleCode(request.getRoleCode().trim(), null)) {
            throw new BizException(409, "角色编码已存在");
        }
        Long roleId = IdWorker.getId();
        Long operatorId = CurrentUserHolder.requireUserId();
        jdbcTemplate.update("""
            INSERT INTO admin_role (id, role_code, role_name, status, created_by, updated_by, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, 0)
            """, roleId, request.getRoleCode().trim(), request.getRoleName().trim(), statusOrActive(request.getStatus()), operatorId, operatorId);
        return getRole(roleId);
    }

    @Transactional
    public RoleResponse updateRole(Long roleId, RoleSaveRequest request) {
        validateStatus(request.getStatus());
        findRole(roleId);
        if (existsRoleCode(request.getRoleCode().trim(), roleId)) {
            throw new BizException(409, "角色编码已存在");
        }
        jdbcTemplate.update("""
            UPDATE admin_role
            SET role_code = ?, role_name = ?, status = ?, updated_by = ?
            WHERE id = ? AND is_deleted = 0
            """, request.getRoleCode().trim(), request.getRoleName().trim(), statusOrActive(request.getStatus()), CurrentUserHolder.requireUserId(), roleId);
        return getRole(roleId);
    }

    @Transactional
    public void deleteRole(Long roleId) {
        findRole(roleId);
        jdbcTemplate.update("DELETE FROM admin_role_permission WHERE role_id = ?", roleId);
        jdbcTemplate.update("DELETE FROM admin_user_role WHERE role_id = ?", roleId);
        jdbcTemplate.update("UPDATE admin_role SET is_deleted = 1, updated_by = ? WHERE id = ? AND is_deleted = 0",
            CurrentUserHolder.requireUserId(), roleId);
    }

    @Transactional
    public RoleResponse assignRolePermissions(Long roleId, AssignRolePermissionsRequest request) {
        findRole(roleId);
        List<Long> permissionIds = distinctIds(request.getPermissionIds());
        ensurePermissionsExist(permissionIds);
        jdbcTemplate.update("DELETE FROM admin_role_permission WHERE role_id = ?", roleId);
        Long operatorId = CurrentUserHolder.requireUserId();
        for (Long permissionId : permissionIds) {
            jdbcTemplate.update("""
                INSERT INTO admin_role_permission (id, role_id, permission_id, created_by)
                VALUES (?, ?, ?, ?)
                """, IdWorker.getId(), roleId, permissionId, operatorId);
        }
        return getRole(roleId);
    }

    public AdminUserRoleResponse getAdminUserRoles(Long adminUserId) {
        AdminUserRoleResponse response = findAdminUser(adminUserId);
        response.setRoles(listAdminRoles(adminUserId));
        return response;
    }

    public List<AdminUserRoleResponse> listAdminUsers() {
        List<AdminUserRoleResponse> users = jdbcTemplate.query("""
            SELECT id, username, display_name
            FROM admin_user
            WHERE is_deleted = 0
            ORDER BY created_at DESC, id DESC
            """, (rs, rowNum) -> {
            AdminUserRoleResponse response = new AdminUserRoleResponse();
            response.setAdminUserId(rs.getLong("id"));
            response.setUsername(rs.getString("username"));
            response.setDisplayName(rs.getString("display_name"));
            return response;
        });
        users.forEach(user -> user.setRoles(listAdminRoles(user.getAdminUserId())));
        return users;
    }

    @Transactional
    public AdminUserRoleResponse assignAdminRoles(Long adminUserId, AssignAdminRolesRequest request) {
        findAdminUser(adminUserId);
        List<Long> roleIds = distinctIds(request.getRoleIds());
        ensureRolesExist(roleIds);
        jdbcTemplate.update("DELETE FROM admin_user_role WHERE admin_user_id = ?", adminUserId);
        Long operatorId = CurrentUserHolder.requireUserId();
        for (Long roleId : roleIds) {
            jdbcTemplate.update("""
                INSERT INTO admin_user_role (id, admin_user_id, role_id, created_by)
                VALUES (?, ?, ?, ?)
                """, IdWorker.getId(), adminUserId, roleId, operatorId);
        }
        return getAdminUserRoles(adminUserId);
    }

    private RoleResponse findRole(Long roleId) {
        List<RoleResponse> roles = jdbcTemplate.query("""
            SELECT id, role_code, role_name, status, created_at, updated_at
            FROM admin_role
            WHERE id = ? AND is_deleted = 0
            """, (rs, rowNum) -> toRoleResponse(rs), roleId);
        if (roles.isEmpty()) {
            throw new BizException(404, "角色不存在");
        }
        return roles.get(0);
    }

    private AdminUserRoleResponse findAdminUser(Long adminUserId) {
        List<AdminUserRoleResponse> users = jdbcTemplate.query("""
            SELECT id, username, display_name
            FROM admin_user
            WHERE id = ? AND is_deleted = 0
            """, (rs, rowNum) -> {
            AdminUserRoleResponse response = new AdminUserRoleResponse();
            response.setAdminUserId(rs.getLong("id"));
            response.setUsername(rs.getString("username"));
            response.setDisplayName(rs.getString("display_name"));
            return response;
        }, adminUserId);
        if (users.isEmpty()) {
            throw new BizException(404, "管理员不存在");
        }
        return users.get(0);
    }

    private List<RoleResponse> listAdminRoles(Long adminUserId) {
        List<RoleResponse> roles = jdbcTemplate.query("""
            SELECT r.id, r.role_code, r.role_name, r.status, r.created_at, r.updated_at
            FROM admin_role r
            JOIN admin_user_role ur ON ur.role_id = r.id
            WHERE ur.admin_user_id = ? AND r.is_deleted = 0
            ORDER BY r.role_code
            """, (rs, rowNum) -> toRoleResponse(rs), adminUserId);
        roles.forEach(role -> role.setPermissions(listRolePermissions(role.getRoleId())));
        return roles;
    }

    private List<PermissionResponse> listRolePermissions(Long roleId) {
        return jdbcTemplate.query("""
            SELECT p.id, p.perm_code, p.perm_name, p.module_code, p.action_code, p.status
            FROM admin_permission p
            JOIN admin_role_permission rp ON rp.permission_id = p.id
            WHERE rp.role_id = ? AND p.is_deleted = 0
            ORDER BY p.module_code, p.action_code, p.id
            """, (rs, rowNum) -> toPermissionResponse(rs), roleId);
    }

    private boolean existsRoleCode(String roleCode, Long excludeRoleId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM admin_role
            WHERE role_code = ?
              AND is_deleted = 0
              AND (? IS NULL OR id <> ?)
            """, Long.class, roleCode, excludeRoleId, excludeRoleId);
        return count != null && count > 0;
    }

    private void ensurePermissionsExist(List<Long> permissionIds) {
        if (permissionIds.isEmpty()) return;
        String marks = "?,".repeat(permissionIds.size());
        marks = marks.substring(0, marks.length() - 1);
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM admin_permission
            WHERE is_deleted = 0 AND id IN (%s)
            """.formatted(marks), Long.class, permissionIds.toArray());
        if (count == null || count != permissionIds.size()) {
            throw new BizException(404, "权限不存在");
        }
    }

    private void ensureRolesExist(List<Long> roleIds) {
        if (roleIds.isEmpty()) return;
        String marks = "?,".repeat(roleIds.size());
        marks = marks.substring(0, marks.length() - 1);
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM admin_role
            WHERE is_deleted = 0 AND id IN (%s)
            """.formatted(marks), Long.class, roleIds.toArray());
        if (count == null || count != roleIds.size()) {
            throw new BizException(404, "角色不存在");
        }
    }

    private List<Long> distinctIds(List<Long> ids) {
        if (ids == null) return List.of();
        return ids.stream()
            .filter(id -> id != null && id > 0)
            .distinct()
            .toList();
    }

    private void validateStatus(String status) {
        if (status == null || status.isBlank()) return;
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new BizException(400, "状态仅支持 ACTIVE 或 INACTIVE");
        }
    }

    private String statusOrActive(String status) {
        return status == null || status.isBlank() ? "ACTIVE" : status;
    }

    private RoleResponse toRoleResponse(ResultSet rs) throws SQLException {
        RoleResponse response = new RoleResponse();
        response.setRoleId(rs.getLong("id"));
        response.setRoleCode(rs.getString("role_code"));
        response.setRoleName(rs.getString("role_name"));
        response.setStatus(rs.getString("status"));
        response.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        response.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return response;
    }

    private PermissionResponse toPermissionResponse(ResultSet rs) throws SQLException {
        PermissionResponse response = new PermissionResponse();
        response.setPermissionId(rs.getLong("id"));
        response.setPermCode(rs.getString("perm_code"));
        response.setPermName(rs.getString("perm_name"));
        response.setModuleCode(rs.getString("module_code"));
        response.setActionCode(rs.getString("action_code"));
        response.setStatus(rs.getString("status"));
        return response;
    }
}
