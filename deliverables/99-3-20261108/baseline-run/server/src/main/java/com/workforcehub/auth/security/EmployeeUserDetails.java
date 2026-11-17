package com.workforcehub.auth.security;

import com.workforcehub.common.enums.UserType;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * 员工/领导用户主体
 */
@Getter
public class EmployeeUserDetails extends AbstractWfhUserDetails {

    private final Long accountId;
    private final String loginName;
    private final String accountStatus;

    public EmployeeUserDetails(Long userId, Long accountId, String loginName,
                               String passwordHash, String displayName, String accountStatus) {
        super(userId, loginName, passwordHash, displayName, accountStatus);
        this.accountId = accountId;
        this.loginName = loginName;
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
}
