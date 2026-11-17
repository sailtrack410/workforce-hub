package com.workforcehub.ragflow.repository;

import java.time.LocalDateTime;

public record RagflowAclRow(
    Long id,
    Long spaceId,
    String subjectType,
    Long subjectId,
    String subjectName,
    String subjectCode,
    String permCode,
    String status,
    LocalDateTime createdAt
) {
}
