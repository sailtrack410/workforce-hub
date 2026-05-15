package com.workforcehub.org.service;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.org.domain.EmpUserPreference;
import com.workforcehub.org.dto.EmployeePreferenceResponse;
import com.workforcehub.org.dto.UpdateEmployeePreferenceRequest;
import com.workforcehub.org.mapper.EmpUserPreferenceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 员工系统偏好云端持久化。
 * - 主题、会话密度、列表宽度、个性签名、在线状态、通知与外观个性化。
 * - 写时合并：仅覆盖请求中提供的字段。
 * - 读时返回默认值，避免首次登录时端上需要兜底。
 */
@Service
public class EmployeePreferenceService {

    public static final String DEFAULT_THEME = "light";
    public static final String DEFAULT_DENSITY = "cozy";
    public static final int DEFAULT_LIST_WIDTH = 400;
    public static final int MIN_LIST_WIDTH = 384;
    public static final int MAX_LIST_WIDTH = 520;
    public static final String DEFAULT_SIGNATURE = "";
    public static final String DEFAULT_PRESENCE = "ONLINE";
    public static final boolean DEFAULT_NOTIF_SOUND = true;
    public static final boolean DEFAULT_NOTIF_DESKTOP = true;
    public static final boolean DEFAULT_NOTIF_MENTION_ONLY = false;
    public static final boolean DEFAULT_NOTIF_UNREAD_BADGE = true;
    public static final String DEFAULT_ACCENT = "indigo";
    public static final String DEFAULT_FONT_SCALE = "medium";
    public static final String DEFAULT_BUBBLE = "rounded";
    public static final int MAX_SIGNATURE_LENGTH = 140;

    private final EmpUserPreferenceMapper preferenceMapper;

    public EmployeePreferenceService(EmpUserPreferenceMapper preferenceMapper) {
        this.preferenceMapper = preferenceMapper;
    }

    public EmployeePreferenceResponse getCurrent() {
        ensureEmployeeContext();
        Long userId = CurrentUserHolder.requireUserId();
        EmpUserPreference row = preferenceMapper.selectById(userId);
        return toResponse(row);
    }

    @Transactional(rollbackFor = Exception.class)
    public EmployeePreferenceResponse updateCurrent(UpdateEmployeePreferenceRequest request) {
        ensureEmployeeContext();
        Long userId = CurrentUserHolder.requireUserId();
        EmpUserPreference existing = preferenceMapper.selectById(userId);
        boolean inserting = existing == null;
        EmpUserPreference entity = inserting ? new EmpUserPreference() : existing;
        if (inserting) {
            entity.setUserId(userId);
            entity.setTheme(DEFAULT_THEME);
            entity.setMessageDensity(DEFAULT_DENSITY);
            entity.setListWidth(DEFAULT_LIST_WIDTH);
            entity.setSignature(DEFAULT_SIGNATURE);
            entity.setPresenceStatus(DEFAULT_PRESENCE);
            entity.setNotifSound(DEFAULT_NOTIF_SOUND);
            entity.setNotifDesktop(DEFAULT_NOTIF_DESKTOP);
            entity.setNotifMentionOnly(DEFAULT_NOTIF_MENTION_ONLY);
            entity.setNotifUnreadBadge(DEFAULT_NOTIF_UNREAD_BADGE);
            entity.setAccentColor(DEFAULT_ACCENT);
            entity.setFontScale(DEFAULT_FONT_SCALE);
            entity.setBubbleStyle(DEFAULT_BUBBLE);
        }
        if (request != null) {
            if (request.getTheme() != null) {
                entity.setTheme(request.getTheme());
            }
            if (request.getMessageDensity() != null) {
                entity.setMessageDensity(request.getMessageDensity());
            }
            if (request.getListWidth() != null) {
                entity.setListWidth(clampListWidth(request.getListWidth()));
            }
            if (request.getSignature() != null) {
                entity.setSignature(clampSignature(request.getSignature()));
            }
            if (request.getPresenceStatus() != null) {
                entity.setPresenceStatus(request.getPresenceStatus());
            }
            if (request.getNotifSound() != null) {
                entity.setNotifSound(request.getNotifSound());
            }
            if (request.getNotifDesktop() != null) {
                entity.setNotifDesktop(request.getNotifDesktop());
            }
            if (request.getNotifMentionOnly() != null) {
                entity.setNotifMentionOnly(request.getNotifMentionOnly());
            }
            if (request.getNotifUnreadBadge() != null) {
                entity.setNotifUnreadBadge(request.getNotifUnreadBadge());
            }
            if (request.getAccentColor() != null) {
                entity.setAccentColor(request.getAccentColor());
            }
            if (request.getFontScale() != null) {
                entity.setFontScale(request.getFontScale());
            }
            if (request.getBubbleStyle() != null) {
                entity.setBubbleStyle(request.getBubbleStyle());
            }
        }
        if (inserting) {
            preferenceMapper.insert(entity);
        } else {
            preferenceMapper.updateById(entity);
        }
        return toResponse(entity);
    }

    private EmployeePreferenceResponse toResponse(EmpUserPreference row) {
        EmployeePreferenceResponse response = new EmployeePreferenceResponse();
        response.setTheme(row != null && row.getTheme() != null ? row.getTheme() : DEFAULT_THEME);
        response.setMessageDensity(row != null && row.getMessageDensity() != null ? row.getMessageDensity() : DEFAULT_DENSITY);
        response.setListWidth(row != null && row.getListWidth() != null ? clampListWidth(row.getListWidth()) : DEFAULT_LIST_WIDTH);
        response.setSignature(row != null && row.getSignature() != null ? row.getSignature() : DEFAULT_SIGNATURE);
        response.setPresenceStatus(row != null && row.getPresenceStatus() != null ? row.getPresenceStatus() : DEFAULT_PRESENCE);
        response.setNotifSound(row != null && row.getNotifSound() != null ? row.getNotifSound() : DEFAULT_NOTIF_SOUND);
        response.setNotifDesktop(row != null && row.getNotifDesktop() != null ? row.getNotifDesktop() : DEFAULT_NOTIF_DESKTOP);
        response.setNotifMentionOnly(row != null && row.getNotifMentionOnly() != null ? row.getNotifMentionOnly() : DEFAULT_NOTIF_MENTION_ONLY);
        response.setNotifUnreadBadge(row != null && row.getNotifUnreadBadge() != null ? row.getNotifUnreadBadge() : DEFAULT_NOTIF_UNREAD_BADGE);
        response.setAccentColor(row != null && row.getAccentColor() != null ? row.getAccentColor() : DEFAULT_ACCENT);
        response.setFontScale(row != null && row.getFontScale() != null ? row.getFontScale() : DEFAULT_FONT_SCALE);
        response.setBubbleStyle(row != null && row.getBubbleStyle() != null ? row.getBubbleStyle() : DEFAULT_BUBBLE);
        return response;
    }

    private int clampListWidth(int width) {
        return Math.max(MIN_LIST_WIDTH, Math.min(MAX_LIST_WIDTH, width));
    }

    private String clampSignature(String raw) {
        String trimmed = raw.strip();
        if (trimmed.length() > MAX_SIGNATURE_LENGTH) {
            return trimmed.substring(0, MAX_SIGNATURE_LENGTH);
        }
        return trimmed;
    }

    private void ensureEmployeeContext() {
        if (!CurrentUserHolder.isEmployee()) {
            throw new BizException(403, "仅员工端支持读取个人偏好");
        }
    }
}
