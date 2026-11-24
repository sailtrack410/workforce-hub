package com.workforcehub.personal.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个人待办响应。
 */
@Data
public class PersonalTodoResponse {
    private Integer version;

    private Long todoId;

    private Long ownerId;

    private String title;

    private String category;

    private String status;

    private LocalDateTime startAt;

    private LocalDateTime dueAt;

    private boolean allDay;

    private Integer reminderMinutes;

    private LocalDateTime reminderAt;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}
