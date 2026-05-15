package com.workforcehub.org.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    @Size(max = 140, message = "signature 长度不能超过 140")
    private String signature;

    @Pattern(regexp = "ONLINE|BUSY|AWAY|DND", message = "presenceStatus 必须是 ONLINE / BUSY / AWAY / DND")
    private String presenceStatus;

    private Boolean notifSound;
    private Boolean notifDesktop;
    private Boolean notifMentionOnly;
    private Boolean notifUnreadBadge;

    @Pattern(regexp = "indigo|violet|blue|teal|amber|rose",
        message = "accentColor 必须是 indigo / violet / blue / teal / amber / rose")
    private String accentColor;

    @Pattern(regexp = "small|medium|large", message = "fontScale 必须是 small / medium / large")
    private String fontScale;

    @Pattern(regexp = "rounded|sharp", message = "bubbleStyle 必须是 rounded 或 sharp")
    private String bubbleStyle;
}
