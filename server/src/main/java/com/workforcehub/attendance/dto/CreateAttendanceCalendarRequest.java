package com.workforcehub.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAttendanceCalendarRequest {

    @NotBlank(message = "日历编码不能为空")
    @Size(max = 64, message = "日历编码不能超过64个字符")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "日历编码只能包含字母、数字、下划线和短横线")
    private String calendarCode;

    @NotBlank(message = "日历名称不能为空")
    @Size(max = 128, message = "日历名称不能超过128个字符")
    private String calendarName;

    @NotBlank(message = "日历类型不能为空")
    private String calendarType;

    @NotBlank(message = "日历状态不能为空")
    private String status;
}
