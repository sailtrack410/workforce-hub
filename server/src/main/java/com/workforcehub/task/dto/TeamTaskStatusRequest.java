package com.workforcehub.task.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 团队任务状态变更请求。
 */
@Data
public class TeamTaskStatusRequest {

    @NotBlank(message = "状态不能为空")
    private String status;
}
