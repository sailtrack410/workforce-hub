package com.workforcehub.file.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateQuotaPolicyRequest {

    @NotBlank(message = "策略编码不能为空")
    @Size(max = 128, message = "策略编码不能超过128个字符")
    private String policyCode;

    @NotBlank(message = "策略名称不能为空")
    @Size(max = 128, message = "策略名称不能超过128个字符")
    private String policyName;

    @NotBlank(message = "作用域类型不能为空")
    private String scopeType;

    private Long scopeId;

    @NotNull(message = "配额不能为空")
    @Min(value = 1, message = "配额必须大于0")
    private Long quotaBytes;

    @Min(value = 0, message = "缓存配额不能小于0")
    private Long cacheQuotaBytes;

    @NotNull(message = "预警阈值不能为空")
    @DecimalMin(value = "0.0000", message = "预警阈值不能小于0")
    @DecimalMax(value = "1.0000", message = "预警阈值不能大于1")
    private BigDecimal warningThreshold;

    @NotNull(message = "阻断阈值不能为空")
    @DecimalMin(value = "0.0001", message = "阻断阈值必须大于0")
    @DecimalMax(value = "1.0000", message = "阻断阈值不能大于1")
    private BigDecimal blockThreshold;

    @NotBlank(message = "状态不能为空")
    private String status;
}
