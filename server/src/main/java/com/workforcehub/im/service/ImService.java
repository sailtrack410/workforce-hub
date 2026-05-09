package com.workforcehub.im.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.im.dto.ImConversationSummaryResponse;
import com.workforcehub.im.dto.ImMessageResponse;
import com.workforcehub.im.repository.ImConversationSummaryRow;
import com.workforcehub.im.repository.ImMessageRow;
import com.workforcehub.im.repository.ImRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * module-im 员工端会话读取服务。
 */
@Service
public class ImService {

    private final ImRepository imRepository;
    private final ObjectMapper objectMapper;

    public ImService(ImRepository imRepository, ObjectMapper objectMapper) {
        this.imRepository = imRepository;
        this.objectMapper = objectMapper;
    }

    public List<ImConversationSummaryResponse> listCurrentEmployeeConversations() {
        Long userId = CurrentUserHolder.requireUserId();
        return imRepository.listActiveConversationsForUser(userId)
            .stream()
            .map(this::toConversationSummaryResponse)
            .toList();
    }

    public List<ImMessageResponse> listCurrentEmployeeMessages(Long conversationId) {
        Long userId = CurrentUserHolder.requireUserId();
        if (!imRepository.existsActiveUserMembership(conversationId, userId)) {
            if (imRepository.existsConversation(conversationId)) {
                throw new BizException(403, "无权访问该会话");
            }
            throw new BizException(404, "会话不存在");
        }
        return imRepository.listMessages(conversationId)
            .stream()
            .map(this::toMessageResponse)
            .toList();
    }

    private ImConversationSummaryResponse toConversationSummaryResponse(ImConversationSummaryRow row) {
        ImConversationSummaryResponse response = new ImConversationSummaryResponse();
        response.setConversationId(row.conversationId());
        response.setConversationType(row.conversationType());
        response.setConversationKey(row.conversationKey());
        response.setTitle(row.title());
        response.setAvatarFileId(row.avatarFileId());
        response.setBoundAgentId(row.boundAgentId());
        response.setLastMessageSeq(row.lastMessageSeq());
        response.setLastReadSeq(row.lastReadSeq());
        response.setUnreadCount(row.unreadCount());
        response.setLastMessageAt(row.lastMessageAt());
        response.setStatus(row.status());
        response.setPin(row.pin());
        response.setMute(row.mute());
        response.setMemberRole(row.memberRole());
        response.setLastMessage(row.lastMessage() == null ? null : toMessageResponse(row.lastMessage()));
        return response;
    }

    private ImMessageResponse toMessageResponse(ImMessageRow row) {
        ImMessageResponse response = new ImMessageResponse();
        response.setMessageId(row.messageId());
        response.setConversationId(row.conversationId());
        response.setSeqNo(row.seqNo());
        response.setSenderType(row.senderType());
        response.setSenderId(row.senderId());
        response.setMessageType(row.messageType());
        response.setContentJson(row.contentJson());
        response.setContentText(extractText(row.contentJson()));
        response.setQuotedMessageId(row.quotedMessageId());
        response.setStatus(row.status());
        response.setSentAt(row.sentAt());
        return response;
    }

    private String extractText(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(contentJson);
            JsonNode textNode = node.get("text");
            return textNode == null || textNode.isNull() ? "" : textNode.asText("");
        } catch (Exception ignored) {
            return "";
        }
    }
}
