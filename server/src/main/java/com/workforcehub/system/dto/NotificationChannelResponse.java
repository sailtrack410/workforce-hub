package com.workforcehub.system.dto;

import java.time.LocalDateTime;

public record NotificationChannelResponse(
    String channelCode,
    String channelType,
    String channelName,
    boolean enabled,
    String host,
    Integer port,
    String username,
    boolean hasSecret,
    String fromAddress,
    String fromName,
    boolean sslEnabled,
    boolean starttlsEnabled,
    Integer timeoutMillis,
    Boolean lastTestSuccess,
    String lastTestMessage,
    LocalDateTime lastTestedAt,
    LocalDateTime updatedAt
) {}
