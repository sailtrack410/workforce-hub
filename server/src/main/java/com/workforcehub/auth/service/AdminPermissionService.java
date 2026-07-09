package com.workforcehub.auth.service;

import com.workforcehub.auth.mapper.AdminUserMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 后台权限契约：SUPER_ADMIN 始终代表所有当前有效权限。
 */
@Service
public class AdminPermissionService {

    public static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    private final AdminUserMapper adminUserMapper;

    public AdminPermissionService(AdminUserMapper adminUserMapper) {
        this.adminUserMapper = adminUserMapper;
    }

    public AdminPermissionProfile resolveProfile(Long adminUserId) {
        List<String> roleCodes = adminUserMapper.selectRoleCodes(adminUserId);
        return new AdminPermissionProfile(roleCodes, resolvePermissionCodes(adminUserId, roleCodes));
    }

    public List<String> resolvePermissionCodes(Long adminUserId, Collection<String> roleCodes) {
        if (hasSuperAdminRole(roleCodes)) {
            return adminUserMapper.selectAllActivePermissionCodes();
        }
        return adminUserMapper.selectPermissionCodes(adminUserId);
    }

    public static boolean hasSuperAdminRole(Collection<String> roleCodes) {
        return roleCodes != null && roleCodes.contains(SUPER_ADMIN_ROLE_CODE);
    }

    public record AdminPermissionProfile(List<String> roleCodes, List<String> permissionCodes) {}
}
