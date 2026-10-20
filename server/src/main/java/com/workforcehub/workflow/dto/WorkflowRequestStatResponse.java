package com.workforcehub.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 我发起的审批统计响应（接口需求表 #8）。
 */
@Data
public class WorkflowRequestStatResponse {

    @Schema(description = "进行中单数", example = "3")
    private Long inFlight;

    @Schema(description = "本月已完成单数", example = "12")
    private Long finishedThisMonth;

    @Schema(description = "平均处理时长(小时,无完成数据时为null)", example = "2.4")
    private Double avgHandleHours;
}
