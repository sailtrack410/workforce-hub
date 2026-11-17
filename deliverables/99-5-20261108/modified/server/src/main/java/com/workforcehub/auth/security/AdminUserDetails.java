package com.workforcehub.auth.security;

import com.workforcehub.common.enums.UserType;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * 后台管理员用户主体
 */
@Getter
public class AdminUserDetails extends AbstractWfhUserDetails {

    private final List<String> roleCodes;
    private final List<String> permissionCodes;

    public AdminUserDetails(Long userId, String username, String passwordHash,
                            String displayName, String status,
                            List<String> roleCodes, List<String> permissionCodes) {
        super(userId, username, passwordHash, displayName, status);
        this.roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
        this.permissionCodes = permissionCodes == null ? List.of() : List.copyOf(permissionCodes);
    }

    @Override
    public UserType getUserType() {
        return UserType.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return permissionCodes.stream()
            .map(SimpleGrantedAuthority::new)
            .map(GrantedAuthority.class::cast)
            .toList();
    }
}
