package com.workforcehub.system.service;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.system.dto.SysNotificationResponse;
import com.workforcehub.system.repository.SysNotificationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 员工站内通知服务（接口需求表 #10）。
 * 写入方：审批流转/系统公告等场景后续接入；当前先提供读取与已读能力。
 */
@Service
public class SysNotificationService {

    private static final List<String> NOTIFICATION_TYPES = List.of("APPROVAL", "SYSTEM", "ATTENDANCE", "ANNOUNCEMENT");

    private final SysNotificationRepository notificationRepository;

    public SysNotificationService(SysNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** 分页查我的通知 */
    public List<SysNotificationResponse> pageMyNotifications(long pageNo, long pageSize) {
        Long userId = CurrentUserHolder.requireUserId();
        return notificationRepository.pageMyNotifications(userId, (int) pageNo, (int) pageSize).stream()
            .map(this::toResponse)
            .toList();
    }

    /** 我的未读数 */
    public long unreadCount() {
        Long userId = CurrentUserHolder.requireUserId();
        return notificationRepository.countUnread(userId);
    }

    /** 标记单条已读 */
    public void markRead(String notificationId) {
        Long userId = CurrentUserHolder.requireUserId();
        int updated = notificationRepository.markRead(userId, Long.valueOf(notificationId));
        if (updated == 0) {
            throw new BizException(404, "通知不存在或已读");
        }
    }

    /** 全部已读 */
    public void markAllRead() {
        Long userId = CurrentUserHolder.requireUserId();
        notificationRepository.markAllRead(userId);
    }

    /** 通知写入入口（供审批流转/公告等场景调用） */
    public void push(Long userId, String type, String title, String content, String linkType, String linkId) {
        if (!NOTIFICATION_TYPES.contains(type)) {
            throw new BizException(400, "不支持的通知类型");
        }
        notificationRepository.insert(IdGenerator.nextId(), userId, type, title, content, linkType, linkId);
    }

    private SysNotificationResponse toResponse(Map<String, Object> row) {
        SysNotificationResponse response = new SysNotificationResponse();
        response.setNotificationId(String.valueOf(row.get("id")));
        response.setType((String) row.get("type"));
        response.setTitle((String) row.get("title"));
        response.setContent((String) row.get("content"));
        response.setLinkType((String) row.get("link_type"));
        response.setLinkId(row.get("link_id") == null ? null : String.valueOf(row.get("link_id")));
        response.setRead(((Number) row.get("read_flag")).intValue() == 1);
        response.setCreatedAt(((LocalDateTime) row.get("created_at")));
        return response;
    }
}
