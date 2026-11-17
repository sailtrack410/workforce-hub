package com.workforcehub.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;

@Data
public class CreateAttendanceShiftRequest {

    @Size(max = 64, message = "班次编码不能超过64个字符")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "班次编码只能包含字母、数字、下划线和短横线")
    private String shiftCode;

    @NotBlank(message = "班次名称不能为空")
    @Size(max = 128, message = "班次名称不能超过128个字符")
    private String shiftName;

    @NotNull(message = "上班时间不能为空")
    private LocalTime startTime;

    @NotNull(message = "下班时间不能为空")
    private LocalTime endTime;

    private LocalTime onDutyPunchStart;

    private LocalTime onDutyPunchEnd;

    private LocalTime offDutyPunchStart;

    private LocalTime offDutyPunchEnd;

    @NotBlank(message = "班次状态不能为空")
    private String status;
}
