package com.workforcehub.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 团队任务创建/更新请求。
 */
@Data
public class TeamTaskSaveRequest {

    @NotBlank(message = "标题不能为空")
    private String title;

    private String description;

    @NotNull(message = "负责人员工ID不能为空")
    private Long assigneeId;

    private LocalDate startDate;

    private LocalDate endDate;

    private String groupId;
}
