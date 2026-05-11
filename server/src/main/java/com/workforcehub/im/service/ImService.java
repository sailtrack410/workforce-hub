package com.workforcehub.im.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
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
import com.workforcehub.im.dto.CreateGroupConversationRequest;
import com.workforcehub.im.dto.EditImMessageRequest;
import com.workforcehub.im.dto.GroupMemberUserIdsRequest;
import com.workforcehub.im.dto.ImConversationDetailResponse;
import com.workforcehub.im.dto.ImConversationMemberResponse;
import com.workforcehub.im.dto.ImMessageAttachmentResponse;
import com.workforcehub.im.dto.ImConversationSummaryResponse;
import com.workforcehub.im.dto.ImGroupProfileResponse;
import com.workforcehub.im.dto.ImMessageResponse;
import com.workforcehub.im.dto.SendImMessageRequest;
import com.workforcehub.im.dto.StartAgentConversationRequest;
import com.workforcehub.im.dto.StartPrivateConversationRequest;
import com.workforcehub.im.dto.UpdateConversationPreferenceRequest;
import com.workforcehub.im.dto.UpdateGroupConversationRequest;
import com.workforcehub.im.dto.UpdateReadPositionRequest;
import com.workforcehub.im.repository.ImAttachmentRow;
import com.workforcehub.im.repository.ImConversationMemberRow;
import com.workforcehub.im.repository.ImConversationSummaryRow;
import com.workforcehub.im.repository.ImGroupProfileRow;
import com.workforcehub.im.repository.ImMessageRow;
import com.workforcehub.im.repository.ImRepository;
import com.workforcehub.im.repository.ImUserRelationRow;
import com.workforcehub.im.websocket.ImWebSocketEvent;
import com.workforcehub.im.websocket.ImWebSocketPushService;
import com.workforcehub.spi.ImAgentDescriptor;
import com.workforcehub.spi.ImAgentResultCallback;
import com.workforcehub.spi.ImAgentTriggerService;
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
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * module-im 员工端会话读取服务。
 */
@Service
public class ImService implements ImAgentResultCallback {

    private static final Logger log = LoggerFactory.getLogger(ImService.class);

    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 50;
    private static final int MAX_MESSAGE_PAGE_SIZE = 100;
    private static final int DEFAULT_CONVERSATION_PAGE_SIZE = 30;
    private static final int MAX_CONVERSATION_PAGE_SIZE = 100;
    private static final String USER_SENDER_TYPE = "USER";
    private static final String AGENT_SENDER_TYPE = "AGENT";
    private static final String SYSTEM_SENDER_TYPE = "SYSTEM";
    private static final long SYSTEM_SENDER_ID = 0L;

    private final ImRepository imRepository;
    private final ObjectMapper objectMapper;
    private final AttachmentRefRepository attachmentRefRepository;
    private final DownloadTicketExecutionService downloadTicketExecutionService;
    private final StorageProviderRegistry storageProviderRegistry;
    private final ImWebSocketPushService webSocketPushService;
    private final ObjectProvider<ImAgentTriggerService> imAgentTriggerServiceProvider;
    private final TransactionTemplate transactionTemplate;

    public ImService(ImRepository imRepository,
                     ObjectMapper objectMapper,
                     AttachmentRefRepository attachmentRefRepository,
                     DownloadTicketExecutionService downloadTicketExecutionService,
                     StorageProviderRegistry storageProviderRegistry,
                     ImWebSocketPushService webSocketPushService,
                     ObjectProvider<ImAgentTriggerService> imAgentTriggerServiceProvider,
                     PlatformTransactionManager transactionManager) {
        this.imRepository = imRepository;
        this.objectMapper = objectMapper;
        this.attachmentRefRepository = attachmentRefRepository;
        this.downloadTicketExecutionService = downloadTicketExecutionService;
        this.storageProviderRegistry = storageProviderRegistry;
        this.webSocketPushService = webSocketPushService;
        this.imAgentTriggerServiceProvider = imAgentTriggerServiceProvider;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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
        return imRepository.listMessages(conversationId, beforeSeq, normalizeMessagePageSize(size))
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
        response.setMembers(listActiveConversationMemberResponses(conversationId, true));
        if ("GROUP".equals(conversation.getConversationType())) {
            response.setGroupProfile(getGroupProfileForUser(conversationId, userId));
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
    public ImConversationSummaryResponse createCurrentEmployeeGroupConversation(CreateGroupConversationRequest request) {
        Long ownerUserId = requireActiveEmployeeUserId();
        imRepository.findActiveOrgUser(ownerUserId)
            .orElseThrow(() -> new BizException(403, "当前员工不可创建群聊"));

        String title = request.getTitle().trim();
        Set<Long> normalizedMemberIds = normalizeGroupMemberIds(ownerUserId, request.getMemberUserIds());
        if (normalizedMemberIds.isEmpty()) {
            throw new BizException(400, "群聊至少需要选择1名其他成员");
        }
        if (imRepository.countActiveOrgUsers(normalizedMemberIds) != normalizedMemberIds.size()) {
            throw new BizException(404, "目标成员不存在或不可加入群聊");
        }

        Long conversationId = IdWorker.getId();
        imRepository.insertGroupConversation(conversationId, title, ownerUserId);
        imRepository.insertGroupProfile(IdWorker.getId(), conversationId, "CUSTOM", ownerUserId, ownerUserId);
        imRepository.insertUserMember(IdWorker.getId(), conversationId, ownerUserId, "OWNER", "CREATE");
        for (Long memberUserId : normalizedMemberIds) {
            imRepository.insertUserMember(IdWorker.getId(), conversationId, memberUserId, "MEMBER", "CREATE");
        }
        return imRepository.findConversationSummaryForUser(conversationId, ownerUserId)
            .map(this::toConversationSummaryResponse)
            .orElseThrow(() -> new BizException(500, "群聊创建失败"));
    }

    @Transactional(rollbackFor = Exception.class)
    public ImConversationSummaryResponse startCurrentEmployeeAgentConversation(StartAgentConversationRequest request) {
        Long currentUserId = requireActiveEmployeeUserId();
        ImAgentDescriptor agent = requireImAgentTriggerService()
            .requireUsableAgent(request.getAgentId(), currentUserId);

        String conversationKey = agentConversationKey(currentUserId, agent.agentId());
        Long conversationId = imRepository.findActiveConversationIdByKey(conversationKey)
            .orElseGet(() -> createAgentConversation(currentUserId, agent, conversationKey));

        return imRepository.findConversationSummaryForUser(conversationId, currentUserId)
            .map(this::toConversationSummaryResponse)
            .orElseThrow(() -> new BizException(500, "Agent会话创建失败"));
    }

    public List<ImConversationMemberResponse> listCurrentEmployeeGroupMembers(Long conversationId) {
        Long userId = requireActiveEmployeeUserId();
        ensureCanAccessActiveGroup(conversationId, userId);
        return listActiveConversationMemberResponses(conversationId);
    }

    public ImGroupProfileResponse getCurrentEmployeeGroupProfile(Long conversationId) {
        Long userId = requireActiveEmployeeUserId();
        ensureCanAccessActiveGroup(conversationId, userId);
        return imRepository.findActiveGroupProfile(conversationId)
            .map(row -> toGroupProfileResponse(row, imRepository.findActiveUserMemberRole(conversationId, userId).orElse(null)))
            .orElseThrow(() -> new BizException(404, "群聊不存在"));
    }

    @Transactional(rollbackFor = Exception.class)
    public ImGroupProfileResponse updateCurrentEmployeeGroupProfile(Long conversationId,
                                                                    UpdateGroupConversationRequest request) {
        Long ownerUserId = requireActiveEmployeeUserId();
        ensureCanManageCustomGroup(conversationId, ownerUserId);
        String title = normalizeOptionalText(request.getTitle());
        String noticeText = normalizeOptionalText(request.getNoticeText());
        if (!StringUtils.hasText(title) && !StringUtils.hasText(noticeText)) {
            throw new BizException(400, "群资料更新内容不能为空");
        }
        imRepository.updateGroupProfile(conversationId, title, noticeText, ownerUserId);
        ImGroupProfileResponse response = getGroupProfileForUser(conversationId, ownerUserId);
        pushConversationEventAfterCommit(
            conversationId,
            ImWebSocketEvent.of("IM_GROUP_PROFILE_UPDATED", conversationId, null, null, ownerUserId, response)
        );
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<ImConversationMemberResponse> addCurrentEmployeeGroupMembers(Long conversationId,
                                                                             GroupMemberUserIdsRequest request) {
        Long ownerUserId = requireActiveEmployeeUserId();
        ensureCanManageCustomGroup(conversationId, ownerUserId);
        Set<Long> normalizedMemberIds = normalizeGroupMemberIds(ownerUserId, request.getMemberUserIds());
        if (normalizedMemberIds.isEmpty()) {
            throw new BizException(400, "至少选择1名要添加的成员");
        }
        if (imRepository.countActiveOrgUsers(normalizedMemberIds) != normalizedMemberIds.size()) {
            throw new BizException(404, "目标成员不存在或不可加入群聊");
        }
        for (Long memberUserId : normalizedMemberIds) {
            imRepository.upsertUserMember(IdWorker.getId(), conversationId, memberUserId, "MEMBER", "OWNER_INVITE");
        }
        List<ImConversationMemberResponse> response = listActiveConversationMemberResponses(conversationId);
        pushConversationEventAfterCommit(
            conversationId,
            ImWebSocketEvent.of("IM_GROUP_MEMBERS_ADDED", conversationId, null, null, ownerUserId, response)
        );
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<ImConversationMemberResponse> removeCurrentEmployeeGroupMembers(Long conversationId,
                                                                                GroupMemberUserIdsRequest request) {
        Long ownerUserId = requireActiveEmployeeUserId();
        ensureCanManageCustomGroup(conversationId, ownerUserId);
        List<Long> recipientsBeforeChange = activeUserMemberIds(conversationId);
        Set<Long> normalizedMemberIds = normalizeUserIds(request.getMemberUserIds());
        if (normalizedMemberIds.contains(ownerUserId)) {
            throw new BizException(400, "群主不能把自己移出群聊");
        }
        if (normalizedMemberIds.isEmpty()) {
            throw new BizException(400, "至少选择1名要移出的成员");
        }
        imRepository.markUserMembersLeft(conversationId, normalizedMemberIds);
        List<ImConversationMemberResponse> response = listActiveConversationMemberResponses(conversationId);
        pushConversationEventToUsersAfterCommit(
            recipientsBeforeChange,
            ImWebSocketEvent.of("IM_GROUP_MEMBERS_REMOVED", conversationId, null, null, ownerUserId, response)
        );
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<ImConversationMemberResponse> leaveCurrentEmployeeGroupConversation(Long conversationId) {
        Long userId = requireActiveEmployeeUserId();
        ensureCanAccessActiveGroup(conversationId, userId);
        String memberRole = imRepository.findActiveUserMemberRole(conversationId, userId).orElse(null);
        if ("OWNER".equals(memberRole)) {
            throw new BizException(400, "群主不能直接退群，请先解散群聊或转让群主");
        }
        List<Long> recipientsBeforeChange = activeUserMemberIds(conversationId);
        int updated = imRepository.markUserMemberLeft(conversationId, userId);
        if (updated == 0) {
            throw new BizException(409, "当前群成员状态不允许退群");
        }
        List<ImConversationMemberResponse> response = listActiveConversationMemberResponses(conversationId);
        pushConversationEventToUsersAfterCommit(
            recipientsBeforeChange,
            ImWebSocketEvent.of("IM_GROUP_MEMBER_LEFT", conversationId, null, null, userId, response)
        );
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public void dismissCurrentEmployeeGroupConversation(Long conversationId) {
        Long ownerUserId = requireActiveEmployeeUserId();
        ensureCanManageCustomGroup(conversationId, ownerUserId);
        List<Long> recipientsBeforeChange = activeUserMemberIds(conversationId);
        imRepository.dismissGroupConversation(conversationId, ownerUserId);
        pushConversationEventToUsersAfterCommit(
            recipientsBeforeChange,
            ImWebSocketEvent.of(
                "IM_GROUP_DISMISSED",
                conversationId,
                null,
                null,
                ownerUserId,
                Map.of("conversationId", conversationId)
            )
        );
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
        Long lastSeq = imRepository.lockActiveConversationAndGetLastSeq(conversationId);
        if (lastSeq == null) {
            throw new BizException(404, "会话不存在");
        }

        String messageType = normalizeMessageType(request.getMessageType(), StringUtils.hasText(text), !attachmentRefIds.isEmpty());
        String clientMessageId = normalizeClientMessageId(request.getClientMessageId());
        if (clientMessageId != null) {
            ImMessageResponse existing = imRepository.findMessageByClientMessageId(
                conversationId,
                USER_SENDER_TYPE,
                userId,
                clientMessageId
            ).map(this::toMessageResponse).orElse(null);
            if (existing != null) {
                imRepository.updateMemberLastReadSeq(conversationId, userId, existing.getSeqNo());
                return existing;
            }
        }
        List<ImAttachmentRow> sourceAttachments = attachmentRefIds.stream()
            .map(attachmentRefId -> imRepository.findAttachmentById(attachmentRefId)
                .orElseThrow(() -> new BizException(404, "附件引用不存在")))
            .peek(attachment -> ensureUsablePersonalAttachment(attachment, userId))
            .toList();
        Long seqNo = lastSeq + 1;
        Long messageId = IdWorker.getId();
        LocalDateTime sentAt = LocalDateTime.now();
        String contentJson = buildContentJson(text, sourceAttachments);
        try {
            imRepository.insertMessage(
                messageId,
                conversationId,
                seqNo,
                USER_SENDER_TYPE,
                userId,
                messageType,
                clientMessageId,
                contentJson,
                sentAt
            );
        } catch (DuplicateKeyException e) {
            if (clientMessageId == null) {
                throw e;
            }
            ImMessageResponse existing = imRepository.findMessageByClientMessageId(
                conversationId,
                USER_SENDER_TYPE,
                userId,
                clientMessageId
            ).map(this::toMessageResponse).orElseThrow(() -> new BizException(409, "消息幂等写入冲突"));
            imRepository.updateMemberLastReadSeq(conversationId, userId, existing.getSeqNo());
            return existing;
        }
        for (int i = 0; i < sourceAttachments.size(); i++) {
            imRepository.createImMessageAttachmentRef(messageId, sourceAttachments.get(i), userId, i);
        }
        imRepository.updateConversationLastMessage(conversationId, seqNo, messageId, sentAt, userId);
        imRepository.updateMemberLastReadSeq(conversationId, userId, seqNo);

        ImMessageResponse response = toMessageResponse(new ImMessageRow(
            messageId,
            conversationId,
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
            conversationId,
            ImWebSocketEvent.of("IM_MESSAGE_CREATED", conversationId, messageId, seqNo, userId, response)
        );
        triggerAgentReplyAfterCommitIfNeeded(response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendAgentMessage(Long conversationId,
                                 Long agentId,
                                 Long agentTaskId,
                                 String messageType,
                                 String contentJson) {
        if (!imRepository.existsActiveAgentConversation(conversationId, agentId)) {
            throw new BizException(403, "Agent无权写入该会话");
        }
        Long lastSeq = imRepository.lockActiveConversationAndGetLastSeq(conversationId);
        if (lastSeq == null) {
            throw new BizException(404, "会话不存在");
        }
        String normalizedMessageType = normalizeAgentMessageType(messageType);
        String normalizedContentJson = normalizeAgentContentJson(contentJson, agentTaskId);
        String clientMessageId = agentMessageClientId(agentTaskId);
        if (clientMessageId != null && imRepository.findMessageByClientMessageId(
            conversationId,
            AGENT_SENDER_TYPE,
            agentId,
            clientMessageId
        ).isPresent()) {
            return;
        }
        Long seqNo = lastSeq + 1;
        Long messageId = IdWorker.getId();
        LocalDateTime sentAt = LocalDateTime.now();
        try {
            imRepository.insertMessage(
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
        imRepository.updateConversationLastMessage(conversationId, seqNo, messageId, sentAt, agentId);
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
            ImWebSocketEvent.of("IM_AGENT_MESSAGE_CREATED", conversationId, messageId, seqNo, agentId, response)
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public ImMessageResponse editCurrentEmployeeMessage(Long conversationId,
                                                        Long messageId,
                                                        EditImMessageRequest request) {
        Long userId = requireActiveEmployeeUserId();
        ensureCanAccessConversation(conversationId, userId);
        ImMessageRow message = imRepository.findMessageById(messageId)
            .orElseThrow(() -> new BizException(404, "消息不存在"));
        ensureEditableMessage(message, conversationId, userId);

        String text = normalizeText(request.getText());
        if (!StringUtils.hasText(text)) {
            throw new BizException(400, "消息内容不能为空");
        }
        String contentJson = buildContentJson(text, imRepository.listMessageAttachments(messageId));
        int updated = imRepository.updateMessageContent(messageId, contentJson, LocalDateTime.now());
        if (updated == 0) {
            throw new BizException(409, "当前消息状态不允许编辑");
        }
        ImMessageResponse response = imRepository.findMessageById(messageId)
            .map(this::toMessageResponse)
            .orElseThrow(() -> new BizException(404, "消息不存在"));
        pushConversationEventAfterCommit(
            conversationId,
            ImWebSocketEvent.of("IM_MESSAGE_EDITED", conversationId, messageId, response.getSeqNo(), userId, response)
        );
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImMessageResponse recallCurrentEmployeeMessage(Long conversationId, Long messageId) {
        Long userId = requireActiveEmployeeUserId();
        ensureCanAccessConversation(conversationId, userId);
        ImMessageRow message = imRepository.findMessageById(messageId)
            .orElseThrow(() -> new BizException(404, "消息不存在"));
        ensureRecallableMessage(message, conversationId, userId);

        String contentJson = buildRecallContentJson();
        int updated = imRepository.recallMessage(messageId, contentJson, LocalDateTime.now());
        if (updated == 0) {
            throw new BizException(409, "当前消息状态不允许撤回");
        }
        ImMessageResponse response = imRepository.findMessageById(messageId)
            .map(this::toMessageResponse)
            .orElseThrow(() -> new BizException(404, "消息不存在"));
        pushConversationEventAfterCommit(
            conversationId,
            ImWebSocketEvent.of("IM_MESSAGE_RECALLED", conversationId, messageId, response.getSeqNo(), userId, response)
        );
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public DownloadTicketResponse createCurrentEmployeeMessageAttachmentDownloadTicket(Long conversationId,
                                                                                       Long messageId,
                                                                                       Long attachmentRefId,
                                                                                       CreateDownloadTicketRequest request) {
        Long userId = requireActiveEmployeeUserId();
        ensureCanAccessConversation(conversationId, userId);
        ImMessageRow message = imRepository.findMessageById(messageId)
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

        Long ticketId = IdWorker.getId();
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
        if (!"IM_MESSAGE".equals(attachment.ownerType()) || attachment.ownerId() == null) {
            throw new BizException(404, "消息附件不存在");
        }
        ImMessageRow message = imRepository.findMessageById(attachment.ownerId())
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
        Long lastMessageSeq = imRepository.findActiveConversationLastMessageSeq(conversationId)
            .orElseThrow(() -> new BizException(404, "会话不存在"));
        Long safeReadSeq = Math.min(request.getLastReadSeq(), lastMessageSeq);
        imRepository.updateMemberLastReadSeq(conversationId, userId, safeReadSeq);
        ImConversationSummaryResponse response = imRepository.findConversationSummaryForUser(conversationId, userId)
            .map(this::toConversationSummaryResponse)
            .orElseThrow(() -> new BizException(500, "已读位点更新失败"));
        pushConversationEventAfterCommit(
            conversationId,
            ImWebSocketEvent.of(
                "IM_READ_POSITION_UPDATED",
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
        pushConversationEventAfterCommit(
            conversationId,
            ImWebSocketEvent.of(
                "IM_CONVERSATION_PREFERENCE_UPDATED",
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

    private ImConversationMemberResponse toConversationMemberResponse(ImConversationMemberRow row) {
        ImConversationMemberResponse response = new ImConversationMemberResponse();
        response.setMemberId(row.memberId());
        response.setMemberType(row.memberType());
        response.setDisplayName(row.displayName());
        response.setMemberRole(row.memberRole());
        response.setStatus(row.status());
        response.setJoinedAt(row.joinedAt());
        return response;
    }

    private ImGroupProfileResponse toGroupProfileResponse(ImGroupProfileRow row, String currentMemberRole) {
        ImGroupProfileResponse response = new ImGroupProfileResponse();
        response.setConversationId(row.conversationId());
        response.setTitle(row.title());
        response.setAvatarFileId(row.avatarFileId());
        response.setGroupType(row.groupType());
        response.setOwnerUserId(row.ownerUserId());
        response.setNoticeText(row.noticeText());
        response.setStatus(row.status());
        response.setCurrentMemberRole(currentMemberRole);
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
        Long conversationId = IdWorker.getId();
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
            imRepository.insertUserMember(IdWorker.getId(), conversationId, currentUser.userId(), "MEMBER", "DIRECT_RELATION");
            imRepository.insertUserMember(IdWorker.getId(), conversationId, targetUser.userId(), "MEMBER", "DIRECT_RELATION");
        } catch (DuplicateKeyException e) {
            return imRepository.findActiveConversationIdByKey(conversationKey)
                .orElseThrow(() -> new BizException(409, "私聊成员写入冲突"));
        }
        return conversationId;
    }

    private Long createAgentConversation(Long userId,
                                         ImAgentDescriptor agent,
                                         String conversationKey) {
        Long conversationId = IdWorker.getId();
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
            imRepository.insertUserMember(IdWorker.getId(), conversationId, userId, "MEMBER", "AGENT_CHAT");
            imRepository.insertAgentMember(IdWorker.getId(), conversationId, agent.agentId(), "AGENT_CHAT");
        } catch (DuplicateKeyException e) {
            return imRepository.findActiveConversationIdByKey(conversationKey)
                .orElseThrow(() -> new BizException(409, "Agent会话成员写入冲突"));
        }
        return conversationId;
    }

    private String normalizeClientMessageId(String clientMessageId) {
        if (!StringUtils.hasText(clientMessageId)) {
            return null;
        }
        return clientMessageId.trim();
    }

    private Set<Long> normalizeGroupMemberIds(Long ownerUserId, List<Long> memberUserIds) {
        Set<Long> normalized = new LinkedHashSet<>();
        for (Long memberUserId : memberUserIds) {
            if (memberUserId == null || ownerUserId.equals(memberUserId)) {
                continue;
            }
            normalized.add(memberUserId);
        }
        return normalized;
    }

    private Set<Long> normalizeUserIds(List<Long> userIds) {
        Set<Long> normalized = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId == null) {
                continue;
            }
            normalized.add(userId);
        }
        return normalized;
    }

    private void ensureCanManageCustomGroup(Long conversationId, Long userId) {
        if (!imRepository.existsActiveCustomGroupConversation(conversationId)) {
            if (imRepository.existsConversation(conversationId)) {
                throw new BizException(400, "仅自定义群聊支持成员治理");
            }
            throw new BizException(404, "群聊不存在");
        }
        String memberRole = imRepository.findActiveUserMemberRole(conversationId, userId).orElse(null);
        if (!"OWNER".equals(memberRole)) {
            throw new BizException(403, "只有群主可以管理群成员");
        }
    }

    private List<ImConversationMemberResponse> listActiveConversationMemberResponses(Long conversationId) {
        return listActiveConversationMemberResponses(conversationId, false);
    }

    private List<ImConversationMemberResponse> listActiveConversationMemberResponses(Long conversationId,
                                                                                    boolean includeAgentMembers) {
        List<ImConversationMemberRow> rows = includeAgentMembers
            ? imRepository.listActiveMembers(conversationId)
            : imRepository.listActiveUserMembers(conversationId);
        return rows.stream()
            .map(this::toConversationMemberResponse)
            .toList();
    }

    private ImGroupProfileResponse getGroupProfileForUser(Long conversationId, Long userId) {
        return imRepository.findActiveGroupProfile(conversationId)
            .map(row -> toGroupProfileResponse(row, imRepository.findActiveUserMemberRole(conversationId, userId).orElse(null)))
            .orElseThrow(() -> new BizException(404, "群聊不存在"));
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

    private void ensureCanAccessActiveGroup(Long conversationId, Long userId) {
        if (!imRepository.existsActiveGroupConversation(conversationId)) {
            if (imRepository.existsConversation(conversationId)) {
                throw new BizException(400, "仅群聊支持成员列表");
            }
            throw new BizException(404, "群聊不存在");
        }
        if (!imRepository.existsActiveUserMembership(conversationId, userId)) {
            throw new BizException(403, "无权访问该群聊");
        }
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

    private String agentConversationKey(Long userId, Long agentId) {
        return "AGENT:%d:%d".formatted(userId, agentId);
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

    private void triggerAgentReplyAfterCommitIfNeeded(ImMessageResponse userMessage) {
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
                ImAgentTriggerService triggerService = requireImAgentTriggerService();
                triggerService.triggerConversationReply(
                    userMessage.getConversationId(),
                    agentId,
                    userMessage.getSenderId(),
                    userMessage.getSenderType(),
                    buildAgentInputContentJson(userMessage)
                );
            } catch (Exception e) {
                log.warn("IM Agent 会话触发失败 conversationId={} messageId={}",
                    userMessage.getConversationId(),
                    userMessage.getMessageId(),
                    e
                );
                writeAgentFailureSystemMessage(userMessage.getConversationId(), agentId);
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

    private String buildAgentInputContentJson(ImMessageResponse userMessage) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "messageId", userMessage.getMessageId(),
                "conversationId", userMessage.getConversationId(),
                "senderId", userMessage.getSenderId(),
                "text", userMessage.getContentText(),
                "contentJson", userMessage.getContentJson()
            ));
        } catch (Exception e) {
            throw new BizException(500, "Agent输入内容序列化失败");
        }
    }

    private void writeAgentFailureSystemMessage(Long conversationId, Long agentId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Long lastSeq = imRepository.lockActiveConversationAndGetLastSeq(conversationId);
                if (lastSeq == null) {
                    throw new BizException(404, "会话不存在");
                }
                Long seqNo = lastSeq + 1;
                Long messageId = IdWorker.getId();
                LocalDateTime sentAt = LocalDateTime.now();
                String contentJson = buildSystemContentJson("Agent 暂时无法回复，请稍后再试");
                imRepository.insertMessage(
                    messageId,
                    conversationId,
                    seqNo,
                    SYSTEM_SENDER_TYPE,
                    SYSTEM_SENDER_ID,
                    "SYSTEM",
                    null,
                    contentJson,
                    sentAt
                );
                imRepository.updateConversationLastMessage(conversationId, seqNo, messageId, sentAt, agentId);
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
                webSocketPushService.pushToUsers(
                    activeUserMemberIds(conversationId),
                    ImWebSocketEvent.of("IM_AGENT_REPLY_FAILED", conversationId, messageId, seqNo, agentId, response)
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

    private ImAgentTriggerService requireImAgentTriggerService() {
        ImAgentTriggerService service = imAgentTriggerServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BizException(501, "Agent会话触发服务未接入");
        }
        return service;
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
            return textNode == null || textNode.isNull() ? "" : textNode.asText("");
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

    private void ensureUsablePersonalAttachment(ImAttachmentRow attachment, Long userId) {
        if (!"USER".equals(attachment.uploaderType()) || !userId.equals(attachment.uploaderId())) {
            throw new BizException(403, "无权使用该附件引用");
        }
        if (!"PERSONAL_FILE".equals(attachment.ownerType())) {
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

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
