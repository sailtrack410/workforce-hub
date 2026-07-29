package com.workforcehub.common.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DomainEventPublisherTest {

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void shouldRejectPublicationOutsideTransaction() {
        ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
        DomainEventPublisher publisher = new DomainEventPublisher(applicationEventPublisher);
        DomainEvent event = event();

        assertThrows(IllegalStateException.class, () -> publisher.publishAfterCommit(event));
        verify(applicationEventPublisher, never()).publishEvent(event);
    }

    @Test
    void shouldPublishOnlyAfterCommit() {
        ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
        DomainEventPublisher publisher = new DomainEventPublisher(applicationEventPublisher);
        DomainEvent event = event();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishAfterCommit(event);

        verify(applicationEventPublisher, never()).publishEvent(event);
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
        verify(applicationEventPublisher).publishEvent(event);
    }

    private DomainEvent event() {
        return new DomainEvent() {
            @Override
            public String eventName() {
                return "test.event";
            }

            @Override
            public Long operatorId() {
                return 1001L;
            }

            @Override
            public LocalDateTime occurredAt() {
                return LocalDateTime.of(2026, 7, 30, 12, 0);
            }
        };
    }
}
