package com.workforcehub.auth.security;

import com.workforcehub.common.enums.UserType;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.exception.BusinessErrorCode;
import com.workforcehub.common.response.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 用户类型路径校验
 * 路径隔离过滤器：/api/admin/** 仅允许管理员访问，/api/employee/** 仅允许员工访问
 *
 * 保证 /api/employee/** 只允许 UserType=USER 访问
 *      /api/admin/**    只允许 UserType=ADMIN 访问
 *
 * 这样即使员工和管理员共享同一套 Session 存储，也不会发生身份串扰。
 */
@Component
public class UserTypePathFilter extends OncePerRequestFilter {

    private static final String EMPLOYEE_PATH_PREFIX = "/api/employee/";
    private static final String ADMIN_PATH_PREFIX = "/api/admin/";
    private static final String EMPLOYEE_LOGIN_PATH = "/api/employee/auth/login";
    private static final String ADMIN_LOGIN_PATH = "/api/admin/auth/login";

    private final ObjectMapper objectMapper;

    public UserTypePathFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (EMPLOYEE_LOGIN_PATH.equals(uri) || ADMIN_LOGIN_PATH.equals(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 仅校验已登录请求
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
            && auth.getPrincipal() instanceof WfhUserDetails user) {

            UserType userType = user.getUserType();

            if (uri.startsWith(EMPLOYEE_PATH_PREFIX) && userType != UserType.USER) {
                writeForbidden(response, "当前账号不是员工身份，无法访问员工端");
                return;
            }
            if (uri.startsWith(ADMIN_PATH_PREFIX) && userType != UserType.ADMIN) {
                writeForbidden(response, "当前账号不是管理员身份，无法访问后台");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), R.fail(BusinessErrorCode.FORBIDDEN.code(), message));
    }
}
