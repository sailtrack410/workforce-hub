package com.workforcehub.task.dto;

import java.time.LocalDateTime;

public record TeamTaskActivityResponse(Long activityId, Long actorId, String actorName,
                                       String kind, String content, LocalDateTime createdAt) {}
