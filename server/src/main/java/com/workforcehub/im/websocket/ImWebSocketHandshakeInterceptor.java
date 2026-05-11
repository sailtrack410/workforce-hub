package com.workforcehub.im.websocket;

import com.workforcehub.auth.security.WfhUserDetails;
import com.workforcehub.common.enums.UserType;
import com.workforcehub.im.repository.ImRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpSession;
import java.util.Map;

/**
 * 从现有员工登录 Session 解析 WebSocket 身份。
 */
@Component
public class ImWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private final ImRepository imRepository;

    public ImWebSocketHandshakeInterceptor(ImRepository imRepository) {
        this.imRepository = imRepository;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        WfhUserDetails user = resolveCurrentUser(request);
        if (user == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        if (user.getUserType() != UserType.USER) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        if (!imRepository.existsLoginAllowedOrgUser(user.getUserId())) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(ImWebSocketHandler.USER_ID_ATTRIBUTE, user.getUserId());
        attributes.put(ImWebSocketHandler.DISPLAY_NAME_ATTRIBUTE, user.getDisplayName());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }

    private WfhUserDetails resolveCurrentUser(ServerHttpRequest request) {
        WfhUserDetails current = resolveFromAuthentication(SecurityContextHolder.getContext().getAuthentication());
        if (current != null) {
            return current;
        }
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpSession session = servletRequest.getServletRequest().getSession(false);
            if (session == null) {
                return null;
            }
            Object context = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (context instanceof SecurityContext securityContext) {
                return resolveFromAuthentication(securityContext.getAuthentication());
            }
        }
        return null;
    }

    private WfhUserDetails resolveFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof WfhUserDetails user ? user : null;
    }
}
