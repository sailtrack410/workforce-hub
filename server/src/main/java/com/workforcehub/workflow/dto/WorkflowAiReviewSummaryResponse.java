package com.workforcehub.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * AI 预审统计响应（接口需求表 #6 期望A）。
 */
@Data
public class WorkflowAiReviewSummaryResponse {

    @Schema(description = "本周已预审单数", example = "17")
    private Long weekReviewed;

    @Schema(description = "预审通过率(0-1)", example = "0.82")
    private Double passRate;

    @Schema(description = "硬规则拦截单数", example = "3")
    private Long blockedCount;

    @Schema(description = "拦截规则分布", example = "[{\"ruleName\": \"请假提前3天\", \"count\": 2}]")
    private List<BlockedRule> blockedRules;

    @Data
    public static class BlockedRule {
        @Schema(description = "规则名", example = "年假余额充足")
        private String ruleName;
        @Schema(description = "次数", example = "2")
        private Long count;
    }
}
