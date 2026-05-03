package com.workforcehub.auth.security;

import org.springframework.security.authentication.DisabledException;

/**
 * 员工生命周期状态导致的登录拦截
 * 与“账号本身被禁用”区分开，便于向前端返回更明确的业务提示。
 */
public class EmploymentLoginBlockedException extends DisabledException {

    public EmploymentLoginBlockedException(String msg) {
        super(msg);
    }
}
