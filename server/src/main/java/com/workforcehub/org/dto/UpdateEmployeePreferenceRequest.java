package com.workforcehub.org.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 员工系统偏好更新请求。
 * 所有字段可选，缺省字段不会覆盖既有值。
 */
@Data
public class UpdateEmployeePreferenceRequest {

    @Pattern(regexp = "light|dark", message = "theme 必须是 light 或 dark")
    private String theme;

    @Pattern(regexp = "cozy|compact", message = "messageDensity 必须是 cozy 或 compact")
    private String messageDensity;

    /**
     * 列表面板宽度（px），有效区间 [384, 520]。
     */
    private Integer listWidth;
}
