package com.workforcehub.im.service;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.response.PageResult;
import com.workforcehub.im.dto.AdminImOutboxAuditQuery;
import com.workforcehub.im.dto.AdminImOutboxAuditResponse;
import com.workforcehub.im.dto.AdminImOutboxEventQuery;
import com.workforcehub.im.dto.AdminImOutboxEventResponse;
import com.workforcehub.im.repository.AdminImOutboxAuditQueryCriteria;
import com.workforcehub.im.repository.AdminImOutboxAuditRow;
import com.workforcehub.im.repository.AdminImOutboxEventQueryCriteria;
import com.workforcehub.im.repository.ImOutboxAdminAuditRepository;
import com.workforcehub.im.repository.ImOutboxAdminEventRepository;
import com.workforcehub.im.support.ImOutboxAdminAuditActions;
import com.workforcehub.im.websocket.ImWebSocketEventTypes;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 后台 IM outbox 查询服务。
 */
@Service
public class ImOutboxAdminQueryService {

    private static final Set<String> OUTBOX_STATUSES = Set.of("PENDING", "SENDING", "SENT", "FAILED", "DEAD");

    private final ImOutboxAdminEventRepository outboxAdminEventRepository;
    private final ImOutboxAdminAuditRepository outboxAdminAuditRepository;

    public ImOutboxAdminQueryService(ImOutboxAdminEventRepository outboxAdminEventRepository,
                                     ImOutboxAdminAuditRepository outboxAdminAuditRepository) {
        this.outboxAdminEventRepository = outboxAdminEventRepository;
        this.outboxAdminAuditRepository = outboxAdminAuditRepository;
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
            .map(ImOutboxAdminResponseMapper::toEventResponse)
            .toList();
        return PageResult.of(safePageNo, safePageSize, total, rows);
    }

    public AdminImOutboxEventResponse getOutboxEvent(Long eventId) {
        return ImOutboxAdminResponseMapper.toEventResponse(outboxAdminEventRepository.getOutboxEventOrThrow(eventId));
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

    private AdminImOutboxAuditResponse toAuditResponse(AdminImOutboxAuditRow row) {
        AdminImOutboxAuditResponse response = new AdminImOutboxAuditResponse();
        response.setAuditId(row.auditId());
        response.setOperatorType(row.operatorType());
        response.setOperatorId(row.operatorId());
        response.setActionType(row.actionType());
        response.setTargetType(row.targetType());
        response.setTargetId(row.targetId());
        response.setResult(row.result());
        response.setDetailJson(row.detailJson());
        response.setOccurredAt(row.occurredAt());
        return response;
    }
}
