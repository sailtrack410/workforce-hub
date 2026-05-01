package com.workforcehub.auth.security;

import com.workforcehub.common.enums.UserType;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * 后台管理员用户主体
 */
@Getter
public class AdminUserDetails implements WfhUserDetails, Serializable {

    private final Long userId;
    private final String username;
    private final String passwordHash;
    private final String displayName;
    private final String status;

    public AdminUserDetails(Long userId, String username, String passwordHash,
                            String displayName, String status) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.status = status;
    }

    @Override
    public UserType getUserType() {
        return UserType.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !"LOCKED".equals(status);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVE".equals(status);
    }
}
