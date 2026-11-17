package com.workforcehub.auth.security;

import com.workforcehub.common.enums.UserType;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 自定义认证 Provider
 * 携带 UserType 标记，便于在登录服务中按类型选择 Provider
 */
public class WfhAuthenticationProvider extends DaoAuthenticationProvider {

    private final UserType userType;

    public WfhAuthenticationProvider(UserDetailsService userDetailsService,
                                     PasswordEncoder passwordEncoder,
                                     UserType userType) {
        super();
        setUserDetailsService(userDetailsService);
        setPasswordEncoder(passwordEncoder);
        this.userType = userType;
    }

    public UserType getUserType() {
        return userType;
    }
}
