package com.workforcehub.im.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;

/**
 * 员工端 IM WebSocket 处理器。
 */
@Component
public class ImWebSocketHandler extends TextWebSocketHandler {

    public static final String USER_ID_ATTRIBUTE = "imUserId";
    public static final String DISPLAY_NAME_ATTRIBUTE = "imDisplayName";

    private final ImWebSocketSessionRegistry sessionRegistry;

    public ImWebSocketHandler(ImWebSocketSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = currentUserId(session);
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        sessionRegistry.register(userId, session);
        session.sendMessage(new TextMessage("""
            {"eventType":"CONNECTED","occurredAt":"%s"}
            """.formatted(LocalDateTime.now())));
    }

    // 心跳响应：兼容纯文本 PING 和 JSON 格式 {"PING"}
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if ("PING".equalsIgnoreCase(payload) || payload.contains("\"PING\"")) {
            session.sendMessage(new TextMessage("""
                {"eventType":"PONG","occurredAt":"%s"}
                """.formatted(LocalDateTime.now())));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        unregister(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        unregister(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private void unregister(WebSocketSession session) {
        Long userId = currentUserId(session);
        if (userId != null) {
            sessionRegistry.unregister(userId, session);
        }
    }

    private Long currentUserId(WebSocketSession session) {
        Object value = session.getAttributes().get(USER_ID_ATTRIBUTE);
        return value instanceof Long userId ? userId : null;
    }
}
