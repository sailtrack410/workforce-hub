package com.workforcehub.im.service;

import com.workforcehub.common.util.IdGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.agent.service.AgentService;
import com.workforcehub.agent.service.ImAgentDescriptor;
import com.workforcehub.agent.service.ImAgentReplyResult;
import com.workforcehub.agent.service.ImAgentTriggerCommand;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.im.dto.ImConversationSummaryResponse;
import com.workforcehub.im.dto.ImMessageAttachmentResponse;
import com.workforcehub.im.dto.ImMessageResponse;
import com.workforcehub.im.dto.StartAgentConversationRequest;
import com.workforcehub.im.repository.ImAttachmentRow;
import com.workforcehub.im.repository.ImConversationSummaryRow;
import com.workforcehub.im.repository.ImMessageRepository;
import com.workforcehub.im.repository.ImMessageRow;
import com.workforcehub.im.repository.ImRepository;
import com.workforcehub.im.websocket.ImWebSocketEvent;
import com.workforcehub.im.websocket.ImWebSocketEventTypes;
import com.workforcehub.im.websocket.ImWebSocketPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IM 会话中的 Agent 创建、触发和回写服务。
 */
@Service
public class ImAgentConversationService {

    private static final Logger log = LoggerFactory.getLogger(ImAgentConversationService.class);

    private static final String USER_SENDER_TYPE = "USER";
    private static final String AGENT_SENDER_TYPE = "AGENT";
    private static final String SYSTEM_SENDER_TYPE = "SYSTEM";
    private static final long SYSTEM_SENDER_ID = 0L;

    private final ImRepository imRepository;
    private final ImMessageRepository imMessageRepository;
    private final ObjectMapper objectMapper;
    private final ImWebSocketPushService webSocketPushService;
    private final ObjectProvider<AgentService> agentServiceProvider;
    private final TransactionTemplate transactionTemplate;

    public ImAgentConversationService(ImRepository imRepository,
                                      ImMessageRepository imMessageRepository,
                                      ObjectMapper objectMapper,
                                      ImWebSocketPushService webSocketPushService,
                                      ObjectProvider<AgentService> agentServiceProvider,
                                      PlatformTransactionManager transactionManager) {
        this.imRepository = imRepository;
        this.imMessageRepository = imMessageRepository;
        this.objectMapper = objectMapper;
        this.webSocketPushService = webSocketPushService;
        this.agentServiceProvider = agentServiceProvider;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(rollbackFor = Exception.class)
    public ImConversationSummaryResponse startCurrentEmployeeAgentConversation(StartAgentConversationRequest request) {
        Long currentUserId = requireActiveEmployeeUserId();
        ImAgentDescriptor agent = requireAgentService()
            .requireUsableAgent(request.getAgentId(), currentUserId);

        String conversationKey = agentConversationKey(currentUserId, agent.agentId());
        Long conversationId = imRepository.findActiveConversationIdByKey(conversationKey)
            .orElseGet(() -> createAgentConversation(currentUserId, agent, conversationKey));

        return imRepository.findConversationSummaryForUser(conversationId, currentUserId)
            .map(this::toConversationSummaryResponse)
            .orElseThrow(() -> new BizException(500, "Agent会话创建失败"));
    }

    @Transactional(rollbackFor = Exception.class)
    public void onAgentReply(ImAgentReplyResult result) {
        if (result == null) {
            throw new BizException(400, "Agent回复结果不能为空");
        }
        Long conversationId = result.conversationId();
        Long agentId = result.agentId();
        Long agentTaskId = result.agentTaskId();
        if (!imRepository.existsActiveAgentConversation(conversationId, agentId)) {
            throw new BizException(403, "Agent无权写入该会话");
        }
        if ("FAILED".equals(result.status())) {
            writeAgentFailureSystemMessage(conversationId, agentId, agentFailureMessageClientId(result));
            return;
        }
        if (!"SUCCEEDED".equals(result.status())) {
            throw new BizException(400, "Agent回复状态不支持");
        }
        Long lastSeq = imMessageRepository.lockActiveConversationAndGetLastSeq(conversationId);
        if (lastSeq == null) {
            throw new BizException(404, "会话不存在");
        }
        String normalizedMessageType = normalizeAgentMessageType(result.messageType());
        String normalizedContentJson = normalizeAgentContentJson(result.contentJson(), agentTaskId);
        String clientMessageId = agentMessageClientId(result);
        if (clientMessageId != null && imMessageRepository.findMessageByClientMessageId(
            conversationId,
            AGENT_SENDER_TYPE,
            agentId,
            clientMessageId
        ).isPresent()) {
            return;
        }
        Long seqNo = lastSeq + 1;
        Long messageId = IdGenerator.nextId();
        LocalDateTime sentAt = LocalDateTime.now();
        try {
            imMessageRepository.insertMessage(
                messageId,
                conversationId,
                seqNo,
                AGENT_SENDER_TYPE,
                agentId,
                normalizedMessageType,
                clientMessageId,
                normalizedContentJson,
                sentAt
            );
        } catch (DuplicateKeyException e) {
            if (clientMessageId != null) {
                return;
            }
            throw e;
        }
        imMessageRepository.updateConversationLastMessage(conversationId, seqNo, messageId, sentAt, agentId);
        ImMessageResponse response = toMessageResponse(new ImMessageRow(
            messageId,
            conversationId,
            seqNo,
            AGENT_SENDER_TYPE,
            agentId,
            normalizedMessageType,
            normalizedContentJson,
            null,
            "NORMAL",
            sentAt
        ));
        pushConversationEventAfterCommit(
            conversationId,
            ImWebSocketEvent.of(ImWebSocketEventTypes.AGENT_MESSAGE_CREATED, conversationId, messageId, seqNo, agentId, response)
        );
    }

    public void triggerAgentReplyAfterCommitIfNeeded(ImMessageResponse userMessage) {
        if (!USER_SENDER_TYPE.equals(userMessage.getSenderType())
            || !"TEXT".equals(userMessage.getMessageType())) {
            return;
        }
        Long agentId = imRepository.findActiveConversationBoundAgentId(userMessage.getConversationId()).orElse(null);
        if (agentId == null) {
            return;
        }
        Runnable trigger = () -> {
            try {
                AgentService triggerService = requireAgentService();
                triggerService.triggerConversationReply(new ImAgentTriggerCommand(
                    userMessage.getConversationId(),
                    userMessage.getMessageId(),
                    agentId,
                    userMessage.getSenderId(),
                    userMessage.getSenderType(),
                    buildAgentInputContentJson(userMessage),
                    null,
                    agentTriggerIdempotencyKey(userMessage.getMessageId()),
                    "{}"
                ));
            } catch (Exception e) {
                log.warn("IM Agent 会话触发失败 conversationId={} messageId={}",
                    userMessage.getConversationId(),
                    userMessage.getMessageId(),
                    e
                );
                writeAgentFailureSystemMessage(
                    userMessage.getConversationId(),
                    agentId,
                    agentFailureMessageClientId(userMessage.getMessageId())
                );
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    trigger.run();
                }
            });
            return;
        }
        trigger.run();
    }

    private Long requireActiveEmployeeUserId() {
        Long userId = CurrentUserHolder.requireUserId();
        if (!imRepository.existsLoginAllowedOrgUser(userId)) {
            throw new BizException(403, "当前员工状态不允许使用会话");
        }
        return userId;
    }

    private Long createAgentConversation(Long userId,
                                         ImAgentDescriptor agent,
                                         String conversationKey) {
        Long conversationId = IdGenerator.nextId();
        boolean inserted = imRepository.tryInsertAgentConversation(
            conversationId,
            conversationKey,
            agent.agentName(),
            agent.agentId(),
            userId
        );
        if (!inserted) {
            return imRepository.findActiveConversationIdByKey(conversationKey)
                .orElseThrow(() -> new BizException(409, "Agent会话创建冲突"));
        }
        try {
            imRepository.insertUserMember(IdGenerator.nextId(), conversationId, userId, "MEMBER", "AGENT_CHAT");
            imRepository.insertAgentMember(IdGenerator.nextId(), conversationId, agent.agentId(), "AGENT_CHAT");
        } catch (DuplicateKeyException e) {
            return imRepository.findActiveConversationIdByKey(conversationKey)
                .orElseThrow(() -> new BizException(409, "Agent会话成员写入冲突"));
        }
        return conversationId;
    }

    private String agentConversationKey(Long userId, Long agentId) {
        return "AGENT:%d:%d".formatted(userId, agentId);
    }

    private void pushConversationEventAfterCommit(Long conversationId, ImWebSocketEvent event) {
        webSocketPushService.pushToUsersAfterCommit(activeUserMemberIds(conversationId), event);
    }

    private List<Long> activeUserMemberIds(Long conversationId) {
        return imRepository.listPushableActiveUserMemberIds(conversationId);
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
        response.setLastMessage(row.lastMessage() == null ? null : toMessageResponse(row.lastMessage()));
        response.setLastMessageAt(row.lastMessageAt());
        response.setStatus(row.status());
        response.setPin(row.pin());
        response.setMute(row.mute());
        response.setMemberRole(row.memberRole());
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
        if ("RECALLED".equals(row.status())) {
            response.setAttachments(List.of());
        } else {
            response.setAttachments(imRepository.listMessageAttachments(row.messageId())
                .stream()
                .map(this::toAttachmentResponse)
                .toList());
        }
        response.setQuotedMessageId(row.quotedMessageId());
        response.setStatus(row.status());
        response.setSentAt(row.sentAt());
        return response;
    }

    private ImMessageAttachmentResponse toAttachmentResponse(ImAttachmentRow row) {
        ImMessageAttachmentResponse response = new ImMessageAttachmentResponse();
        response.setAttachmentRefId(row.attachmentRefId());
        response.setDisplayName(row.displayName());
        response.setMimeType(row.mimeType());
        response.setFileSize(row.fileSize());
        response.setContentSha256(row.contentSha256());
        response.setScanStatus(row.scanStatus());
        return response;
    }

    private String normalizeAgentMessageType(String messageType) {
        if (!StringUtils.hasText(messageType)) {
            return "TEXT";
        }
        String normalized = messageType.trim().toUpperCase();
        if (!Set.of("TEXT", "CARD", "SYSTEM").contains(normalized)) {
            throw new BizException(400, "Agent消息类型不支持");
        }
        return normalized;
    }

    private String normalizeAgentContentJson(String contentJson, Long agentTaskId) {
        try {
            JsonNode node = objectMapper.readTree(StringUtils.hasText(contentJson) ? contentJson : "{}");
            if (node.has("agentTaskId")) {
                return objectMapper.writeValueAsString(node);
            }
            if (node instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
                objectNode.put("agentTaskId", agentTaskId);
                return objectMapper.writeValueAsString(objectNode);
            }
            return objectMapper.writeValueAsString(Map.of(
                "text", node.asText(""),
                "agentTaskId", agentTaskId
            ));
        } catch (Exception e) {
            throw new BizException(400, "Agent消息内容不是合法JSON");
        }
    }

    private String agentMessageClientId(Long agentTaskId) {
        return agentTaskId == null ? null : "agent-task-" + agentTaskId;
    }

    private String agentMessageClientId(ImAgentReplyResult result) {
        if (StringUtils.hasText(result.idempotencyKey())) {
            return result.idempotencyKey().trim();
        }
        return agentMessageClientId(result.agentTaskId());
    }

    private String agentTriggerIdempotencyKey(Long sourceMessageId) {
        return sourceMessageId == null ? null : "im-message-" + sourceMessageId;
    }

    private String agentFailureMessageClientId(ImAgentReplyResult result) {
        if (StringUtils.hasText(result.idempotencyKey())) {
            return result.idempotencyKey().trim();
        }
        return agentMessageClientId(result.agentTaskId());
    }

    private String agentFailureMessageClientId(Long sourceMessageId) {
        return sourceMessageId == null ? null : "agent-trigger-failure-" + sourceMessageId;
    }

    private String buildAgentInputContentJson(ImMessageResponse userMessage) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "messageId", userMessage.getMessageId(),
                "conversationId", userMessage.getConversationId(),
                "senderId", userMessage.getSenderId(),
                "userId", userMessage.getSenderId(),
                "currentUserId", userMessage.getSenderId(),
                "query", userMessage.getContentText(),
                "text", userMessage.getContentText(),
                "contentJson", userMessage.getContentJson()
            ));
        } catch (Exception e) {
            throw new BizException(500, "Agent输入内容序列化失败");
        }
    }

    private void writeAgentFailureSystemMessage(Long conversationId, Long agentId, String clientMessageId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Long lastSeq = imMessageRepository.lockActiveConversationAndGetLastSeq(conversationId);
                if (lastSeq == null) {
                    throw new BizException(404, "会话不存在");
                }
                if (StringUtils.hasText(clientMessageId)
                    && imMessageRepository.findMessageByClientMessageId(
                        conversationId,
                        SYSTEM_SENDER_TYPE,
                        SYSTEM_SENDER_ID,
                        clientMessageId
                    ).isPresent()) {
                    return;
                }
                Long seqNo = lastSeq + 1;
                Long messageId = IdGenerator.nextId();
                LocalDateTime sentAt = LocalDateTime.now();
                String contentJson = buildSystemContentJson("Agent 暂时无法回复，请稍后再试");
                try {
                    imMessageRepository.insertMessage(
                        messageId,
                        conversationId,
                        seqNo,
                        SYSTEM_SENDER_TYPE,
                        SYSTEM_SENDER_ID,
                        "SYSTEM",
                        clientMessageId,
                        contentJson,
                        sentAt
                    );
                } catch (DuplicateKeyException e) {
                    if (StringUtils.hasText(clientMessageId)) {
                        return;
                    }
                    throw e;
                }
                imMessageRepository.updateConversationLastMessage(conversationId, seqNo, messageId, sentAt, agentId);
                ImMessageResponse response = toMessageResponse(new ImMessageRow(
                    messageId,
                    conversationId,
                    seqNo,
                    SYSTEM_SENDER_TYPE,
                    SYSTEM_SENDER_ID,
                    "SYSTEM",
                    contentJson,
                    null,
                    "NORMAL",
                    sentAt
                ));
                webSocketPushService.pushToUsersAfterCommit(
                    activeUserMemberIds(conversationId),
                    ImWebSocketEvent.of(ImWebSocketEventTypes.AGENT_REPLY_FAILED, conversationId, messageId, seqNo, agentId, response)
                );
            });
        } catch (Exception e) {
            log.warn("IM Agent 失败提示消息写入失败 conversationId={}", conversationId, e);
        }
    }

    private String buildSystemContentJson(String text) {
        try {
            return objectMapper.writeValueAsString(Map.of("text", text));
        } catch (Exception e) {
            throw new BizException(500, "系统消息内容序列化失败");
        }
    }

    private AgentService requireAgentService() {
        AgentService service = agentServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BizException(501, "Agent会话触发服务未接入");
        }
        return service;
    }

    private String extractText(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(contentJson);
            JsonNode textNode = node.get("text");
            if (textNode != null && !textNode.isNull()) {
                return textNode.asText("");
            }
            JsonNode titleNode = node.get("title");
            return titleNode == null || titleNode.isNull() ? "" : titleNode.asText("");
        } catch (Exception e) {
            return "";
        }
    }
}
