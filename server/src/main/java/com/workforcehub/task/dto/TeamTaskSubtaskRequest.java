package com.workforcehub.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamTaskSubtaskRequest(@NotBlank(message = "子任务标题不能为空")
                                     @Size(max = 128, message = "子任务标题不能超过128个字符") String title,
                                     boolean completed) {}
