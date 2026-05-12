package com.workforcehub.im.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.im.dto.CreateGroupConversationRequest;
import com.workforcehub.im.dto.GroupMemberUserIdsRequest;
import com.workforcehub.im.dto.ImConversationMemberResponse;
import com.workforcehub.im.dto.ImConversationSummaryResponse;
import com.workforcehub.im.dto.ImGroupProfileResponse;
import com.workforcehub.im.dto.UpdateGroupConversationRequest;
import com.workforcehub.im.repository.ImConversationMemberRow;
import com.workforcehub.im.repository.ImConversationSummaryRow;
import com.workforcehub.im.repository.ImGroupProfileRow;
import com.workforcehub.im.repository.ImGroupRepository;
import com.workforcehub.im.repository.ImRepository;
import com.workforcehub.im.websocket.ImWebSocketEvent;
import com.workforcehub.im.websocket.ImWebSocketEventTypes;
import com.workforcehub.im.websocket.ImWebSocketPushService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * module-im 群聊资料与成员治理服务。
 */
@Service
public class ImGroupService {

    private final ImRepository imRepository;
    private final ImGroupRepository imGroupRepository;
    private final ImWebSocketPushService webSocketPushService;

    public ImGroupService(ImRepository imRepository,
                          ImGroupRepository imGroupRepository,
                          ImWebSocketPushService webSocketPushService) {
        this.imRepository = imRepository;
        this.imGroupRepository = imGroupRepository;
        this.webSocketPushService = webSocketPushService;
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
        if (imGroupRepository.countActiveOrgUsers(normalizedMemberIds) != normalizedMemberIds.size()) {
            throw new BizException(404, "目标成员不存在或不可加入群聊");
        }

        Long conversationId = IdWorker.getId();
        imGroupRepository.insertGroupConversation(conversationId, title, ownerUserId);
        imGroupRepository.insertGroupProfile(IdWorker.getId(), conversationId, "CUSTOM", ownerUserId, ownerUserId);
        imGroupRepository.insertUserMember(IdWorker.getId(), conversationId, ownerUserId, "OWNER", "CREATE");
        for (Long memberUserId : normalizedMemberIds) {
            imGroupRepository.insertUserMember(IdWorker.getId(), conversationId, memberUserId, "MEMBER", "CREATE");
        }
        return imRepository.findConversationSummaryForUser(conversationId, ownerUserId)
            .map(this::toConversationSummaryResponse)
            .orElseThrow(() -> new BizException(500, "群聊创建失败"));
    }

    public List<ImConversationMemberResponse> listCurrentEmployeeGroupMembers(Long conversationId) {
        Long userId = requireActiveEmployeeUserId();
        ensureCanAccessActiveGroup(conversationId, userId);
        return listActiveConversationMemberResponses(conversationId);
    }

    public ImGroupProfileResponse getCurrentEmployeeGroupProfile(Long conversationId) {
        Long userId = requireActiveEmployeeUserId();
        ensureCanAccessActiveGroup(conversationId, userId);
        return getGroupProfileForUser(conversationId, userId);
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
        imGroupRepository.updateGroupProfile(conversationId, title, noticeText, ownerUserId);
        ImGroupProfileResponse response = getGroupProfileForUser(conversationId, ownerUserId);
        pushConversationEventAfterCommit(
            conversationId,
            ImWebSocketEvent.of(ImWebSocketEventTypes.GROUP_PROFILE_UPDATED, conversationId, null, null, ownerUserId, response)
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
        if (imGroupRepository.countActiveOrgUsers(normalizedMemberIds) != normalizedMemberIds.size()) {
            throw new BizException(404, "目标成员不存在或不可加入群聊");
        }
        for (Long memberUserId : normalizedMemberIds) {
            imGroupRepository.upsertUserMember(IdWorker.getId(), conversationId, memberUserId, "MEMBER", "OWNER_INVITE");
        }
        List<ImConversationMemberResponse> response = listActiveConversationMemberResponses(conversationId);
        pushConversationEventAfterCommit(
            conversationId,
            ImWebSocketEvent.of(ImWebSocketEventTypes.GROUP_MEMBERS_ADDED, conversationId, null, null, ownerUserId, response)
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
        imGroupRepository.markUserMembersLeft(conversationId, normalizedMemberIds);
        List<ImConversationMemberResponse> response = listActiveConversationMemberResponses(conversationId);
        pushConversationEventToUsersAfterCommit(
            recipientsBeforeChange,
            ImWebSocketEvent.of(ImWebSocketEventTypes.GROUP_MEMBERS_REMOVED, conversationId, null, null, ownerUserId, response)
        );
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<ImConversationMemberResponse> leaveCurrentEmployeeGroupConversation(Long conversationId) {
        Long userId = requireActiveEmployeeUserId();
        ensureCanAccessActiveGroup(conversationId, userId);
        String memberRole = imGroupRepository.findActiveUserMemberRole(conversationId, userId).orElse(null);
        if ("OWNER".equals(memberRole)) {
            throw new BizException(400, "群主不能直接退群，请先解散群聊或转让群主");
        }
        List<Long> recipientsBeforeChange = activeUserMemberIds(conversationId);
        int updated = imGroupRepository.markUserMemberLeft(conversationId, userId);
        if (updated == 0) {
            throw new BizException(409, "当前群成员状态不允许退群");
        }
        List<ImConversationMemberResponse> response = listActiveConversationMemberResponses(conversationId);
        pushConversationEventToUsersAfterCommit(
            recipientsBeforeChange,
            ImWebSocketEvent.of(ImWebSocketEventTypes.GROUP_MEMBER_LEFT, conversationId, null, null, userId, response)
        );
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public void dismissCurrentEmployeeGroupConversation(Long conversationId) {
        Long ownerUserId = requireActiveEmployeeUserId();
        ensureCanManageCustomGroup(conversationId, ownerUserId);
        List<Long> recipientsBeforeChange = activeUserMemberIds(conversationId);
        imGroupRepository.dismissGroupConversation(conversationId, ownerUserId);
        pushConversationEventToUsersAfterCommit(
            recipientsBeforeChange,
            ImWebSocketEvent.of(
                ImWebSocketEventTypes.GROUP_DISMISSED,
                conversationId,
                null,
                null,
                ownerUserId,
                Map.of("conversationId", conversationId)
            )
        );
    }

    List<ImConversationMemberResponse> listActiveConversationMemberResponses(Long conversationId) {
        return listActiveConversationMemberResponses(conversationId, false);
    }

    List<ImConversationMemberResponse> listActiveConversationMemberResponses(Long conversationId,
                                                                             boolean includeAgentMembers) {
        List<ImConversationMemberRow> rows = includeAgentMembers
            ? imGroupRepository.listActiveMembers(conversationId)
            : imGroupRepository.listActiveUserMembers(conversationId);
        return rows.stream()
            .map(this::toConversationMemberResponse)
            .toList();
    }

    ImGroupProfileResponse getGroupProfileForUser(Long conversationId, Long userId) {
        return imGroupRepository.findActiveGroupProfile(conversationId)
            .map(row -> toGroupProfileResponse(row, imGroupRepository.findActiveUserMemberRole(conversationId, userId).orElse(null)))
            .orElseThrow(() -> new BizException(404, "群聊不存在"));
    }

    private Long requireActiveEmployeeUserId() {
        Long userId = CurrentUserHolder.requireUserId();
        if (!imRepository.existsLoginAllowedOrgUser(userId)) {
            throw new BizException(403, "当前员工状态不允许使用会话");
        }
        return userId;
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
        if (!imGroupRepository.existsActiveCustomGroupConversation(conversationId)) {
            if (imRepository.existsConversation(conversationId)) {
                throw new BizException(400, "仅自定义群聊支持成员治理");
            }
            throw new BizException(404, "群聊不存在");
        }
        String memberRole = imGroupRepository.findActiveUserMemberRole(conversationId, userId).orElse(null);
        if (!"OWNER".equals(memberRole)) {
            throw new BizException(403, "只有群主可以管理群成员");
        }
    }

    private void ensureCanAccessActiveGroup(Long conversationId, Long userId) {
        if (!imGroupRepository.existsActiveGroupConversation(conversationId)) {
            if (imRepository.existsConversation(conversationId)) {
                throw new BizException(400, "仅群聊支持成员列表");
            }
            throw new BizException(404, "群聊不存在");
        }
        if (!imRepository.existsActiveUserMembership(conversationId, userId)) {
            throw new BizException(403, "无权访问该群聊");
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

    private String normalizeOptionalText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
    }
}
