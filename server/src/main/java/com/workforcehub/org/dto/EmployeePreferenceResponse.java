package com.workforcehub.org.dto;

import lombok.Data;

/**
 * 员工系统偏好（云端持久化）
 */
@Data
public class EmployeePreferenceResponse {

    /**
     * 主题：light / dark
     */
    private String theme;

    /**
     * 会话密度：cozy / compact
     */
    private String messageDensity;

    /**
     * 列表面板宽度（px）
     */
    private Integer listWidth;

    /**
     * 个性签名
     */
    private String signature;

    /**
     * 在线状态：ONLINE / BUSY / AWAY / DND
     */
    private String presenceStatus;

    /**
     * 声音提醒
     */
    private Boolean notifSound;

    /**
     * 桌面通知
     */
    private Boolean notifDesktop;

    /**
     * 仅 @ 我提醒
     */
    private Boolean notifMentionOnly;

    /**
     * 未读角标
     */
    private Boolean notifUnreadBadge;

    /**
     * 强调色：indigo / violet / blue / teal / amber / rose
     */
    private String accentColor;

    /**
     * 字号：small / medium / large
     */
    private String fontScale;

    /**
     * 气泡样式：rounded / sharp
     */
    private String bubbleStyle;
}
