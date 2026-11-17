package com.workforcehub.attendance.repository;

import java.time.Duration;
import java.time.LocalDateTime;

public record AttendanceWorkPeriod(LocalDateTime startAt, LocalDateTime endAt) {
    public int overlapMinutes(LocalDateTime start, LocalDateTime end) {
        LocalDateTime from = startAt.isAfter(start) ? startAt : start;
        LocalDateTime to = endAt.isBefore(end) ? endAt : end;
        return to.isAfter(from) ? Math.toIntExact(Duration.between(from, to).toMinutes()) : 0;
    }

    public int minutes() {
        return Math.toIntExact(Duration.between(startAt, endAt).toMinutes());
    }
}
