package com.workforcehub.im.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.im.dto.CreateGroupConversationRequest;
import com.workforcehub.im.dto.GroupMemberUserIdsRequest;
import com.workforcehub.im.dto.ImConversationMemberResponse;
import com.workforcehub.im.dto.ImConversationSummaryResponse;
import com.workforcehub.im.dto.ImMessageResponse;
import com.workforcehub.im.dto.SendImMessageRequest;
import com.workforcehub.im.dto.StartPrivateConversationRequest;
import com.workforcehub.im.dto.UpdateReadPositionRequest;
import com.workforcehub.im.repository.ImConversationMemberRow;
import com.workforcehub.im.repository.ImConversationSummaryRow;
import com.workforcehub.im.repository.ImMessageRow;
import com.workforcehub.im.repository.ImRepository;
import com.workforcehub.im.repository.ImUserRelationRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * module-im 员工端会话读取服务。
 */
@Service
public class ImService {

    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 50;
    private static final int MAX_MESSAGE_PAGE_SIZE = 100;

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

    public List<ImMessageResponse> listCurrentEmployeeMessages(Long conversationId, Long beforeSeq, Integer size) {
        Long userId = CurrentUserHolder.requireUserId();
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

    @Transactional(rollbackFor = Exception.class)
    public ImConversationSummaryResponse startCurrentEmployeePrivateConversation(StartPrivateConversationRequest request) {
        Long currentUserId = CurrentUserHolder.requireUserId();
        Long targetUserId = request.getTargetUserId();
        if (currentUserId.equals(targetUserId)) {
            throw new BizException(400, "不能和自己发起私聊");
        }

        ImUserRelationRow currentUser = imRepository.findActiveOrgUser(currentUserId)
            .orElseThrow(() -> new BizException(403, "当前员工不可发起私聊"));
        ImUserRelationRow targetUser = imRepository.findActiveOrgUser(targetUserId)
            .orElseThrow(() -> new BizException(404, "目标用户不存在"));
        if (!hasDirectManagerRelation(currentUser, targetUser)) {
            throw new BizException(403, "只能和直属上级或直属下级发起私聊");
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
        Long ownerUserId = CurrentUserHolder.requireUserId();
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
    public List<ImConversationMemberResponse> addCurrentEmployeeGroupMembers(Long conversationId,
                                                                             GroupMemberUserIdsRequest request) {
        Long ownerUserId = CurrentUserHolder.requireUserId();
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
        return listActiveConversationMemberResponses(conversationId);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<ImConversationMemberResponse> removeCurrentEmployeeGroupMembers(Long conversationId,
                                                                                GroupMemberUserIdsRequest request) {
        Long ownerUserId = CurrentUserHolder.requireUserId();
        ensureCanManageCustomGroup(conversationId, ownerUserId);
        Set<Long> normalizedMemberIds = normalizeUserIds(request.getMemberUserIds());
        if (normalizedMemberIds.contains(ownerUserId)) {
            throw new BizException(400, "群主不能把自己移出群聊");
        }
        if (normalizedMemberIds.isEmpty()) {
            throw new BizException(400, "至少选择1名要移出的成员");
        }
        imRepository.markUserMembersLeft(conversationId, normalizedMemberIds);
        return listActiveConversationMemberResponses(conversationId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ImMessageResponse sendCurrentEmployeeMessage(Long conversationId, SendImMessageRequest request) {
        Long userId = CurrentUserHolder.requireUserId();
        ensureCanAccessConversation(conversationId, userId);
        Long lastSeq = imRepository.lockActiveConversationAndGetLastSeq(conversationId);
        if (lastSeq == null) {
            throw new BizException(404, "会话不存在");
        }

        String messageType = normalizeMessageType(request.getMessageType());
        String text = request.getText().trim();
        Long seqNo = lastSeq + 1;
        Long messageId = IdWorker.getId();
        LocalDateTime sentAt = LocalDateTime.now();
        String contentJson = buildTextContentJson(text);
        imRepository.insertMessage(
            messageId,
            conversationId,
            seqNo,
            "USER",
            userId,
            messageType,
            contentJson,
            sentAt
        );
        imRepository.updateConversationLastMessage(conversationId, seqNo, messageId, sentAt, userId);
        imRepository.updateMemberLastReadSeq(conversationId, userId, seqNo);

        return toMessageResponse(new ImMessageRow(
            messageId,
            conversationId,
            seqNo,
            "USER",
            userId,
            messageType,
            contentJson,
            null,
            "NORMAL",
            sentAt
        ));
    }

    @Transactional(rollbackFor = Exception.class)
    public ImConversationSummaryResponse updateCurrentEmployeeReadPosition(Long conversationId,
                                                                           UpdateReadPositionRequest request) {
        Long userId = CurrentUserHolder.requireUserId();
        ensureCanAccessConversation(conversationId, userId);
        Long lastMessageSeq = imRepository.findActiveConversationLastMessageSeq(conversationId)
            .orElseThrow(() -> new BizException(404, "会话不存在"));
        Long safeReadSeq = Math.min(request.getLastReadSeq(), lastMessageSeq);
        imRepository.updateMemberLastReadSeq(conversationId, userId, safeReadSeq);
        return imRepository.findConversationSummaryForUser(conversationId, userId)
            .map(this::toConversationSummaryResponse)
            .orElseThrow(() -> new BizException(500, "已读位点更新失败"));
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
        response.setMemberRole(row.memberRole());
        response.setStatus(row.status());
        response.setJoinedAt(row.joinedAt());
        return response;
    }

    private Long createPrivateConversation(ImUserRelationRow currentUser,
                                           ImUserRelationRow targetUser,
                                           String conversationKey) {
        Long conversationId = IdWorker.getId();
        String title = privateConversationTitle(currentUser, targetUser);
        imRepository.insertPrivateConversation(
            conversationId,
            conversationKey,
            title,
            currentUser.userId()
        );
        imRepository.insertUserMember(IdWorker.getId(), conversationId, currentUser.userId(), "MEMBER", "DIRECT_RELATION");
        imRepository.insertUserMember(IdWorker.getId(), conversationId, targetUser.userId(), "MEMBER", "DIRECT_RELATION");
        return conversationId;
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
        return imRepository.listActiveUserMembers(conversationId)
            .stream()
            .map(this::toConversationMemberResponse)
            .toList();
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
        response.setQuotedMessageId(row.quotedMessageId());
        response.setStatus(row.status());
        response.setSentAt(row.sentAt());
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

    private void ensureCanAccessConversation(Long conversationId, Long userId) {
        if (imRepository.existsActiveUserMembership(conversationId, userId)) {
            return;
        }
        if (imRepository.existsConversation(conversationId)) {
            throw new BizException(403, "无权访问该会话");
        }
        throw new BizException(404, "会话不存在");
    }

    private String normalizeMessageType(String messageType) {
        if (!StringUtils.hasText(messageType)) {
            return "TEXT";
        }
        String normalized = messageType.trim().toUpperCase();
        if (!"TEXT".equals(normalized)) {
            throw new BizException(400, "当前仅支持发送文本消息");
        }
        return normalized;
    }

    private String buildTextContentJson(String text) {
        try {
            return objectMapper.writeValueAsString(Map.of("text", text));
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
}
