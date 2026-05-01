package com.workforcehub.auth.security;

import com.workforcehub.common.enums.UserType;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * 员工/领导用户主体
 */
@Getter
public class EmployeeUserDetails implements WfhUserDetails, Serializable {

    private final Long userId;
    private final Long accountId;
    private final String loginName;
    private final String passwordHash;
    private final String displayName;
    private final String accountStatus;

    public EmployeeUserDetails(Long userId, Long accountId, String loginName,
                               String passwordHash, String displayName, String accountStatus) {
        this.userId = userId;
        this.accountId = accountId;
        this.loginName = loginName;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.accountStatus = accountStatus;
    }

    @Override
    public UserType getUserType() {
        return UserType.USER;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return loginName;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !"LOCKED".equals(accountStatus);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVE".equals(accountStatus);
    }
}
