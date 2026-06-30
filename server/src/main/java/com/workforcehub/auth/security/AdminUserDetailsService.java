package com.workforcehub.auth.security;

import com.workforcehub.auth.domain.AdminUser;
import com.workforcehub.auth.mapper.AdminUserMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 后台管理员 UserDetailsService
 */
@Service("adminUserDetailsService")
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserMapper adminUserMapper;

    public AdminUserDetailsService(AdminUserMapper adminUserMapper) {
        this.adminUserMapper = adminUserMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminUser admin = adminUserMapper.selectByUsername(username);
        if (admin == null) {
            throw new UsernameNotFoundException("管理员账号不存在");
        }

        return new AdminUserDetails(
            admin.getId(),
            admin.getUsername(),
            admin.getPasswordHash(),
            admin.getDisplayName(),
            admin.getStatus(),
            adminUserMapper.selectRoleCodes(admin.getId()),
            adminUserMapper.selectPermissionCodes(admin.getId())
        );
    }
}
