package com.workforcehub.im.websocket;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * IM WebSocket 事件 outbox 写入服务。
 */
@Service
@EnableConfigurationProperties(ImWebSocketOutboxProperties.class)
public class ImWebSocketOutboxService {

    private final ImEventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ImWebSocketOutboxService(ImEventOutboxRepository outboxRepository,
                                    ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void enqueue(Collection<Long> userIds, ImWebSocketEvent event) {
        LinkedHashSet<Long> recipients = normalizeRecipients(userIds);
        if (recipients.isEmpty()) {
            return;
        }
        outboxRepository.insertEvent(
            IdWorker.getId(),
            event.eventType(),
            event.conversationId(),
            event.messageId(),
            event.seqNo(),
            event.actorUserId(),
            writeJson(recipients),
            writeJson(event),
            LocalDateTime.now()
        );
    }

    private LinkedHashSet<Long> normalizeRecipients(Collection<Long> userIds) {
        LinkedHashSet<Long> recipients = new LinkedHashSet<>();
        if (userIds == null) {
            return recipients;
        }
        for (Long userId : userIds) {
            if (userId != null) {
                recipients.add(userId);
            }
        }
        return recipients;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("IM WebSocket outbox JSON序列化失败", e);
        }
    }
}
