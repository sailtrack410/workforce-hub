package com.workforcehub.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record NotificationChannelSaveRequest(
    boolean enabled,
    @NotBlank(message = "邮件服务器不能为空") String host,
    @Min(value = 1, message = "端口不正确") @Max(value = 65535, message = "端口不正确") Integer port,
    String username,
    String secretValue,
    @NotBlank(message = "发件地址不能为空") @Email(message = "发件地址格式不正确") String fromAddress,
    String fromName,
    boolean sslEnabled,
    boolean starttlsEnabled,
    @Min(value = 100, message = "超时时间不正确") @Max(value = 60000, message = "超时时间不正确") Integer timeoutMillis
) {}
