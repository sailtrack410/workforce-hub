package com.workforcehub.attendance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAttendanceGroupRequest {

    @Size(max = 64, message = "考勤组编码不能超过64个字符")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "考勤组编码只能包含字母、数字、下划线和短横线")
    private String groupCode;

    @NotBlank(message = "考勤组名称不能为空")
    @Size(max = 128, message = "考勤组名称不能超过128个字符")
    private String groupName;

    @NotNull(message = "工作日历不能为空")
    private Long calendarId;

    @NotNull(message = "默认班次不能为空")
    private Long defaultShiftId;

    @NotNull(message = "迟到宽限分钟不能为空")
    @Min(value = 0, message = "迟到宽限分钟不能小于0")
    private Integer lateGraceMinutes;

    @NotNull(message = "早退宽限分钟不能为空")
    @Min(value = 0, message = "早退宽限分钟不能小于0")
    private Integer earlyLeaveGraceMinutes;

    @NotBlank(message = "考勤组状态不能为空")
    private String status;
}
