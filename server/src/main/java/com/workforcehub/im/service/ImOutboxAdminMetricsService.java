package com.workforcehub.im.service;

import com.workforcehub.im.dto.AdminImOutboxFailureSummaryResponse;
import com.workforcehub.im.dto.AdminImOutboxSummaryResponse;
import com.workforcehub.im.repository.AdminImOutboxFailureSummaryRow;
import com.workforcehub.im.repository.AdminImOutboxStatusSummaryRow;
import com.workforcehub.im.repository.AdminImOutboxSummaryAggregateRow;
import com.workforcehub.im.repository.ImOutboxAdminMetricsRepository;
import com.workforcehub.im.websocket.ImWebSocketOutboxProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台 IM outbox 指标服务。
 */
@Service
public class ImOutboxAdminMetricsService {

    private static final List<String> OUTBOX_STATUS_ORDER = List.of("PENDING", "SENDING", "SENT", "FAILED", "DEAD");
    private static final int FAILURE_SUMMARY_LIMIT = 20;

    private final ImOutboxAdminMetricsRepository outboxAdminMetricsRepository;
    private final ImWebSocketOutboxProperties outboxProperties;

    public ImOutboxAdminMetricsService(ImOutboxAdminMetricsRepository outboxAdminMetricsRepository,
                                       ImWebSocketOutboxProperties outboxProperties) {
        this.outboxAdminMetricsRepository = outboxAdminMetricsRepository;
        this.outboxProperties = outboxProperties;
    }

    public AdminImOutboxSummaryResponse summarizeOutboxEvents() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleSendingBefore = now.minus(Duration.ofMillis(outboxProperties.getSendingStaleMs()));
        AdminImOutboxSummaryAggregateRow aggregate =
            outboxAdminMetricsRepository.summarizeOutboxEvents(now, staleSendingBefore);
        List<AdminImOutboxStatusSummaryRow> rows = outboxAdminMetricsRepository.countOutboxEventsByStatus();

        AdminImOutboxSummaryResponse response = new AdminImOutboxSummaryResponse();
        response.setTotalCount(aggregate.totalCount());
        response.setDueDispatchCount(aggregate.dueDispatchCount());
        response.setStaleSendingCount(aggregate.staleSendingCount());
        response.setEarliestCreatedAt(aggregate.earliestCreatedAt());
        response.setEarliestNextRetryAt(aggregate.earliestNextRetryAt());
        response.setGeneratedAt(now);
        response.setSendingStaleBefore(staleSendingBefore);
        response.setStatusCounts(OUTBOX_STATUS_ORDER.stream()
            .map(status -> AdminImOutboxSummaryResponse.StatusCount.of(status, countStatus(rows, status)))
            .toList());
        return response;
    }

    public List<AdminImOutboxFailureSummaryResponse> listOutboxFailureSummary() {
        return outboxAdminMetricsRepository.listOutboxFailureSummary(FAILURE_SUMMARY_LIMIT).stream()
            .map(this::toFailureSummaryResponse)
            .toList();
    }

    private Long countStatus(List<AdminImOutboxStatusSummaryRow> rows, String status) {
        return rows.stream()
            .filter(row -> status.equals(row.status()))
            .map(AdminImOutboxStatusSummaryRow::eventCount)
            .findFirst()
            .orElse(0L);
    }

    private AdminImOutboxFailureSummaryResponse toFailureSummaryResponse(AdminImOutboxFailureSummaryRow row) {
        AdminImOutboxFailureSummaryResponse response = new AdminImOutboxFailureSummaryResponse();
        response.setStatus(row.status());
        response.setLastError(row.lastError());
        response.setEventCount(row.eventCount());
        response.setMaxRetryCount(row.maxRetryCount());
        response.setFirstOccurredAt(row.firstOccurredAt());
        response.setLastOccurredAt(row.lastOccurredAt());
        return response;
    }
}
