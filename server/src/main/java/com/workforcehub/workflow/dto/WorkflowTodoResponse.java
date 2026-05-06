package com.workforcehub.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工端审批待办响应。
 */
@Data
public class WorkflowTodoResponse {

    private Long todoId;
    private Long requestId;
    private String taskId;
    private String title;
    private String summary;
    private String status;
    private String priority;
    private LocalDateTime createdAt;
}
