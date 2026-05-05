package com.workforcehub.file.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RunCleanupJobRequest {

    @NotBlank(message = "清理任务类型不能为空")
    private String jobType;

    private Boolean dryRun;
}
