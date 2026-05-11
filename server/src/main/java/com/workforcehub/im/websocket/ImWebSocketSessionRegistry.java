package com.workforcehub.im.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 按员工用户 ID 维护当前活跃 IM WebSocket 连接。
 */
@Component
public class ImWebSocketSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ImWebSocketSessionRegistry.class);

    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>> sessionsByUserId = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        sessionsByUserId.computeIfAbsent(userId, ignored -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void unregister(Long userId, WebSocketSession session) {
        CopyOnWriteArraySet<WebSocketSession> sessions = sessionsByUserId.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByUserId.remove(userId, sessions);
        }
    }

    public void clear() {
        sessionsByUserId.clear();
    }

    public int sendToUsers(Collection<Long> userIds, String payload) {
        try {
            return sendToUsersOrThrow(userIds, payload);
        } catch (IOException e) {
            log.warn("IM WebSocket 推送失败", e);
            return 0;
        }
    }

    public int sendToUsersOrThrow(Collection<Long> userIds, String payload) throws IOException {
        Set<Long> uniqueUserIds = new LinkedHashSet<>(userIds);
        int deliveredCount = 0;
        for (Long userId : uniqueUserIds) {
            deliveredCount += sendToUserOrThrow(userId, payload);
        }
        return deliveredCount;
    }

    private int sendToUserOrThrow(Long userId, String payload) throws IOException {
        CopyOnWriteArraySet<WebSocketSession> sessions = sessionsByUserId.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }

        int deliveredCount = 0;
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                unregister(userId, session);
                continue;
            }
            sendOrThrow(userId, session, payload);
            deliveredCount++;
        }
        return deliveredCount;
    }

    private void sendOrThrow(Long userId, WebSocketSession session, String payload) throws IOException {
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException e) {
            log.warn("IM WebSocket 推送失败 userId={} sessionId={}", userId, session.getId(), e);
            unregister(userId, session);
            throw e;
        }
    }
}
