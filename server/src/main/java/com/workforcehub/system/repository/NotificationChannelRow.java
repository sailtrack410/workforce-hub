package com.workforcehub.system.repository;

import java.time.LocalDateTime;

public record NotificationChannelRow(
    Long id,
    String channelCode,
    String channelType,
    String channelName,
    boolean enabled,
    String host,
    Integer port,
    String username,
    String secretValue,
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
