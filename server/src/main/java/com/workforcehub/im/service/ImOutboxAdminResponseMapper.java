package com.workforcehub.im.service;

import com.workforcehub.im.dto.AdminImOutboxEventResponse;
import com.workforcehub.im.repository.AdminImOutboxEventRow;

/**
 * IM outbox 后台响应转换。
 */
final class ImOutboxAdminResponseMapper {

    private ImOutboxAdminResponseMapper() {
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
}
