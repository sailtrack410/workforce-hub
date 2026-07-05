package com.workforcehub.im.service;

import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.file.dto.CreateDownloadTicketRequest;
import com.workforcehub.file.dto.DownloadFileResult;
import com.workforcehub.file.dto.DownloadTicketResponse;
import com.workforcehub.file.dto.FileTrashRequest;
import com.workforcehub.file.dto.FileTrashResponse;
import com.workforcehub.file.repository.AttachmentRefRepository;
import com.workforcehub.file.repository.DownloadAttachmentRow;
import com.workforcehub.file.service.DownloadTicketExecutionService;
import com.workforcehub.file.storage.StorageObjectResource;
import com.workforcehub.file.storage.StorageProviderRegistry;
import com.workforcehub.im.dto.CreateGroupConversationRequest;
import com.workforcehub.im.dto.GroupMemberUserIdsRequest;
import com.workforcehub.im.dto.ImConversationMemberResponse;
import com.workforcehub.im.dto.ImGroupFileResponse;
import com.workforcehub.im.dto.ImConversationSummaryResponse;
import com.workforcehub.im.dto.ImGroupProfileResponse;
import com.workforcehub.im.dto.UpdateGroupConversationRequest;
import com.workforcehub.im.repository.ImConversationMemberRow;
import com.workforcehub.im.repository.ImConversationSummaryRow;
import com.workforcehub.im.repository.ImGroupFileRow;
import com.workforcehub.im.repository.ImGroupProfileRow;
import com.workforcehub.im.repository.ImGroupRepository;
import com.workforcehub.im.repository.ImRepository;
import com.workforcehub.im.websocket.ImWebSocketEvent;
import com.workforcehub.im.websocket.ImWebSocketEventTypes;
import com.workforcehub.im.websocket.ImWebSocketPushService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.workforcehub.common.util.TextValues.blankToNull;

/**
 * module-im 群聊资料与成员治理服务。
 */
// 群聊管理：创建/解散群、成员增删、群资料维护、群文件管理
@Service
public class ImGroupService {

    private final ImRepository imRepository;
    private final ImGroupRepository imGroupRepository;
    private final ImWebSocketPushService webSocketPushService;
    private final AttachmentRefRepository attachmentRefRepository;
    private final DownloadTicketExecutionService downloadTicketExecutionService;
    private final StorageProviderRegistry storageProviderRegistry;

    public ImGroupService(ImRepository imRepository,
                          ImGroupRepository imGroupRepository,
                          ImWebSocketPushService webSocketPushService,
                          AttachmentRefRepository attachmentRefRepository,
                          DownloadTicketExecutionService downloadTicketExecutionService,
                          StorageProviderRegistry storageProviderRegistry) {
        this.imRepository = imRepository;
        this.imGroupRepository = imGroupRepository;
        this.webSocketPushService = webSocketPushService;
        this.attachmentRefRepository = attachmentRefRepository;
        this.downloadTicketExecutionService = downloadTicketExecutionService;
        this.storageProviderRegistry = storageProviderRegistry;
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

        Long conversationId = IdGenerator.nextId();
        imGroupRepository.insertGroupConversation(conversationId, title, ownerUserId);
        imGroupRepository.insertGroupProfile(IdGenerator.nextId(), conversationId, "CUSTOM", ownerUserId, ownerUserId);
        imGroupRepository.insertUserMember(IdGenerator.nextId(), conversationId, ownerUserId, "OWNER", "CREATE");
        for (Long memberUserId : normalizedMemberIds) {
            imGroupRepository.insertUserMember(IdGenerator.nextId(), conversationId, memberUserId, "MEMBER", "CREATE");
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

    public List<ImGroupFileResponse> listCurrentEmployeeGroupFiles(Long conversationId) {
        Long userId = requireActiveEmployeeUserId();
        String memberRole = ensureCanAccessActiveGroupAndReturnRole(conversationId, userId);
        boolean canDelete = canDeleteGroupFile(memberRole);
        return imGroupRepository.listGroupFiles(conversationId)
            .stream()
            .map(row -> toGroupFileResponse(row, canDelete))
            .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public DownloadTicketResponse createCurrentEmployeeGroupFileDownloadTicket(Long conversationId,
                                                                                Long attachmentRefId,
                                                                                CreateDownloadTicketRequest request) {
        Long userId = requireActiveEmployeeUserId();
        ensureCanAccessActiveGroup(conversationId, userId);
        ImGroupFileRow file = getUsableGroupFileOrThrow(conversationId, attachmentRefId);
        if (!"SINGLE".equalsIgnoreCase(request.getDownloadType())) {
            throw new BizException(400, "第一版仅支持单文件下载");
        }

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
            "IM_GROUP_FILE_DOWNLOAD_TICKET_CREATED",
            "DOWNLOAD_TICKET",
            ticketId,
            "SUCCESS",
            "{\"conversationId\":%s,\"attachmentRefId\":%s}".formatted(conversationId, attachmentRefId)
        );

        DownloadTicketResponse response = new DownloadTicketResponse();
        response.setDownloadTicketId(ticketId);
        response.setTicketNo(ticketNo);
        response.setDownloadUrl(downloadUrl);
        response.setExpireAt(expireAt);
        response.setFileName(file.displayName());
        response.setFileSize(file.fileSize());
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public FileTrashResponse trashCurrentEmployeeGroupFile(Long conversationId,
                                                           Long attachmentRefId,
                                                           FileTrashRequest request) {
        Long userId = requireActiveEmployeeUserId();
        String memberRole = ensureCanAccessActiveGroupAndReturnRole(conversationId, userId);
        if (!canDeleteGroupFile(memberRole)) {
            throw new BizException(403, "只有群主或群管理员可以删除群文件");
        }
        ImGroupFileRow file = getUsableGroupFileOrThrow(conversationId, attachmentRefId);
        int updated = imGroupRepository.trashGroupFile(conversationId, attachmentRefId, userId);
        if (updated == 0) {
            throw new BizException(409, "群文件已删除");
        }
        imRepository.insertAudit(
            "USER",
            userId,
            "IM_GROUP_FILE_TRASHED",
            "ATTACHMENT_REF",
            attachmentRefId,
            "SUCCESS",
            "{\"conversationId\":%s,\"reason\":\"%s\"}".formatted(
                conversationId,
                safeJsonText(request.getReason())
            )
        );
        FileTrashResponse response = new FileTrashResponse();
        response.setAttachmentRefId(file.attachmentRefId());
        response.setDeletedAt(LocalDateTime.now());
        response.setPurgeAfter(LocalDateTime.now().plusDays(30));
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public DownloadFileResult downloadCurrentEmployeeGroupOrMessageAttachment(String ticketNo) {
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

        DownloadAttachmentRow attachment = attachmentRefRepository.getDownloadAttachmentOrThrow(ticket.attachmentRefId());
        Long conversationId = groupFileConversationId(attachment);
        ensureCanAccessActiveGroup(conversationId, userId);
        ensureDownloadableGroupFile(attachment);
        downloadTicketExecutionService.claimActiveTicket(ticket.id());
        StorageObjectResource resource = storageProviderRegistry.require(attachment.storageProvider())
            .loadObject(attachment.bucketName(), attachment.objectKey());
        imRepository.insertAudit(
            "USER",
            userId,
            "IM_GROUP_FILE_DOWNLOAD_EXECUTED",
            "DOWNLOAD_TICKET",
            ticket.id(),
            "SUCCESS",
            "{\"conversationId\":%s,\"attachmentRefId\":%s}".formatted(conversationId, ticket.attachmentRefId())
        );
        return new DownloadFileResult(
            resource.resource(),
            attachment.displayName(),
            attachment.mimeType(),
            resource.size()
        );
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
            imGroupRepository.upsertUserMember(IdGenerator.nextId(), conversationId, memberUserId, "MEMBER", "OWNER_INVITE");
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
        ensureCanAccessActiveGroupAndReturnRole(conversationId, userId);
    }

    private String ensureCanAccessActiveGroupAndReturnRole(Long conversationId, Long userId) {
        if (!imGroupRepository.existsActiveGroupConversation(conversationId)) {
            if (imRepository.existsConversation(conversationId)) {
                throw new BizException(400, "仅群聊支持成员列表");
            }
            throw new BizException(404, "群聊不存在");
        }
        String memberRole = imGroupRepository.findActiveUserMemberRole(conversationId, userId).orElse(null);
        if (memberRole == null) {
            throw new BizException(403, "无权访问该群聊");
        }
        return memberRole;
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

    private ImGroupFileResponse toGroupFileResponse(ImGroupFileRow row, boolean canDelete) {
        ImGroupFileResponse response = new ImGroupFileResponse();
        response.setAttachmentRefId(row.attachmentRefId());
        response.setConversationId(row.conversationId());
        response.setDisplayName(row.displayName());
        response.setMimeType(row.mimeType());
        response.setFileSize(row.fileSize());
        response.setContentSha256(row.contentSha256());
        response.setScanStatus(row.scanStatus());
        response.setUploaderId(row.uploaderId());
        response.setUploaderName(row.uploaderName());
        response.setCreatedAt(row.createdAt());
        response.setCanDelete(canDelete);
        return response;
    }

    private ImGroupFileRow getUsableGroupFileOrThrow(Long conversationId, Long attachmentRefId) {
        ImGroupFileRow file = imGroupRepository.findGroupFile(conversationId, attachmentRefId)
            .orElseThrow(() -> new BizException(404, "群文件不存在"));
        if (file.deletedAt() != null || file.attachmentDeleted() != 0 || file.fileDeleted() != 0) {
            throw new BizException(404, "群文件不存在");
        }
        if ("FAILED".equals(file.scanStatus())) {
            throw new BizException(409, "附件安全扫描未通过");
        }
        return file;
    }

    private void ensureDownloadableGroupFile(DownloadAttachmentRow attachment) {
        if (!"IM_GROUP_FILE".equals(attachment.ownerType())) {
            throw new BizException(404, "群文件不存在");
        }
        if (attachment.deletedAt() != null || attachment.attachmentDeleted() != 0 || attachment.fileDeleted() != 0) {
            throw new BizException(404, "群文件不存在");
        }
        if ("FAILED".equals(attachment.scanStatus())) {
            throw new BizException(409, "附件安全扫描未通过");
        }
    }

    private Long groupFileConversationId(DownloadAttachmentRow attachment) {
        return imGroupRepository.findGroupFileByAttachmentRefId(attachment.attachmentRefId())
            .map(ImGroupFileRow::conversationId)
            .orElseThrow(() -> new BizException(404, "群文件不存在"));
    }

    private boolean canDeleteGroupFile(String memberRole) {
        return "OWNER".equals(memberRole) || "ADMIN".equals(memberRole);
    }

    private String normalizeOptionalText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
    }

    private String safeJsonText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
