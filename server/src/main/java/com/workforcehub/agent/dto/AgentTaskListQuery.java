package com.workforcehub.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 后台 Agent 任务分页查询参数。
 */
@Data
public class AgentTaskListQuery {

    @Min(value = 1, message = "pageNo 最小为 1")
    private long pageNo = 1;

    @Min(value = 1, message = "pageSize 最小为 1")
    @Max(value = 100, message = "pageSize 最大为 100")
    private long pageSize = 20;

    private String status;
    private Long agentId;
    private String triggeredByType;
    private Long triggeredById;
    private String contextType;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdTo;
}
