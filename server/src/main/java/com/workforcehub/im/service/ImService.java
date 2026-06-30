package com.workforcehub.im.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.workforcehub.agent.model.AgentModelClient;
import com.workforcehub.agent.model.AgentModelRequest;
import com.workforcehub.agent.repository.AgentRepository;
import com.workforcehub.agent.repository.LlmModelRow;
import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.file.dto.CreateDownloadTicketRequest;
import com.workforcehub.file.dto.DownloadFileResult;
import com.workforcehub.file.dto.DownloadTicketResponse;
import com.workforcehub.file.repository.DownloadAttachmentRow;
import com.workforcehub.file.repository.AttachmentRefRepository;
import com.workforcehub.file.service.DownloadTicketExecutionService;
import com.workforcehub.file.storage.StorageObjectResource;
import com.workforcehub.file.storage.StorageProviderRegistry;
import com.workforcehub.im.dto.EditImMessageRequest;
import com.workforcehub.im.dto.ForwardImMessageRequest;
import com.workforcehub.im.dto.ImConversationDetailResponse;
import com.workforcehub.im.dto.ImMessageActionResponse;
import com.workforcehub.im.dto.ImMessageAttachmentResponse;
import com.workforcehub.im.dto.ImConversationSummaryResponse;
import com.workforcehub.im.dto.ImMessageResponse;
import com.workforcehub.im.dto.RemindImMessageRequest;
import com.workforcehub.im.dto.SendImMessageRequest;
import com.workforcehub.im.dto.StartAgentConversationRequest;
import com.workforcehub.im.dto.StartPrivateConversationRequest;
import com.workforcehub.im.dto.TranslateImMessageResponse;
import com.workforcehub.im.dto.UpdateConversationPreferenceRequest;
import com.workforcehub.im.dto.UpdateReadPositionRequest;
import com.workforcehub.im.repository.ImAttachmentRow;
import com.workforcehub.im.repository.ImConversationSummaryRow;
import com.workforcehub.im.repository.ImMessageRepository;
import com.workforcehub.im.repository.ImMessageRow;
import com.workforcehub.im.repository.ImRepository;
import com.workforcehub.im.repository.ImUserRelationRow;
import com.workforcehub.im.websocket.ImWebSocketEvent;
import com.workforcehub.im.websocket.ImWebSocketEventTypes;
import com.workforcehub.im.websocket.ImWebSocketPushService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.workforcehub.common.util.TextValues.blankToNull;

/**
 * module-im 员工端会话读取服务。
 */
@Service
public class ImService {

    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 50;
    private static final int MAX_MESSAGE_PAGE_SIZE = 100;
    private static final int DEFAULT_CONVERSATION_PAGE_SIZE = 30;
    private static final int MAX_CONVERSATION_PAGE_SIZE = 100;
    private static final String USER_SENDER_TYPE = "USER";

    private final ImRepository imRepository;
    private final ImMessageRepository imMessageRepository;
    private final ObjectMapper objectMapper;
    private final AttachmentRefRepository attachmentRefRepository;
    private final DownloadTicketExecutionService downloadTicketExecutionService;
    private final StorageProviderRegistry storageProviderRegistry;
    private final ImWebSocketPushService webSocketPushService;
    private final ImAgentConversationService imAgentConversationService;
    private final ImGroupService imGroupService;
    private final AgentRepository agentRepository;
    private final AgentModelClient agentModelClient;

    public ImService(ImRepository imRepository,
                     ImMessageRepository imMessageRepository,
                     ObjectMapper objectMapper,
                     AttachmentRefRepository attachmentRefRepository,
                     DownloadTicketExecutionService downloadTicketExecutionService,
                     StorageProviderRegistry storageProviderRegistry,
                     ImWebSocketPushService webSocketPushService,
                     ImAgentConversationService imAgentConversationService,
                     ImGroupService imGroupService,
                     AgentRepository agentRepository,
                     AgentModelClient agentModelClient) {
        this.imRepository = imRepository;
        this.imMessageRepository = imMessageRepository;
        this.objectMapper = objectMapper;
        this.attachmentRefRepository = attachmentRefRepository;
        this.downloadTicketExecutionService = downloadTicketExecutionService;
        this.storageProviderRegistry = storageProviderRegistry;
        this.webSocketPushService = webSocketPushService;
        this.imAgentConversationService = imAgentConversationService;
        this.imGroupService = imGroupService;
        this.agentRepository = agentRepository;
        this.agentModelClient = agentModelClient;
    }

    public List<ImConversationSummaryResponse> listCurrentEmployeeConversations(String beforeLastMessageAt,
                                                                                 Long beforeConversationId,
                                                                                 Integer size) {
        Long userId = requireActiveEmployeeUserId();
        LocalDateTime beforeLastMessageAtValue = parseConversationCursorTime(beforeLastMessageAt);
        if ((beforeLastMessageAtValue == null) != (beforeConversationId == null)) {
            throw new BizException(400, "会话分页游标必须同时包含时间和会话ID");
        }
        return imRepository.listActiveConversationsForUser(
                userId,
                beforeLastMessageAtValue,
                beforeConversationId,
                normalizeConversationPageSize(size)
            )
            .stream()
            .map(this::toConversationSummaryResponse)
            .toList();
    }

    public List<ImMessageResponse> listCurrentEmployeeMessages(Long conversationId, Long beforeSeq, Integer size) {
        Long userId = requireActiveEmployeeUserId();
        if (!imRepository.existsActiveUserMembership(conversationId, userId)) {
            if (imRepository.existsConversation(conversationId)) {
                throw new BizException(403, "无权访问该会话");
            }
            throw new BizException(404, "会话不存在");
        }
        return imMessageRepository.listMessages(conversationId, userId, beforeSeq, normalizeMessagePageSize(size))
            .stream()
            .map(this::toMessageResponse)
            .toList();
    }

    public ImConversationDetailResponse getCurrentEmployeeConversationDetail(Long conversationId) {
        Long userId = requireActiveEmployeeUserId();
        ImConversationSummaryResponse conversation = imRepository.findConversationSummaryForUser(conversationId, userId)
            .map(this::toConversationSummaryResponse)
            .orElseGet(() -> {
                if (imRepository.existsConversation(conversationId)) {
                    throw new BizException(403, "无权访问该会话");
                }
                throw new BizException(404, "会话不存在");
            });

        ImConversationDetailResponse response = new ImConversationDetailResponse();
        response.setConversation(conversation);
        response.setMembers(imGroupService.listActiveConversationMemberResponses(conversationId, true));
        if ("GROUP".equals(conversation.getConversationType())) {
            response.setGroupProfile(imGroupService.getGroupProfileForUser(conversationId, userId));
        }
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImConversationSummaryResponse startCurrentEmployeePrivateConversation(StartPrivateConversationRequest request) {
        Long currentUserId = requireActiveEmployeeUserId();
        Long targetUserId = request.getTargetUserId();
        if (currentUserId.equals(targetUserId)) {
            throw new BizException(400, "不能和自己发起私聊");
        }

        ImUserRelationRow currentUser = imRepository.findActiveOrgUser(currentUserId)
            .orElseThrow(() -> new BizException(403, "当前员工不可发起私聊"));
        ImUserRelationRow targetUser = imRepository.findActiveOrgUser(targetUserId)
            .orElseThrow(() -> new BizException(404, "目标用户不存在"));
        if (!hasDirectManagerRelation(currentUser, targetUser)
            && !imRepository.existsSharedActiveGroupMembership(currentUserId, targetUserId)) {
            throw new BizException(403, "只能和直属上级、直属下级或同群成员发起私聊");
        }

        String conversationKey = privateConversationKey(currentUserId, targetUserId);
        Long conversationId = imRepository.findActiveConversationIdByKey(conversationKey)
            .orElseGet(() -> createPrivateConversation(currentUser, targetUser, conversationKey));

        return imRepository.findConversationSummaryForUser(conversationId, currentUserId)
            .map(this::toConversationSummaryResponse)
            .orElseThrow(() -> new BizException(500, "私聊会话创建失败"));
    }

    @Transactional(rollbackFor = Exception.class)
    public ImConversationSummaryResponse startCurrentEmployeeAgentConversation(StartAgentConversationRequest request) {
        return imAgentConversationService.startCurrentEmployeeAgentConversation(request);
    }

    @Transactional(rollbackFor = Exception.class)
    public ImMessageResponse sendCurrentEmployeeMessage(Long conversationId, SendImMessageRequest request) {
        Long userId = requireActiveEmployeeUserId();
        ensureCanAccessConversation(conversationId, userId);
        String text = normalizeText(request.getText());
        List<Long> attachmentRefIds = normalizeAttachmentRefIds(request.getAttachmentRefIds());
        if (!StringUtils.hasText(text) && attachmentRefIds.isEmpty()) {
            throw new BizException(400, "消息内容不能为空");
        }
        Long lastSeq = imMessageRepository.lockActiveConversationAndGetLastSeq(conversationId);
        if (lastSeq == null) {
            throw new BizException(404, "会话不存在");
        }

        String messageType = normalizeMessageType(request.getMessageType(), StringUtils.hasText(text), !attachmentRefIds.isEmpty());
        String clientMessageId = normalizeClientMessageId(request.getClientMessageId());
        Long quotedMessageId = normalizeQuotedMessageId(conversationId, request.getQuotedMessageId());
        if (clientMessageId != null) {
            ImMessageResponse existing = imMessageRepository.findMessageByClientMessageId(
                conversationId,
                USER_SENDER_TYPE,
                userId,
                clientMessageId
            ).map(this::toMessageResponse).orElse(null);
            if (existing != null) {
                imMessageRepository.updateMemberLastReadSeq(conversationId, userId, existing.getSeqNo());
                return existing;
            }
        }
        List<ImAttachmentRow> sourceAttachments = attachmentRefIds.stream()
            .map(attachmentRefId -> imRepository.findAttachmentById(attachmentRefId)
                .orElseThrow(() -> new BizException(404, "附件引用不存在")))
            .peek(attachment -> ensureUsableSourceAttachment(attachment, userId))
            .toList();
        Long seqNo = lastSeq + 1;
        Long messageId = IdGenerator.nextId();
        LocalDateTime sentAt = LocalDateTime.now();
        String contentJson = buildContentJson(text, sourceAttachments);
        try {
            imMessageRepository.insertMessage(
                messageId,
                conversationId,
                seqNo,
                USER_SENDER_TYPE,
                userId,
                messageType,
                clientMessageId,
                quotedMessageId,
                contentJson,
                sentAt
            );
        } catch (DuplicateKeyException e) {
            if (clientMessageId == null) {
                throw e;
            }
            ImMessageResponse existing = imMessageRepository.findMessageByClientMessageId(
                conversationId,
                USER_SENDER_TYPE,
                userId,
                clientMessageId
            ).map(this::toMessageResponse).orElseThrow(() -> new BizException(409, "消息幂等写入冲突"));
            imMessageRepository.updateMemberLastReadSeq(conversationId, userId, existing.getSeqNo());
            return existing;
        }
        for (int i = 0; i < sourceAttachments.size(); i++) {
            imRepository.createImMessageAttachmentRef(messageId, sourceAttachments.get(i), userId, i);
        }
        imMessageRepository.updateConversationLastMessage(conversationId, seqNo, messageId, sentAt, userId);
        imMessageRepository.updateMemberLastReadSeq(conversationId, userId, seqNo);

        ImMessageResponse response = toMessageResponse(new ImMessageRow(
            messageId,
            conversationId,
            seqNo,
            USER_SENDER_TYPE,
            userId,
            messageType,
            contentJson,
            quotedMessageId,
            "NORMAL",
            sentAt
        ));
        pushConversationEventAfterCommit(
            conversationId,
            ImWebSocketEvent.of(ImWebSocketEventTypes.MESSAGE_CREATED, conversationId, messageId, seqNo, userId, response)
        );
        imAgentConversationService.triggerAgentReplyAfterCommitIfNeeded(response);
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImMessageResponse editCurrentEmployeeMessage(Long conversationId,
                                                        Long messageId,
                                                        EditImMessageRequest request) {
        Long userId = requireActiveEmployeeUserId();
        ensureCanAccessConversation(conversationId, userId);
        ImMessageRow message = imMessageRepository.findMessageById(messageId)
            .orElseThrow(() -> new BizException(404, "消息不存在"));
        ensureEditableMessage(message, conversationId, userId);

        String text = normalizeText(request.getText());
        if (!StringUtils.hasText(text)) {
            throw new BizException(400, "消息内容不能为空");
        }
        String contentJson = buildContentJson(text, imRepository.listMessageAttachments(messageId));
        int updated = imMessageRepository.updateMessageContent(messageId, contentJson, LocalDateTime.now());
        if (updated == 0) {
            throw new BizException(409, "当前消息状态不允许编辑");
        }
        ImMessageResponse response = imMessageRepository.findMessageById(messageId)
            .map(this::toMessageResponse)
            .orElseThrow(() -> new BizException(404, "消息不存在"));
        pushConversationEventAfterCommit(
            conversationId,
            ImWebSocketEvent.of(ImWebSocketEventTypes.MESSAGE_EDITED, conversationId, messageId, response.getSeqNo(), userId, response)
        );
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImMessageResponse recallCurrentEmployeeMessage(Long conversationId, Long messageId) {
        Long userId = requireActiveEmployeeUserId();
        ensureCanAccessConversation(conversationId, userId);
        ImMessageRow message = imMessageRepository.findMessageById(messageId)
            .orElseThrow(() -> new BizException(404, "消息不存在"));
        ensureRecallableMessage(message, conversationId, userId);

        String contentJson = buildRecallContentJson();
        int updated = imMessageRepository.recallMessage(messageId, contentJson, LocalDateTime.now());
        if (updated == 0) {
            throw new BizException(409, "当前消息状态不允许撤回");
        }
        ImMessageResponse response = imMessageRepository.findMessageById(messageId)
            .map(this::toMessageResponse)
            .orElseThrow(() -> new BizException(404, "消息不存在"));
        pushConversationEventAfterCommit(
            conversationId,
            ImWebSocketEvent.of(ImWebSocketEventTypes.MESSAGE_RECALLED, conversationId, messageId, response.getSeqNo(), userId, response)
        );
        return response;
    }

    public TranslateImMessageResponse translateCurrentEmployeeMessage(Long conversationId, Long messageId) {
        Long userId = requireActiveEmployeeUserId();
        ImMessageRow message = requireReadableMessage(conversationId, messageId, userId);
        String sourceText = extractText(message.contentJson());
        if (!StringUtils.hasText(sourceText)) {
            throw new BizException(400, "当前消息无可翻译文本");
        }
        LlmModelRow model = firstActiveLlmModel();
        if (model == null) {
            throw new BizException(409, "未配置可用AI模型");
        }
        try {
            String modelConfigJson = objectMapper.writeValueAsString(Map.of(
                "baseUrl", model.endpoint() == null ? "" : model.endpoint(),
                "apiKey", model.apiKey(),
                "model", model.modelName()
            ));
            String translatedText = agentModelClient.generateReply(new AgentModelRequest(
                0L,
                0L,
                "message-translate",
                "你是企业即时消息翻译助手。只输出中文译文，不要解释。",
                "请翻译以下消息：\n" + sourceText,
                modelConfigJson
            )).text();
            return new TranslateImMessageResponse(messageId, sourceText, translatedText);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(502, "AI翻译失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ImMessageResponse forwardCurrentEmployeeMessage(Long conversationId,
                                                           Long messageId,
                                                           ForwardImMessageRequest request) {
        Long userId = requireActiveEmployeeUserId();
        ImMessageRow message = requireReadableMessage(conversationId, messageId, userId);
        if (request.getTargetConversationId() == null) {
            throw new BizException(400, "目标会话不能为空");
        }
        ensureCanAccessConversation(request.getTargetConversationId(), userId);
        String text = extractText(message.contentJson());
        List<ImAttachmentRow> sourceAttachments = imRepository.listMessageAttachments(messageId);
        sourceAttachments.forEach(attachment -> ensureDownloadableMessageAttachment(attachment, messageId));
        if (!StringUtils.hasText(text) && sourceAttachments.isEmpty()) {
            throw new BizException(400, "当前消息无可转发内容");
        }
        Long lastSeq = imMessageRepository.lockActiveConversationAndGetLastSeq(request.getTargetConversationId());
        if (lastSeq == null) {
            throw new BizException(404, "会话不存在");
        }
        Long seqNo = lastSeq + 1;
        Long forwardedMessageId = IdWorker.getId();
        LocalDateTime sentAt = LocalDateTime.now();
        String messageType = sourceAttachments.isEmpty() || StringUtils.hasText(text) ? "TEXT" : "FILE";
        String contentJson = buildContentJson(text, sourceAttachments);
        imMessageRepository.insertMessage(
            forwardedMessageId,
            request.getTargetConversationId(),
            seqNo,
            USER_SENDER_TYPE,
            userId,
            messageType,
            "forward-%s-%s-%s".formatted(request.getTargetConversationId(), messageId, System.currentTimeMillis()),
            null,
            contentJson,
            sentAt
        );
        for (int i = 0; i < sourceAttachments.size(); i++) {
            imRepository.createImMessageAttachmentRef(forwardedMessageId, sourceAttachments.get(i), userId, i);
        }
        imMessageRepository.updateConversationLastMessage(request.getTargetConversationId(), seqNo, forwardedMessageId, sentAt, userId);
        imMessageRepository.updateMemberLastReadSeq(request.getTargetConversationId(), userId, seqNo);
        ImMessageResponse response = toMessageResponse(new ImMessageRow(
            forwardedMessageId,
            request.getTargetConversationId(),
            seqNo,
            USER_SENDER_TYPE,
            userId,
            messageType,
            contentJson,
            null,
            "NORMAL",
            sentAt
        ));
        pushConversationEventAfterCommit(
            request.getTargetConversationId(),
            ImWebSocketEvent.of(ImWebSocketEventTypes.MESSAGE_CREATED, request.getTargetConversationId(), forwardedMessageId, seqNo, userId, response)
        );
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImMessageActionResponse favoriteCurrentEmployeeMessage(Long conversationId, Long messageId) {
        Long userId = requireActiveEmployeeUserId();
        requireReadableMessage(conversationId, messageId, userId);
        imMessageRepository.upsertUserAction(IdWorker.getId(), conversationId, messageId, userId, "FAVORITE", "{}", null);
        return new ImMessageActionResponse(messageId, "FAVORITE", "ACTIVE");
    }

    @Transactional(rollbackFor = Exception.class)
    public ImMessageActionResponse remindCurrentEmployeeMessage(Long conversationId,
                                                                Long messageId,
                                                                RemindImMessageRequest request) {
        Long userId = requireActiveEmployeeUserId();
        requireReadableMessage(conversationId, messageId, userId);
        String note = blankToNull(request.getNote());
        String payloadJson = writeJson(Map.of("note", note == null ? "" : note));
        imMessageRepository.upsertUserAction(IdWorker.getId(), conversationId, messageId, userId, "REMINDER", payloadJson, request.getRemindAt());
        return new ImMessageActionResponse(messageId, "REMINDER", "ACTIVE");
    }

    @Transactional(rollbackFor = Exception.class)
    public ImMessageActionResponse deleteCurrentEmployeeMessage(Long conversationId, Long messageId) {
        Long userId = requireActiveEmployeeUserId();
        requireReadableMessage(conversationId, messageId, userId);
        imMessageRepository.upsertUserAction(IdWorker.getId(), conversationId, messageId, userId, "DELETE", "{}", null);
        return new ImMessageActionResponse(messageId, "DELETE", "ACTIVE");
    }

    @Transactional(rollbackFor = Exception.class)
    public DownloadTicketResponse createCurrentEmployeeMessageAttachmentDownloadTicket(Long conversationId,
                                                                                       Long messageId,
                                                                                       Long attachmentRefId,
                                                                                       CreateDownloadTicketRequest request) {
        Long userId = requireActiveEmployeeUserId();
        ensureCanAccessConversation(conversationId, userId);
        ImMessageRow message = imMessageRepository.findMessageById(messageId)
            .orElseThrow(() -> new BizException(404, "消息不存在"));
        if (!conversationId.equals(message.conversationId())) {
            throw new BizException(404, "消息不存在");
        }
        ensureMessageAllowsAttachmentDownload(message);
        if (!"SINGLE".equalsIgnoreCase(request.getDownloadType())) {
            throw new BizException(400, "第一版仅支持单文件下载");
        }

        ImAttachmentRow attachment = imRepository.findAttachmentById(attachmentRefId)
            .orElseThrow(() -> new BizException(404, "消息附件不存在"));
        ensureDownloadableMessageAttachment(attachment, messageId);

        Long ticketId = IdGenerator.nextId();
        String ticketNo = "IMDL" + ticketId;
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(10);
        String downloadUrl = "/api/employee/im/attachments/download/" + ticketNo;
        imRepository.insertDownloadTicket(
            ticketId,
            ticketNo,
            attachmentRefId,
            userId,
            downloadUrl,
            blankToNull(request.getReason()),
            expireAt
        );
        imRepository.insertAudit(
            "USER",
            userId,
            "IM_ATTACHMENT_DOWNLOAD_TICKET_CREATED",
            "DOWNLOAD_TICKET",
            ticketId,
            "SUCCESS",
            "{\"conversationId\":%s,\"messageId\":%s,\"attachmentRefId\":%s}".formatted(
                conversationId,
                messageId,
                attachmentRefId
            )
        );

        DownloadTicketResponse response = new DownloadTicketResponse();
        response.setDownloadTicketId(ticketId);
        response.setTicketNo(ticketNo);
        response.setDownloadUrl(downloadUrl);
        response.setExpireAt(expireAt);
        response.setFileName(attachment.displayName());
        response.setFileSize(attachment.fileSize());
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public DownloadFileResult downloadCurrentEmployeeAttachment(String ticketNo) {
        Long userId = requireActiveEmployeeUserId();
        ImRepository.ImDownloadTicketRow ticket = imRepository.findDownloadTicket(ticketNo)
            .orElseThrow(() -> new BizException(404, "下载票据不存在"));
        if (!"USER".equals(ticket.requesterType()) || !userId.equals(ticket.requesterId())) {
            throw new BizException(403, "无权使用该下载票据");
        }
        if (!"ACTIVE".equals(ticket.status())) {
            throw new BizException(409, "下载票据已使用或已失效");
        }
        if (ticket.expireAt().isBefore(LocalDateTime.now())) {
            downloadTicketExecutionService.expireActiveTicket(ticket.id());
            throw new BizException(410, "下载链接已过期，请重新申请");
        }

        ImAttachmentRow attachment = imRepository.findAttachmentById(ticket.attachmentRefId())
            .orElseThrow(() -> new BizException(404, "消息附件不存在"));
        if ("IM_GROUP_FILE".equals(attachment.ownerType())) {
            return imGroupService.downloadCurrentEmployeeGroupOrMessageAttachment(ticketNo);
        }
        if (!"IM_MESSAGE".equals(attachment.ownerType()) || attachment.ownerId() == null) {
            throw new BizException(404, "消息附件不存在");
        }
        ImMessageRow message = imMessageRepository.findMessageById(attachment.ownerId())
            .orElseThrow(() -> new BizException(404, "消息不存在"));
        ensureCanAccessConversation(message.conversationId(), userId);
        ensureMessageAllowsAttachmentDownload(message);
        ensureDownloadableMessageAttachment(attachment, message.messageId());

        DownloadAttachmentRow downloadAttachment = attachmentRefRepository.getDownloadAttachmentOrThrow(ticket.attachmentRefId());
        ensureDownloadableMessageAttachment(downloadAttachment, message.messageId());
        downloadTicketExecutionService.claimActiveTicket(ticket.id());
        StorageObjectResource resource = storageProviderRegistry.require(downloadAttachment.storageProvider())
            .loadObject(downloadAttachment.bucketName(), downloadAttachment.objectKey());
        imRepository.insertAudit(
            "USER",
            userId,
            "IM_ATTACHMENT_DOWNLOAD_EXECUTED",
            "DOWNLOAD_TICKET",
            ticket.id(),
            "SUCCESS",
            "{\"conversationId\":%s,\"messageId\":%s,\"attachmentRefId\":%s}".formatted(
                message.conversationId(),
                message.messageId(),
                ticket.attachmentRefId()
            )
        );
        return new DownloadFileResult(
            resource.resource(),
            downloadAttachment.displayName(),
            downloadAttachment.mimeType(),
            resource.size()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public ImConversationSummaryResponse updateCurrentEmployeeReadPosition(Long conversationId,
                                                                           UpdateReadPositionRequest request) {
        Long userId = requireActiveEmployeeUserId();
        ensureCanAccessConversation(conversationId, userId);
        Long lastMessageSeq = imMessageRepository.findActiveConversationLastMessageSeq(conversationId)
            .orElseThrow(() -> new BizException(404, "会话不存在"));
        Long safeReadSeq = Math.min(request.getLastReadSeq(), lastMessageSeq);
        imMessageRepository.updateMemberLastReadSeq(conversationId, userId, safeReadSeq);
        ImConversationSummaryResponse response = imRepository.findConversationSummaryForUser(conversationId, userId)
            .map(this::toConversationSummaryResponse)
            .orElseThrow(() -> new BizException(500, "已读位点更新失败"));
        pushConversationEventAfterCommit(
            conversationId,
            ImWebSocketEvent.of(
                ImWebSocketEventTypes.READ_POSITION_UPDATED,
                conversationId,
                null,
                safeReadSeq,
                userId,
                Map.of("userId", userId, "lastReadSeq", safeReadSeq)
            )
        );
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImConversationSummaryResponse updateCurrentEmployeeConversationPreference(
        Long conversationId,
        UpdateConversationPreferenceRequest request
    ) {
        Long userId = requireActiveEmployeeUserId();
        ensureCanAccessConversation(conversationId, userId);
        if (request.getPin() == null && request.getMute() == null) {
            throw new BizException(400, "会话偏好更新内容不能为空");
        }
        int updated = imRepository.updateMemberPreference(conversationId, userId, request.getPin(), request.getMute());
        if (updated == 0) {
            throw new BizException(409, "会话偏好更新失败");
        }
        ImConversationSummaryResponse response = imRepository.findConversationSummaryForUser(conversationId, userId)
            .map(this::toConversationSummaryResponse)
            .orElseThrow(() -> new BizException(500, "会话偏好更新失败"));
        pushConversationEventToUsersAfterCommit(
            List.of(userId),
            ImWebSocketEvent.of(
                ImWebSocketEventTypes.CONVERSATION_PREFERENCE_UPDATED,
                conversationId,
                null,
                null,
                userId,
                Map.of(
                    "userId", userId,
                    "pin", response.getPin(),
                    "mute", response.getMute()
                )
            )
        );
        return response;
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

    private Long requireActiveEmployeeUserId() {
        Long userId = CurrentUserHolder.requireUserId();
        if (!imRepository.existsLoginAllowedOrgUser(userId)) {
            throw new BizException(403, "当前员工状态不允许使用会话");
        }
        return userId;
    }

    private Long createPrivateConversation(ImUserRelationRow currentUser,
                                           ImUserRelationRow targetUser,
                                           String conversationKey) {
        Long conversationId = IdGenerator.nextId();
        String title = privateConversationTitle(currentUser, targetUser);
        boolean inserted = imRepository.tryInsertPrivateConversation(
            conversationId,
            conversationKey,
            title,
            currentUser.userId()
        );
        if (!inserted) {
            return imRepository.findActiveConversationIdByKey(conversationKey)
                .orElseThrow(() -> new BizException(409, "私聊会话创建冲突"));
        }
        try {
            imRepository.insertUserMember(IdGenerator.nextId(), conversationId, currentUser.userId(), "MEMBER", "DIRECT_RELATION");
            imRepository.insertUserMember(IdGenerator.nextId(), conversationId, targetUser.userId(), "MEMBER", "DIRECT_RELATION");
        } catch (DuplicateKeyException e) {
            return imRepository.findActiveConversationIdByKey(conversationKey)
                .orElseThrow(() -> new BizException(409, "私聊成员写入冲突"));
        }
        return conversationId;
    }

    private String normalizeClientMessageId(String clientMessageId) {
        if (!StringUtils.hasText(clientMessageId)) {
            return null;
        }
        return clientMessageId.trim();
    }

    private Long normalizeQuotedMessageId(Long conversationId, Long quotedMessageId) {
        if (quotedMessageId == null) {
            return null;
        }
        ImMessageRow quotedMessage = imMessageRepository.findMessageById(quotedMessageId)
            .orElseThrow(() -> new BizException(404, "引用消息不存在"));
        if (!conversationId.equals(quotedMessage.conversationId())) {
            throw new BizException(400, "只能引用当前会话消息");
        }
        return quotedMessageId;
    }

    private ImMessageRow requireReadableMessage(Long conversationId, Long messageId, Long userId) {
        ensureCanAccessConversation(conversationId, userId);
        ImMessageRow message = imMessageRepository.findMessageById(messageId)
            .orElseThrow(() -> new BizException(404, "消息不存在"));
        if (!conversationId.equals(message.conversationId())) {
            throw new BizException(404, "消息不存在");
        }
        if (!Set.of("NORMAL", "EDITED").contains(message.status())) {
            throw new BizException(409, "当前消息状态不允许操作");
        }
        return message;
    }

    private LlmModelRow firstActiveLlmModel() {
        return agentRepository.listLlmModels().stream()
            .filter(model -> "ACTIVE".equals(model.status()) && StringUtils.hasText(model.apiKey()))
            .findFirst()
            .orElse(null);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(500, "JSON序列化失败");
        }
    }

    private void pushConversationEventAfterCommit(Long conversationId, ImWebSocketEvent event) {
        pushConversationEventToUsersAfterCommit(activeUserMemberIds(conversationId), event);
    }

    private void pushConversationEventToUsersAfterCommit(List<Long> recipientUserIds, ImWebSocketEvent event) {
        webSocketPushService.pushToUsersAfterCommit(recipientUserIds, event);
    }

    private List<Long> activeUserMemberIds(Long conversationId) {
        return imRepository.listPushableActiveUserMemberIds(conversationId);
    }

    private boolean hasDirectManagerRelation(ImUserRelationRow currentUser, ImUserRelationRow targetUser) {
        return targetUser.userId().equals(currentUser.managerUserId())
            || currentUser.userId().equals(targetUser.managerUserId());
    }

    private String privateConversationKey(Long firstUserId, Long secondUserId) {
        long minUserId = Math.min(firstUserId, secondUserId);
        long maxUserId = Math.max(firstUserId, secondUserId);
        return "PRIVATE:%d:%d".formatted(minUserId, maxUserId);
    }

    private String privateConversationTitle(ImUserRelationRow firstUser, ImUserRelationRow secondUser) {
        if (secondUser.userId().equals(firstUser.managerUserId())) {
            return "%s与%s".formatted(firstUser.realName(), secondUser.realName());
        }
        if (firstUser.userId().equals(secondUser.managerUserId())) {
            return "%s与%s".formatted(secondUser.realName(), firstUser.realName());
        }
        return "%s与%s".formatted(secondUser.realName(), firstUser.realName());
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

    private int normalizeMessagePageSize(Integer size) {
        if (size == null) {
            return DEFAULT_MESSAGE_PAGE_SIZE;
        }
        if (size <= 0) {
            throw new BizException(400, "分页大小必须大于0");
        }
        return Math.min(size, MAX_MESSAGE_PAGE_SIZE);
    }

    private int normalizeConversationPageSize(Integer size) {
        if (size == null) {
            return DEFAULT_CONVERSATION_PAGE_SIZE;
        }
        if (size <= 0) {
            throw new BizException(400, "分页大小必须大于0");
        }
        return Math.min(size, MAX_CONVERSATION_PAGE_SIZE);
    }

    private LocalDateTime parseConversationCursorTime(String beforeLastMessageAt) {
        if (!StringUtils.hasText(beforeLastMessageAt)) {
            return null;
        }
        try {
            return LocalDateTime.parse(beforeLastMessageAt.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(400, "会话分页时间游标格式错误");
        }
    }

    private void ensureCanAccessConversation(Long conversationId, Long userId) {
        if (imRepository.existsActiveUserMembership(conversationId, userId)) {
            return;
        }
        if (imRepository.existsConversation(conversationId)) {
            throw new BizException(403, "无权访问该会话");
        }
        throw new BizException(404, "会话不存在");
    }

    private String normalizeMessageType(String messageType, boolean hasText, boolean hasAttachments) {
        if (!StringUtils.hasText(messageType)) {
            return hasAttachments && !hasText ? "FILE" : "TEXT";
        }
        String normalized = messageType.trim().toUpperCase();
        if (!Set.of("TEXT", "FILE").contains(normalized)) {
            throw new BizException(400, "当前仅支持发送文本或附件消息");
        }
        if ("FILE".equals(normalized) && !hasAttachments) {
            throw new BizException(400, "附件消息必须包含附件");
        }
        if ("TEXT".equals(normalized) && !hasText && !hasAttachments) {
            throw new BizException(400, "消息内容不能为空");
        }
        return normalized;
    }

    private String buildContentJson(String text, List<ImAttachmentRow> attachments) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "text",
                text,
                "attachmentCount",
                attachments.size()
            ));
        } catch (Exception e) {
            throw new BizException(500, "消息内容序列化失败");
        }
    }

    private String buildRecallContentJson() {
        try {
            return objectMapper.writeValueAsString(Map.of("text", "消息已撤回"));
        } catch (Exception e) {
            throw new BizException(500, "消息内容序列化失败");
        }
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
        } catch (Exception ignored) {
            return "";
        }
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.trim();
    }

    private String normalizeOptionalText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
    }

    private List<Long> normalizeAttachmentRefIds(List<Long> attachmentRefIds) {
        return new LinkedHashSet<>(attachmentRefIds == null ? List.<Long>of() : attachmentRefIds)
            .stream()
            .filter(attachmentRefId -> attachmentRefId != null)
            .toList();
    }

    private void ensureUsableSourceAttachment(ImAttachmentRow attachment, Long userId) {
        if (!"USER".equals(attachment.uploaderType()) || !userId.equals(attachment.uploaderId())) {
            throw new BizException(403, "无权使用该附件引用");
        }
        if (!"PERSONAL_FILE".equals(attachment.ownerType()) && !"IM_MESSAGE_PENDING".equals(attachment.ownerType())) {
            throw new BizException(403, "无权使用该附件引用");
        }
        if (attachment.deletedAt() != null || attachment.attachmentDeleted() != 0 || attachment.fileDeleted() != 0) {
            throw new BizException(404, "附件引用不存在");
        }
        if ("FAILED".equals(attachment.scanStatus())) {
            throw new BizException(409, "附件安全扫描未通过");
        }
    }

    private void ensureDownloadableMessageAttachment(ImAttachmentRow attachment, Long messageId) {
        if (!"IM_MESSAGE".equals(attachment.ownerType()) || !messageId.equals(attachment.ownerId())) {
            throw new BizException(404, "消息附件不存在");
        }
        if (attachment.deletedAt() != null || attachment.attachmentDeleted() != 0 || attachment.fileDeleted() != 0) {
            throw new BizException(409, "消息附件已删除");
        }
        if ("FAILED".equals(attachment.scanStatus())) {
            throw new BizException(409, "附件安全扫描未通过");
        }
    }

    private void ensureDownloadableMessageAttachment(DownloadAttachmentRow attachment, Long messageId) {
        if (!"IM_MESSAGE".equals(attachment.ownerType())) {
            throw new BizException(404, "消息附件不存在");
        }
        if (attachment.deletedAt() != null || attachment.attachmentDeleted() != 0 || attachment.fileDeleted() != 0) {
            throw new BizException(409, "消息附件已删除");
        }
        if ("FAILED".equals(attachment.scanStatus())) {
            throw new BizException(409, "附件安全扫描未通过");
        }
    }

    private void ensureEditableMessage(ImMessageRow message, Long conversationId, Long userId) {
        ensureOwnMessageInConversation(message, conversationId, userId);
        if (!Set.of("NORMAL", "EDITED").contains(message.status())) {
            throw new BizException(409, "当前消息状态不允许编辑");
        }
        if (!"TEXT".equals(message.messageType())) {
            throw new BizException(400, "当前仅支持编辑文本消息");
        }
    }

    private void ensureRecallableMessage(ImMessageRow message, Long conversationId, Long userId) {
        ensureOwnMessageInConversation(message, conversationId, userId);
        if (!Set.of("NORMAL", "EDITED").contains(message.status())) {
            throw new BizException(409, "当前消息状态不允许撤回");
        }
    }

    private void ensureMessageAllowsAttachmentDownload(ImMessageRow message) {
        if (!Set.of("NORMAL", "EDITED").contains(message.status())) {
            throw new BizException(409, "当前消息状态不允许下载附件");
        }
    }

    private void ensureOwnMessageInConversation(ImMessageRow message, Long conversationId, Long userId) {
        if (!conversationId.equals(message.conversationId())) {
            throw new BizException(404, "消息不存在");
        }
        if (!USER_SENDER_TYPE.equals(message.senderType()) || !userId.equals(message.senderId())) {
            throw new BizException(403, "只能操作自己发送的消息");
        }
    }

}
