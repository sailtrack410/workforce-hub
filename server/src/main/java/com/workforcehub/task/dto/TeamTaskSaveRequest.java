package com.workforcehub.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 团队任务创建/更新请求。
 */
@Data
public class TeamTaskSaveRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 128, message = "标题不能超过128个字符")
    private String title;

    @Size(max = 1024, message = "描述不能超过1024个字符")
    private String description;

    @NotNull(message = "负责人员工ID不能为空")
    @Positive(message = "负责人员工ID必须为正数")
    private Long assigneeId;

    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    @Size(max = 64, message = "关联标识不能超过64个字符")
    private String groupId;
}
