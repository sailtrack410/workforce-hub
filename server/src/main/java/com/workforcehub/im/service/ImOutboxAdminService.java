package com.workforcehub.im.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.im.dto.AdminImOutboxAuditQuery;
import com.workforcehub.im.dto.AdminImOutboxAuditResponse;
import com.workforcehub.im.dto.AdminImOutboxBatchRetryRequest;
import com.workforcehub.im.dto.AdminImOutboxBatchRetryResponse;
import com.workforcehub.im.dto.AdminImOutboxDispatchResponse;
import com.workforcehub.im.dto.AdminImOutboxEventQuery;
import com.workforcehub.im.dto.AdminImOutboxEventResponse;
import com.workforcehub.im.dto.AdminImOutboxFailureSummaryResponse;
import com.workforcehub.im.dto.AdminImOutboxSummaryResponse;
import com.workforcehub.im.repository.AdminImOutboxAuditQueryCriteria;
import com.workforcehub.im.repository.AdminImOutboxAuditRow;
import com.workforcehub.im.repository.ImOutboxAdminAuditRepository;
import com.workforcehub.im.repository.ImOutboxAdminEventRepository;
import com.workforcehub.im.repository.AdminImOutboxEventRow;
import com.workforcehub.im.repository.AdminImOutboxEventQueryCriteria;
import com.workforcehub.im.repository.AdminImOutboxFailureSummaryRow;
import com.workforcehub.im.repository.AdminImOutboxStatusSummaryRow;
import com.workforcehub.im.repository.AdminImOutboxSummaryAggregateRow;
import com.workforcehub.im.repository.ImOutboxAdminMetricsRepository;
import com.workforcehub.im.support.ImOutboxAdminAuditActions;
import com.workforcehub.im.websocket.ImWebSocketEventTypes;
import com.workforcehub.im.websocket.ImWebSocketOutboxDispatchResult;
import com.workforcehub.im.websocket.ImWebSocketOutboxDispatcher;
import com.workforcehub.im.websocket.ImWebSocketOutboxProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 后台 IM outbox 治理服务。
 */
// Outbox 治理：消息投递状态审计、失败摘要、批量重试、调度指标查询
@Service
public class ImOutboxAdminService {

    private static final String AUDIT_OPERATOR_TYPE_ADMIN = "ADMIN";
    private static final String AUDIT_TARGET_TYPE_OUTBOX_EVENT = "IM_EVENT_OUTBOX";
    private static final String AUDIT_TARGET_TYPE_OUTBOX_DISPATCH = "IM_EVENT_OUTBOX_DISPATCH";
    private static final String AUDIT_TARGET_TYPE_OUTBOX_BATCH_RETRY = "IM_EVENT_OUTBOX_BATCH_RETRY";
    private static final Set<String> OUTBOX_STATUSES = Set.of("PENDING", "SENDING", "SENT", "FAILED", "DEAD");
    private static final List<String> OUTBOX_STATUS_ORDER = List.of("PENDING", "SENDING", "SENT", "FAILED", "DEAD");
    private static final int FAILURE_SUMMARY_LIMIT = 20;

    private final ImOutboxAdminEventRepository outboxAdminEventRepository;
    private final ImOutboxAdminAuditRepository outboxAdminAuditRepository;
    private final ImOutboxAdminMetricsRepository outboxAdminMetricsRepository;
    private final ImWebSocketOutboxDispatcher outboxDispatcher;
    private final ImWebSocketOutboxProperties outboxProperties;
    private final ObjectMapper objectMapper;

    public ImOutboxAdminService(ImOutboxAdminEventRepository outboxAdminEventRepository,
                                ImOutboxAdminAuditRepository outboxAdminAuditRepository,
                                ImOutboxAdminMetricsRepository outboxAdminMetricsRepository,
                                ImWebSocketOutboxDispatcher outboxDispatcher,
                                ImWebSocketOutboxProperties outboxProperties,
                                ObjectMapper objectMapper) {
        this.outboxAdminEventRepository = outboxAdminEventRepository;
        this.outboxAdminAuditRepository = outboxAdminAuditRepository;
        this.outboxAdminMetricsRepository = outboxAdminMetricsRepository;
        this.outboxDispatcher = outboxDispatcher;
        this.outboxProperties = outboxProperties;
        this.objectMapper = objectMapper;
    }

    public PageResult<AdminImOutboxEventResponse> pageOutboxEvents(AdminImOutboxEventQuery query) {
        long safePageNo = Math.max(query.getPageNo(), 1L);
        long safePageSize = Math.min(Math.max(query.getPageSize(), 1L), 100L);
        AdminImOutboxEventQueryCriteria criteria = toCriteria(query);
        long total = outboxAdminEventRepository.countOutboxEvents(criteria);
        List<AdminImOutboxEventResponse> rows = outboxAdminEventRepository.pageOutboxEvents(
                criteria,
                (safePageNo - 1) * safePageSize,
                safePageSize
            ).stream()
            .map(this::toEventResponse)
            .toList();
        return PageResult.of(safePageNo, safePageSize, total, rows);
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
            .map(status -> AdminImOutboxSummaryResponse.StatusCount.of(status, statusName(status), countStatus(rows, status)))
            .toList());
        return response;
    }

    public List<AdminImOutboxFailureSummaryResponse> listOutboxFailureSummary() {
        return outboxAdminMetricsRepository.listOutboxFailureSummary(FAILURE_SUMMARY_LIMIT).stream()
            .map(this::toFailureSummaryResponse)
            .toList();
    }

    public PageResult<AdminImOutboxAuditResponse> pageOutboxAudits(AdminImOutboxAuditQuery query) {
        long safePageNo = Math.max(query.getPageNo(), 1L);
        long safePageSize = Math.min(Math.max(query.getPageSize(), 1L), 100L);
        AdminImOutboxAuditQueryCriteria criteria = toAuditCriteria(query);
        long total = outboxAdminAuditRepository.countOutboxAudits(criteria);
        List<AdminImOutboxAuditResponse> rows = outboxAdminAuditRepository.pageOutboxAudits(
                criteria,
                (safePageNo - 1) * safePageSize,
                safePageSize
            ).stream()
            .map(this::toAuditResponse)
            .toList();
        return PageResult.of(safePageNo, safePageSize, total, rows);
    }

    public AdminImOutboxEventResponse getOutboxEvent(Long eventId) {
        return toEventResponse(outboxAdminEventRepository.getOutboxEventOrThrow(eventId));
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

    private AdminImOutboxEventQueryCriteria toCriteria(AdminImOutboxEventQuery query) {
        if (query.getCreatedFrom() != null && query.getCreatedTo() != null
            && query.getCreatedFrom().isAfter(query.getCreatedTo())) {
            throw new BizException(400, "创建时间起始不能晚于结束");
        }
        return new AdminImOutboxEventQueryCriteria(
            normalizeIn(query.getStatus(), OUTBOX_STATUSES, "outbox事件状态不支持"),
            normalizeIn(query.getEventType(), ImWebSocketEventTypes.OUTBOX_EVENT_TYPES, "outbox事件类型不支持"),
            query.getConversationId(),
            query.getMessageId(),
            query.getActorUserId(),
            query.getCreatedFrom(),
            query.getCreatedTo()
        );
    }

    private AdminImOutboxAuditQueryCriteria toAuditCriteria(AdminImOutboxAuditQuery query) {
        if (query.getOccurredFrom() != null && query.getOccurredTo() != null
            && query.getOccurredFrom().isAfter(query.getOccurredTo())) {
            throw new BizException(400, "发生时间起始不能晚于结束");
        }
        return new AdminImOutboxAuditQueryCriteria(
            normalizeIn(query.getActionType(), ImOutboxAdminAuditActions.SUPPORTED_ACTION_TYPES, "outbox审计动作不支持"),
            query.getEventId(),
            query.getOperatorId(),
            query.getOccurredFrom(),
            query.getOccurredTo()
        );
    }

    private String normalizeIn(String value, Set<String> candidates, String message) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!candidates.contains(normalized)) {
            throw new BizException(400, message);
        }
        return normalized;
    }

    AdminImOutboxEventResponse toEventResponse(AdminImOutboxEventRow row) {
        AdminImOutboxEventResponse response = new AdminImOutboxEventResponse();
        response.setEventId(row.eventId());
        response.setEventType(row.eventType());
        response.setEventTypeName(eventTypeName(row.eventType()));
        response.setConversationId(row.conversationId());
        response.setMessageId(row.messageId());
        response.setSeqNo(row.seqNo());
        response.setActorUserId(row.actorUserId());
        response.setActorName(row.actorUserId() == null ? null : "成员");
        response.setRecipientUserIdsJson(row.recipientUserIdsJson());
        response.setPayloadJson(row.payloadJson());
        response.setStatus(row.status());
        response.setStatusName(statusName(row.status()));
        response.setRetryCount(row.retryCount());
        response.setNextRetryAt(row.nextRetryAt());
        response.setLastError(row.lastError());
        response.setClaimedAt(row.claimedAt());
        response.setSentAt(row.sentAt());
        response.setCreatedAt(row.createdAt());
        response.setUpdatedAt(row.updatedAt());
        return response;
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
        response.setStatusName(statusName(row.status()));
        response.setLastError(row.lastError());
        response.setEventCount(row.eventCount());
        response.setMaxRetryCount(row.maxRetryCount());
        response.setFirstOccurredAt(row.firstOccurredAt());
        response.setLastOccurredAt(row.lastOccurredAt());
        return response;
    }

    private AdminImOutboxAuditResponse toAuditResponse(AdminImOutboxAuditRow row) {
        AdminImOutboxAuditResponse response = new AdminImOutboxAuditResponse();
        response.setAuditId(row.auditId());
        response.setOperatorType(row.operatorType());
        response.setOperatorTypeName(actorTypeName(row.operatorType()));
        response.setOperatorId(row.operatorId());
        response.setOperatorName(row.operatorId() == null ? null : actorTypeName(row.operatorType()));
        response.setActionType(row.actionType());
        response.setActionTypeName(actionTypeName(row.actionType()));
        response.setTargetType(row.targetType());
        response.setTargetTypeName(targetTypeName(row.targetType()));
        response.setTargetId(row.targetId());
        response.setTargetDisplayName(row.targetId() == null ? null : targetTypeName(row.targetType()));
        response.setResult(row.result());
        response.setResultName(statusName(row.result()));
        response.setDetailJson(row.detailJson());
        response.setOccurredAt(row.occurredAt());
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

    private String statusName(String value) {
        if ("PENDING".equals(value)) return "待发送";
        if ("SENDING".equals(value)) return "发送中";
        if ("SENT".equals(value)) return "已发送";
        if ("FAILED".equals(value)) return "失败";
        if ("DEAD".equals(value)) return "终止";
        if ("SUCCESS".equals(value)) return "成功";
        return value == null ? null : "其他状态";
    }

    private String eventTypeName(String value) {
        if ("MESSAGE_CREATED".equals(value)) return "消息发送";
        if ("MESSAGE_RECALLED".equals(value)) return "消息撤回";
        if ("MESSAGE_EDITED".equals(value)) return "消息编辑";
        if ("GROUP_PROFILE_UPDATED".equals(value)) return "群资料更新";
        if ("AGENT_MESSAGE_CREATED".equals(value)) return "AI 回复";
        if ("AGENT_REPLY_FAILED".equals(value)) return "AI 回复失败";
        return value == null ? null : "消息事件";
    }

    private String actorTypeName(String value) {
        if ("USER".equals(value)) return "员工";
        if ("ADMIN".equals(value) || "ADMIN_USER".equals(value)) return "管理员";
        if ("SYSTEM".equals(value)) return "系统";
        if ("AGENT".equals(value)) return "AI 助手";
        return value == null ? null : "操作人";
    }

    private String actionTypeName(String value) {
        if ("RETRY".equals(value)) return "重试";
        if ("BATCH_RETRY".equals(value)) return "批量重试";
        if ("DISPATCH".equals(value)) return "发送";
        return value == null ? null : "操作";
    }

    private String targetTypeName(String value) {
        if ("IM_OUTBOX_EVENT".equals(value)) return "消息事件";
        if ("IM_OUTBOX".equals(value)) return "消息队列";
        return value == null ? null : "业务对象";
    }
}
