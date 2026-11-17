package com.workforcehub.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.exception.BusinessErrorCode;
import com.workforcehub.common.response.R;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static com.workforcehub.common.config.RequestPathResolver.resolve;

@Component
public class SessionValidityFilter extends OncePerRequestFilter {
    private final SessionValidityService validity;
    private final ObjectMapper objectMapper;

    public SessionValidityFilter(SessionValidityService validity, ObjectMapper objectMapper) {
        this.validity = validity;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String path = resolve(request);
        // 允许旧身份直接重新登录，仍由 Provider 完整校验凭证和登录准入。
        if ("/api/employee/auth/login".equals(path) || "/api/admin/auth/login".equals(path)) {
            chain.doFilter(request, response);
            return;
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
            && authentication.getPrincipal() instanceof WfhUserDetails user) {
            boolean current;
            try {
                current = validity.isCurrent(user);
            } catch (DataAccessException e) {
                // 数据库不可用时不得退回 Session 中的旧权限。
                writeFailure(response, 503, "认证状态暂不可用");
                return;
            }
            if (!current) {
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                writeFailure(response, 401, "登录状态已变更，请重新登录");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void writeFailure(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), R.fail(status == 401
            ? BusinessErrorCode.UNAUTHORIZED.code() : 503, message));
    }
}
