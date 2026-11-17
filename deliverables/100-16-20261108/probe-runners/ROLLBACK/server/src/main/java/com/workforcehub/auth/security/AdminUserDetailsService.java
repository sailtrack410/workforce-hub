package com.workforcehub.auth.security;

import com.workforcehub.auth.domain.AdminUser;
import com.workforcehub.auth.mapper.AdminUserMapper;
import com.workforcehub.auth.service.AdminPermissionService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 后台管理员 UserDetailsService
 */
// Spring Security 加载管理员用户：从 admin_user 表查询，构建 GrantedAuthority 列表
@Service("adminUserDetailsService")
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserMapper adminUserMapper;
    private final AdminPermissionService adminPermissionService;

    public AdminUserDetailsService(AdminUserMapper adminUserMapper,
                                   AdminPermissionService adminPermissionService) {
        this.adminUserMapper = adminUserMapper;
        this.adminPermissionService = adminPermissionService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminUser admin = adminUserMapper.selectByUsername(username);
        if (admin == null) {
            throw new UsernameNotFoundException("管理员账号不存在");
        }

        AdminPermissionService.AdminPermissionProfile permissionProfile =
            adminPermissionService.resolveProfile(admin.getId());
        return new AdminUserDetails(
            admin.getId(),
            admin.getUsername(),
            admin.getPasswordHash(),
            admin.getDisplayName(),
            admin.getStatus(),
            permissionProfile.roleCodes(),
            permissionProfile.permissionCodes()
        );
    }
}
