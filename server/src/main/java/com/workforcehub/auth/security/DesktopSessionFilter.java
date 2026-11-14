package com.workforcehub.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 仅桌面客户端启用 7 天滑动会话，其他客户端保持全局默认超时。 */
@Component
public class DesktopSessionFilter extends OncePerRequestFilter {

    private final int maxInactiveInterval;

    public DesktopSessionFilter(@Value("${workforce.desktop-session-timeout:604800}") int maxInactiveInterval) {
        this.maxInactiveInterval = maxInactiveInterval;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if ("desktop".equals(request.getHeader("X-Workforce-Client"))) {
            // 登录时创建新会话并写入 7 天期限；已认证请求每次访问自动续期。
            HttpSession session = request.getSession(false);
            if (session != null) session.setMaxInactiveInterval(maxInactiveInterval);
        }
        chain.doFilter(request, response);
    }
}
