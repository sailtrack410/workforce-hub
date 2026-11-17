package com.workforcehub.im.websocket;

import com.workforcehub.auth.security.SessionValidityService;
import com.workforcehub.auth.security.WfhUserDetails;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

/** 握手之后仍在每次收发前重新核验，避免长连接绕过 HTTP 认证边界。 */
@Component
public class ImWebSocketAuthentication {
    public static final String PRINCIPAL_ATTRIBUTE = "imAuthentication";
    private final SessionValidityService validity;

    public ImWebSocketAuthentication(SessionValidityService validity) {
        this.validity = validity;
    }

    public boolean check(WebSocketSession session) throws IOException {
        Object principal = session.getAttributes().get(PRINCIPAL_ATTRIBUTE);
        try {
            if (principal instanceof WfhUserDetails user && validity.isCurrent(user)) return true;
        } catch (DataAccessException e) {
            // 共享数据库不可用时同样关闭连接，不发送任何业务数据。
        }
        if (session.isOpen()) session.close(CloseStatus.POLICY_VIOLATION);
        return false;
    }
}
