package com.workforcehub.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工站内通知响应（接口需求表 #10）。
 */
@Data
public class SysNotificationResponse {

    @Schema(description = "通知ID", example = "2054016024106426370")
    private String notificationId;

    @Schema(description = "类型: APPROVAL/SYSTEM/ATTENDANCE/ANNOUNCEMENT", example = "APPROVAL")
    private String type;

    @Schema(description = "标题", example = "请假申请已通过直属上级审批")
    private String title;

    @Schema(description = "内容", example = "您的年假申请已通过")
    private String content;

    @Schema(description = "跳转类型", example = "APPROVAL_DETAIL")
    private String linkType;

    @Schema(description = "跳转目标ID", example = "2054016024106426371")
    private String linkId;

    @Schema(description = "是否已读", example = "false")
    private Boolean read;

    @Schema(description = "创建时间", example = "2026-10-14T09:30:00")
    private LocalDateTime createdAt;
}
