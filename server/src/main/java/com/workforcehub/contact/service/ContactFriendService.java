package com.workforcehub.contact.service;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.contact.domain.ContactFriend;
import com.workforcehub.contact.mapper.ContactFriendMapper;
import com.workforcehub.org.dto.OrgUserSummaryResponse;
import com.workforcehub.org.mapper.OrgDirectoryMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 员工好友关系服务
 */
// 好友关系：好友列表、发送/接受/拒绝好友请求，防止重复添加和自加好友
@Service
public class ContactFriendService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_PENDING = "PENDING";

    private final ContactFriendMapper contactFriendMapper;
    private final OrgDirectoryMapper orgDirectoryMapper;

    public ContactFriendService(ContactFriendMapper contactFriendMapper,
                                OrgDirectoryMapper orgDirectoryMapper) {
        this.contactFriendMapper = contactFriendMapper;
        this.orgDirectoryMapper = orgDirectoryMapper;
    }

    public List<OrgUserSummaryResponse> listCurrentFriends() {
        Long userId = CurrentUserHolder.requireUserId();
        return contactFriendMapper.selectFriends(userId);
    }

    public List<OrgUserSummaryResponse> listCurrentFriendRequests() {
        Long userId = CurrentUserHolder.requireUserId();
        return contactFriendMapper.selectIncomingRequests(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrgUserSummaryResponse addCurrentFriend(Long friendUserId, String remark) {
        Long userId = CurrentUserHolder.requireUserId();
        if (friendUserId == null || friendUserId.equals(userId)) {
            throw new BizException(400, "好友用户无效");
        }
        OrgUserSummaryResponse friend = orgDirectoryMapper.selectUserSummaryById(friendUserId);
        if (friend == null) {
            throw new BizException(404, "用户不存在");
        }
        ContactFriend existing = contactFriendMapper.selectFriend(userId, friendUserId);
        if (existing != null) {
            if (STATUS_ACTIVE.equals(existing.getStatus())) {
                throw new BizException(409, "已是好友");
            }
            throw new BizException(409, "已发送好友申请");
        }
        ContactFriend incoming = contactFriendMapper.selectFriend(friendUserId, userId);
        if (incoming != null && STATUS_PENDING.equals(incoming.getStatus())) {
            return acceptFriendRequest(friendUserId);
        }
        ContactFriend row = new ContactFriend();
        row.setUserId(userId);
        row.setFriendUserId(friendUserId);
        row.setRemark(trimToNull(remark));
        row.setStatus(STATUS_PENDING);
        row.setCreatedBy(userId);
        row.setUpdatedBy(userId);
        try {
            contactFriendMapper.insertFriend(row);
        } catch (DuplicateKeyException ex) {
            throw new BizException(409, "已发送好友申请");
        }
        return orgDirectoryMapper.selectUserSummaryById(friendUserId);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrgUserSummaryResponse acceptFriendRequest(Long requesterUserId) {
        Long userId = CurrentUserHolder.requireUserId();
        ContactFriend incoming = contactFriendMapper.selectFriend(requesterUserId, userId);
        if (incoming == null || !STATUS_PENDING.equals(incoming.getStatus())) {
            throw new BizException(404, "好友申请不存在");
        }
        contactFriendMapper.updateStatus(requesterUserId, userId, STATUS_ACTIVE, userId);
        ContactFriend existing = contactFriendMapper.selectFriend(userId, requesterUserId);
        if (existing == null) {
            ContactFriend row = new ContactFriend();
            row.setUserId(userId);
            row.setFriendUserId(requesterUserId);
            row.setStatus(STATUS_ACTIVE);
            row.setCreatedBy(userId);
            row.setUpdatedBy(userId);
            contactFriendMapper.insertFriend(row);
        } else if (!STATUS_ACTIVE.equals(existing.getStatus())) {
            contactFriendMapper.updateStatus(userId, requesterUserId, STATUS_ACTIVE, userId);
        }
        return orgDirectoryMapper.selectUserSummaryById(requesterUserId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeCurrentFriend(Long friendUserId) {
        Long userId = CurrentUserHolder.requireUserId();
        contactFriendMapper.deleteFriend(userId, friendUserId);
        contactFriendMapper.deleteFriend(friendUserId, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrgUserSummaryResponse updateCurrentFriendRemark(Long friendUserId, String remark) {
        Long userId = CurrentUserHolder.requireUserId();
        ContactFriend existing = contactFriendMapper.selectFriend(userId, friendUserId);
        if (existing == null || !STATUS_ACTIVE.equals(existing.getStatus())) {
            throw new BizException(404, "好友不存在");
        }
        contactFriendMapper.updateRemark(userId, friendUserId, trimToNull(remark));
        return orgDirectoryMapper.selectUserSummaryById(friendUserId);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
