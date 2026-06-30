package com.workforcehub.org.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工系统偏好持久化
 * 对应数据库表 emp_user_preference
 */
@Data
public class EmpUserPreference {

    private Long userId;

    private String theme;
    private String messageDensity;
    private Integer listWidth;

    private String signature;
    private String presenceStatus;

    private Boolean notifSound;
    private Boolean notifDesktop;
    private Boolean notifMentionOnly;
    private Boolean notifUnreadBadge;

    private String accentColor;
    private String fontScale;
    private String bubbleStyle;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
