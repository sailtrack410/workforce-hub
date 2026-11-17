package com.workforcehub.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateAttendanceScheduleRequest {

    @NotNull(message = "班次不能为空")
    private Long shiftId;

    private Long calendarId;

    @NotBlank(message = "排班来源不能为空")
    private String scheduleSource;

    @NotBlank(message = "排班状态不能为空")
    private String status;
}
