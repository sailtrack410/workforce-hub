package com.workforcehub.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * Agent 任务状态
 */
@Getter
public enum AgentTaskStatus {

    PENDING("PENDING", "待执行"),
    RUNNING("RUNNING", "执行中"),
    WAITING_HUMAN_CONFIRM("WAITING_HUMAN_CONFIRM", "等待人工确认"),
    SUCCEEDED("SUCCEEDED", "已完成"),
    REJECTED("REJECTED", "已驳回"),
    FAILED("FAILED", "执行失败"),
    CANCELLED("CANCELLED", "已取消");

    @EnumValue
    private final String code;
    private final String desc;

    AgentTaskStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
