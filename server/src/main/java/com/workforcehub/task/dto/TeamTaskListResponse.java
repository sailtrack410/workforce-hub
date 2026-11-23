package com.workforcehub.task.dto;

public record TeamTaskListResponse(Long listId, String name, Long ownerId, long taskCount, boolean canEdit) {}
