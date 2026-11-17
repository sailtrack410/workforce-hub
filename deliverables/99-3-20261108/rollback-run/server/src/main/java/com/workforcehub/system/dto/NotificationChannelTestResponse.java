package com.workforcehub.system.dto;

import java.time.LocalDateTime;

public record NotificationChannelTestResponse(
    boolean success,
    String message,
    LocalDateTime testedAt
) {}
