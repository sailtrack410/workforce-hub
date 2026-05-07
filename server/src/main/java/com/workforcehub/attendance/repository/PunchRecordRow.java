package com.workforcehub.attendance.repository;

import java.time.LocalDateTime;

public record PunchRecordRow(
    Long punchRecordId,
    Long userId,
    LocalDateTime punchAt,
    String punchType,
    String sourceType,
    String status
) {
}
