package com.workforcehub.workflow.repository;

import java.time.LocalDateTime;

/**
 * 审批待办读模型。
 */
public record WorkflowTodoRow(
    Long todoId,
    Long requestId,
    String taskId,
    String title,
    String summary,
    String status,
    String priority,
    LocalDateTime createdAt
) {
}
