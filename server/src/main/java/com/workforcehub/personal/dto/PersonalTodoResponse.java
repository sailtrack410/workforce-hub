package com.workforcehub.personal.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个人待办响应。
 */
@Data
public class PersonalTodoResponse {

    private Long todoId;

    private Long ownerId;

    private String title;

    private String category;

    private String status;

    private LocalDateTime dueAt;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}
