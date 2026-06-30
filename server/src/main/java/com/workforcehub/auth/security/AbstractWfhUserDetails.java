package com.workforcehub.auth.security;

import lombok.Getter;

import java.io.Serializable;

@Getter
public abstract class AbstractWfhUserDetails implements WfhUserDetails, Serializable {

    private final Long userId;
    private final String username;
    private final String passwordHash;
    private final String displayName;
    private final String status;

    protected AbstractWfhUserDetails(Long userId,
                                     String username,
                                     String passwordHash,
                                     String displayName,
                                     String status) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.status = status;
    }

    @Override
    public String getPassword() {
        return passwordHash;
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
