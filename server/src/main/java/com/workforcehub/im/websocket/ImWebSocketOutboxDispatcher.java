package com.workforcehub.im.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IM WebSocket outbox 投递器。
 */
@Service
public class ImWebSocketOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ImWebSocketOutboxDispatcher.class);
    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {
    };

    private final ImEventOutboxRepository outboxRepository;
    private final ImWebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final ImWebSocketOutboxProperties properties;
    private final TransactionTemplate transactionTemplate;

    public ImWebSocketOutboxDispatcher(ImEventOutboxRepository outboxRepository,
                                       ImWebSocketSessionRegistry sessionRegistry,
                                       ObjectMapper objectMapper,
                                       ImWebSocketOutboxProperties properties,
                                       PlatformTransactionManager transactionManager) {
        this.outboxRepository = outboxRepository;
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(
        fixedDelayString = "${workforce.im.outbox.dispatcher.fixed-delay-ms:3000}",
        initialDelayString = "${workforce.im.outbox.dispatcher.initial-delay-ms:3000}"
    )
    public void scheduledDispatchDueEvents() {
        if (!properties.isEnabled()) {
            return;
        }
        dispatchDueEvents();
    }

    public int dispatchDueEvents() {
        return dispatchDueEvents(properties.getBatchSize());
    }

    public int dispatchDueEvents(int batchSize) {
        List<ImEventOutboxRow> rows = claimDueEvents(Math.max(batchSize, 1));
        int dispatched = 0;
        for (ImEventOutboxRow row : rows) {
            if (dispatchOne(row)) {
                dispatched++;
            }
        }
        return dispatched;
    }

    private List<ImEventOutboxRow> claimDueEvents(int batchSize) {
        return transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime staleSendingBefore = now.minusNanos(properties.getSendingStaleMs() * 1_000_000L);
            List<Long> ids = outboxRepository.claimDueEventIds(batchSize, now, staleSendingBefore);
            if (ids.isEmpty()) {
                return List.of();
            }
            for (Long id : ids) {
                outboxRepository.markSending(id, now);
            }
            return outboxRepository.listEventsByIds(ids);
        });
    }

    private boolean dispatchOne(ImEventOutboxRow row) {
        try {
            List<Long> recipients = objectMapper.readValue(row.recipientUserIdsJson(), LONG_LIST_TYPE);
            sessionRegistry.sendToUsersOrThrow(recipients, row.payloadJson());
            transactionTemplate.executeWithoutResult(status -> outboxRepository.markSent(row.id(), LocalDateTime.now()));
            return true;
        } catch (Exception e) {
            markFailed(row, e);
            return false;
        }
    }

    private void markFailed(ImEventOutboxRow row, Exception e) {
        int nextRetryCount = row.retryCount() == null ? 1 : row.retryCount() + 1;
        boolean dead = nextRetryCount >= properties.getMaxAttempts();
        LocalDateTime nextRetryAt = LocalDateTime.now().plusNanos(properties.getRetryDelayMs() * 1_000_000L);
        transactionTemplate.executeWithoutResult(status -> outboxRepository.markFailed(
            row.id(),
            dead ? "DEAD" : "FAILED",
            nextRetryCount,
            nextRetryAt,
            e.getMessage()
        ));
        log.warn("IM WebSocket outbox 投递失败 eventId={} eventType={} conversationId={} retryCount={}",
            row.id(),
            row.eventType(),
            row.conversationId(),
            nextRetryCount,
            e
        );
    }
}
