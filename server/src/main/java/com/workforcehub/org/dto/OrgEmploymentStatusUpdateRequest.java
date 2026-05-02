package com.workforcehub.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 员工生命周期状态更新请求
 */
@Data
public class OrgEmploymentStatusUpdateRequest {

    @NotBlank(message = "employmentStatus 不能为空")
    @Size(max = 32, message = "employmentStatus 长度不能超过 32")
    private String employmentStatus;
}
