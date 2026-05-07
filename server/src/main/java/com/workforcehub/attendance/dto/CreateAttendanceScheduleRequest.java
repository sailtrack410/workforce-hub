package com.workforcehub.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateAttendanceScheduleRequest {

    @NotNull(message = "员工不能为空")
    private Long userId;

    @NotNull(message = "排班日期不能为空")
    private LocalDate workDate;

    @NotNull(message = "班次不能为空")
    private Long shiftId;

    private Long calendarId;

    @NotBlank(message = "排班来源不能为空")
    private String scheduleSource;
}
