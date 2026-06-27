package com.workforcehub.im.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.im.dto.AdminImOutboxBatchRetryRequest;
import com.workforcehub.im.dto.AdminImOutboxBatchRetryResponse;
import com.workforcehub.im.dto.AdminImOutboxDispatchResponse;
import com.workforcehub.im.dto.AdminImOutboxEventResponse;
import com.workforcehub.im.repository.ImOutboxAdminAuditRepository;
import com.workforcehub.im.repository.ImOutboxAdminEventRepository;
import com.workforcehub.im.repository.AdminImOutboxEventRow;
import com.workforcehub.im.support.ImOutboxAdminAuditActions;
import com.workforcehub.im.websocket.ImWebSocketOutboxDispatchResult;
import com.workforcehub.im.websocket.ImWebSocketOutboxDispatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 后台 IM outbox 治理服务。
 */
@Service
public class ImOutboxAdminService {

    private static final String AUDIT_OPERATOR_TYPE_ADMIN = "ADMIN";
    private static final String AUDIT_TARGET_TYPE_OUTBOX_EVENT = "IM_EVENT_OUTBOX";
    private static final String AUDIT_TARGET_TYPE_OUTBOX_DISPATCH = "IM_EVENT_OUTBOX_DISPATCH";
    private static final String AUDIT_TARGET_TYPE_OUTBOX_BATCH_RETRY = "IM_EVENT_OUTBOX_BATCH_RETRY";

    private final ImOutboxAdminEventRepository outboxAdminEventRepository;
    private final ImOutboxAdminAuditRepository outboxAdminAuditRepository;
    private final ImWebSocketOutboxDispatcher outboxDispatcher;
    private final ObjectMapper objectMapper;

    public ImOutboxAdminService(ImOutboxAdminEventRepository outboxAdminEventRepository,
                                ImOutboxAdminAuditRepository outboxAdminAuditRepository,
                                ImWebSocketOutboxDispatcher outboxDispatcher,
                                ObjectMapper objectMapper) {
        this.outboxAdminEventRepository = outboxAdminEventRepository;
        this.outboxAdminAuditRepository = outboxAdminAuditRepository;
        this.outboxDispatcher = outboxDispatcher;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminImOutboxEventResponse retryOutboxEvent(Long eventId) {
        Long adminId = CurrentUserHolder.requireUserId();
        AdminImOutboxEventRow row = outboxAdminEventRepository.getOutboxEventOrThrow(eventId);
        if (!Set.of("FAILED", "DEAD").contains(row.status())) {
            throw new BizException(409, "当前outbox事件状态不允许手动重试");
        }
        assertPayloadEventMatches(row);
        int updated = outboxAdminEventRepository.resetForAdminRetry(eventId, LocalDateTime.now().minusSeconds(1));
        if (updated != 1) {
            throw new BizException(409, "当前outbox事件状态不允许手动重试");
        }
        AdminImOutboxEventRow updatedRow = outboxAdminEventRepository.getOutboxEventOrThrow(eventId);
        outboxAdminAuditRepository.insertAudit(
            AUDIT_OPERATOR_TYPE_ADMIN,
            adminId,
            ImOutboxAdminAuditActions.RETRY_REQUESTED,
            AUDIT_TARGET_TYPE_OUTBOX_EVENT,
            eventId,
            "SUCCESS",
            retryAuditDetail(row, updatedRow)
        );
        return toEventResponse(updatedRow);
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminImOutboxBatchRetryResponse batchRetryOutboxEvents(AdminImOutboxBatchRetryRequest request) {
        Long adminId = CurrentUserHolder.requireUserId();
        List<Long> eventIds = normalizeBatchEventIds(request.getEventIds());
        List<AdminImOutboxBatchRetryResponse.Item> items = eventIds.stream()
            .map(this::retryOneForBatch)
            .toList();
        AdminImOutboxBatchRetryResponse response = AdminImOutboxBatchRetryResponse.of(items);
        outboxAdminAuditRepository.insertAudit(
            AUDIT_OPERATOR_TYPE_ADMIN,
            adminId,
            ImOutboxAdminAuditActions.BATCH_RETRY_REQUESTED,
            AUDIT_TARGET_TYPE_OUTBOX_BATCH_RETRY,
            null,
            response.getSkippedCount() == 0 ? "SUCCESS" : "PARTIAL_SUCCESS",
            batchRetryAuditDetail(response)
        );
        return response;
    }

    public AdminImOutboxDispatchResponse dispatchDueEvents(int batchSize) {
        Long adminId = CurrentUserHolder.requireUserId();
        int safeBatchSize = Math.min(Math.max(batchSize, 1), 500);
        ImWebSocketOutboxDispatchResult dispatchResult = outboxDispatcher.dispatchDueEventsWithResult(safeBatchSize);
        outboxAdminAuditRepository.insertAudit(
            AUDIT_OPERATOR_TYPE_ADMIN,
            adminId,
            ImOutboxAdminAuditActions.DISPATCH_TRIGGERED,
            AUDIT_TARGET_TYPE_OUTBOX_DISPATCH,
            null,
            "SUCCESS",
            dispatchAuditDetail(safeBatchSize, dispatchResult)
        );
        return AdminImOutboxDispatchResponse.of(safeBatchSize, dispatchResult.dispatchedCount());
    }

    private void assertPayloadEventMatches(AdminImOutboxEventRow row) {
        try {
            JsonNode node = objectMapper.readTree(row.payloadJson());
            String payloadEventType = node.path("eventType").asText(null);
            long payloadConversationId = node.path("conversationId").asLong(Long.MIN_VALUE);
            if (!row.eventType().equals(payloadEventType) || payloadConversationId != row.conversationId()) {
                throw new BizException(409, "outbox事件payload与索引字段不一致，需人工核查后再重试");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(409, "outbox事件payload不是合法JSON，需人工核查后再重试");
        }
    }

    static AdminImOutboxEventResponse toEventResponse(AdminImOutboxEventRow row) {
        AdminImOutboxEventResponse response = new AdminImOutboxEventResponse();
        response.setEventId(row.eventId());
        response.setEventType(row.eventType());
        response.setConversationId(row.conversationId());
        response.setMessageId(row.messageId());
        response.setSeqNo(row.seqNo());
        response.setActorUserId(row.actorUserId());
        response.setRecipientUserIdsJson(row.recipientUserIdsJson());
        response.setPayloadJson(row.payloadJson());
        response.setStatus(row.status());
        response.setRetryCount(row.retryCount());
        response.setNextRetryAt(row.nextRetryAt());
        response.setLastError(row.lastError());
        response.setClaimedAt(row.claimedAt());
        response.setSentAt(row.sentAt());
        response.setCreatedAt(row.createdAt());
        response.setUpdatedAt(row.updatedAt());
        return response;
    }

    private List<Long> normalizeBatchEventIds(List<Long> eventIds) {
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long eventId : eventIds) {
            if (eventId == null) {
                throw new BizException(400, "outbox事件ID不能为空");
            }
            if (eventId <= 0) {
                throw new BizException(400, "outbox事件ID必须大于0");
            }
            distinctIds.add(eventId);
        }
        if (distinctIds.isEmpty()) {
            throw new BizException(400, "outbox事件ID不能为空");
        }
        return List.copyOf(distinctIds);
    }

    private AdminImOutboxBatchRetryResponse.Item retryOneForBatch(Long eventId) {
        return outboxAdminEventRepository.findOutboxEvent(eventId)
            .map(this::retryExistingOneForBatch)
            .orElseGet(() -> AdminImOutboxBatchRetryResponse.Item.skipped(eventId, null, "IM outbox事件不存在"));
    }

    private AdminImOutboxBatchRetryResponse.Item retryExistingOneForBatch(AdminImOutboxEventRow row) {
        if (!Set.of("FAILED", "DEAD").contains(row.status())) {
            return AdminImOutboxBatchRetryResponse.Item.skipped(
                row.eventId(),
                row.status(),
                "当前outbox事件状态不允许手动重试"
            );
        }
        try {
            assertPayloadEventMatches(row);
        } catch (BizException e) {
            return AdminImOutboxBatchRetryResponse.Item.skipped(row.eventId(), row.status(), e.getMessage());
        }
        int updated = outboxAdminEventRepository.resetForAdminRetry(row.eventId(), LocalDateTime.now().minusSeconds(1));
        if (updated != 1) {
            return AdminImOutboxBatchRetryResponse.Item.skipped(
                row.eventId(),
                row.status(),
                "当前outbox事件状态不允许手动重试"
            );
        }
        AdminImOutboxEventRow updatedRow = outboxAdminEventRepository.getOutboxEventOrThrow(row.eventId());
        return AdminImOutboxBatchRetryResponse.Item.succeeded(
            row.eventId(),
            row.status(),
            updatedRow.status()
        );
    }

    private String retryAuditDetail(AdminImOutboxEventRow before, AdminImOutboxEventRow after) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("eventId", before.eventId());
        node.put("eventType", before.eventType());
        node.put("conversationId", before.conversationId());
        if (before.messageId() != null) {
            node.put("messageId", before.messageId());
        }
        node.put("beforeStatus", before.status());
        node.put("afterStatus", after.status());
        node.put("beforeRetryCount", before.retryCount());
        node.put("afterRetryCount", after.retryCount());
        if (before.lastError() != null) {
            node.put("beforeLastError", before.lastError());
        }
        return node.toString();
    }

    private String batchRetryAuditDetail(AdminImOutboxBatchRetryResponse response) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("requestedCount", response.getRequestedCount());
        node.put("succeededCount", response.getSucceededCount());
        node.put("skippedCount", response.getSkippedCount());
        node.putArray("requestedEventIds").addAll(response.getItems().stream()
            .map(AdminImOutboxBatchRetryResponse.Item::getEventId)
            .map(objectMapper.getNodeFactory()::numberNode)
            .toList());
        node.putArray("succeededEventIds").addAll(response.getItems().stream()
            .filter(AdminImOutboxBatchRetryResponse.Item::isSucceeded)
            .map(AdminImOutboxBatchRetryResponse.Item::getEventId)
            .map(objectMapper.getNodeFactory()::numberNode)
            .toList());
        node.putArray("skippedEventIds").addAll(response.getItems().stream()
            .filter(item -> !item.isSucceeded())
            .map(AdminImOutboxBatchRetryResponse.Item::getEventId)
            .map(objectMapper.getNodeFactory()::numberNode)
            .toList());
        return node.toString();
    }

    private String dispatchAuditDetail(int requestedBatchSize, ImWebSocketOutboxDispatchResult result) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("requestedBatchSize", requestedBatchSize);
        node.put("attemptedCount", result.attemptedEventIds().size());
        node.put("dispatchedCount", result.dispatchedCount());
        node.putArray("attemptedEventIds").addAll(result.attemptedEventIds().stream()
            .map(objectMapper.getNodeFactory()::numberNode)
            .toList());
        node.putArray("dispatchedEventIds").addAll(result.dispatchedEventIds().stream()
            .map(objectMapper.getNodeFactory()::numberNode)
            .toList());
        node.putArray("failedEventIds").addAll(result.failedEventIds().stream()
            .map(objectMapper.getNodeFactory()::numberNode)
            .toList());
        return node.toString();
    }
}
