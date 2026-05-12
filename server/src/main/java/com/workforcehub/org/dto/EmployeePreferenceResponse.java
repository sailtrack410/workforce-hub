package com.workforcehub.org.dto;

import lombok.Data;

/**
 * 员工系统偏好（云端持久化）
 */
@Data
public class EmployeePreferenceResponse {

    /**
     * 主题：light / dark
     */
    private String theme;

    /**
     * 会话密度：cozy / compact
     */
    private String messageDensity;

    /**
     * 列表面板宽度（px）
     */
    private Integer listWidth;
}
