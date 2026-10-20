package com.workforcehub.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 抄送我的记录响应（接口需求表 #7）。
 */
@Data
public class WorkflowCcResponse {

    @Schema(description = "申请ID", example = "2054016024106426370")
    private String requestId;

    @Schema(description = "申请编号", example = "WF-2026-1012-007")
    private String requestNo;

    @Schema(description = "业务类型名", example = "请假")
    private String bizTypeName;

    @Schema(description = "申请标题", example = "请假申请 · 年假 2 天")
    private String title;

    @Schema(description = "申请人", example = "张伟")
    private String applicantName;

    @Schema(description = "抄送时间", example = "2026-10-12T09:30:00")
    private LocalDateTime ccAt;

    @Schema(description = "申请状态", example = "IN_APPROVAL")
    private String status;

    @Schema(description = "申请状态名", example = "审批中")
    private String statusName;

    @Schema(description = "是否已读", example = "false")
    private Boolean read;
}
