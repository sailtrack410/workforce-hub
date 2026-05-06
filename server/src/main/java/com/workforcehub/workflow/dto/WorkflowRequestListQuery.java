package com.workforcehub.workflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 员工端我的审批申请分页查询。
 */
@Data
public class WorkflowRequestListQuery {

    @Min(value = 1, message = "pageNo 最小为 1")
    private long pageNo = 1;

    @Min(value = 1, message = "pageSize 最小为 1")
    @Max(value = 100, message = "pageSize 最大为 100")
    private long pageSize = 20;

    private String status;
    private String bizType;
}
