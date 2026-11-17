package com.workforcehub.leave.repository;

import java.time.LocalDateTime;

public record ApprovedLeavePeriod(LocalDateTime startAt, LocalDateTime endAt) {
}
