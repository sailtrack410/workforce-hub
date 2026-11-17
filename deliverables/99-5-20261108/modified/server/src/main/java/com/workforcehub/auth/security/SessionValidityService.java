package com.workforcehub.auth.security;

import com.workforcehub.common.enums.UserType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

/** 以共享数据库的当前身份为准，不依赖单实例失效通知或本地缓存。 */
@Service
public class SessionValidityService {
    private final EmployeeUserDetailsService employees;
    private final AdminUserDetailsService admins;

    public SessionValidityService(EmployeeUserDetailsService employees, AdminUserDetailsService admins) {
        this.employees = employees;
        this.admins = admins;
    }

    public boolean isCurrent(WfhUserDetails snapshot) {
        WfhUserDetails current;
        try {
            current = (WfhUserDetails) (snapshot.getUserType() == UserType.USER
                ? employees.loadUserByUsername(snapshot.getUsername())
                : admins.loadUserByUsername(snapshot.getUsername()));
        } catch (AuthenticationException e) {
            return false;
        }
        if (!current.isEnabled() || !current.isAccountNonLocked() || !current.isAccountNonExpired()
            || !current.isCredentialsNonExpired() || current.getUserType() != snapshot.getUserType()
            || !Objects.equals(current.getUserId(), snapshot.getUserId())
            || !Objects.equals(current.getPassword(), snapshot.getPassword())) {
            return false;
        }
        if (snapshot instanceof EmployeeUserDetails previous && current instanceof EmployeeUserDetails employee
            && !Objects.equals(previous.getAccountId(), employee.getAccountId())) {
            return false;
        }
        if (snapshot instanceof AdminUserDetails previous && current instanceof AdminUserDetails admin
            && !new HashSet<>(previous.getRoleCodes()).equals(new HashSet<>(admin.getRoleCodes()))) {
            return false;
        }
        return snapshot.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet())
            .equals(current.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet()));
    }
}
