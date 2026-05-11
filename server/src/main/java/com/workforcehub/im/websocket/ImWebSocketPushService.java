package com.workforcehub.im.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * IM 实时事件推送门面。
 */
@Service
public class ImWebSocketPushService {

    private static final Logger log = LoggerFactory.getLogger(ImWebSocketPushService.class);

    private final ImWebSocketSessionRegistry sessionRegistry;
    private final ImWebSocketOutboxService outboxService;
    private final ImWebSocketOutboxDispatcher outboxDispatcher;
    private final ObjectMapper objectMapper;

    public ImWebSocketPushService(ImWebSocketSessionRegistry sessionRegistry,
                                  ImWebSocketOutboxService outboxService,
                                  ImWebSocketOutboxDispatcher outboxDispatcher,
                                  ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.outboxService = outboxService;
        this.outboxDispatcher = outboxDispatcher;
        this.objectMapper = objectMapper;
    }

    public void pushToUsersAfterCommit(Collection<Long> userIds, ImWebSocketEvent event) {
        LinkedHashSet<Long> recipients = new LinkedHashSet<>(userIds);
        if (recipients.isEmpty()) {
            return;
        }
        outboxService.enqueue(recipients, event);
        Runnable delivery = () -> outboxDispatcher.dispatchDueEvents(10);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delivery.run();
                }
            });
            return;
        }
        delivery.run();
    }

    public int pushToUsers(Collection<Long> userIds, ImWebSocketEvent event) {
        try {
            return sessionRegistry.sendToUsers(userIds, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("IM WebSocket 事件推送失败 eventType={} conversationId={}",
                event.eventType(),
                event.conversationId(),
                e
            );
            return 0;
        }
    }
}
