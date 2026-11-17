package com.workforcehub.plugin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record PluginConfigSaveRequest(
    @NotNull(message = "expectedVersion 不能为空")
    @PositiveOrZero(message = "expectedVersion 不能小于 0")
    Long expectedVersion,
    @NotBlank(message = "配置类型不能为空") String valueType,
    String value,
    String defaultValue,
    @Size(max = 256, message = "配置说明不能超过 256 个字符") String description,
    boolean requiresRestart
) {}
