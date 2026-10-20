package com.workforcehub.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
    private String statusName;
    private String priority;
    private String priorityName;
    private LocalDateTime createdAt;
    @Schema(description = "AI预审结论: PASS/REVIEWING/BLOCKED", example = "PASS")
    private String aiVerdict;
    @Schema(description = "AI预审结论中文名", example = "AI 预审通过")
    private String aiVerdictName;
}
