package com.workforcehub.task.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 团队任务响应。
 */
@Data
public class TeamTaskResponse {

    private Long taskId;

    private String title;

    private String description;

    private Long assigneeId;

    private Long creatorId;

    private String creatorName;

    private String assigneeName;

    private boolean canEdit;

    private boolean canDelete;

    private boolean canStart;

    private boolean canComplete;

    private boolean canReopen;

    private String status;

    private LocalDate startDate;

    private LocalDate endDate;

    private String groupId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
