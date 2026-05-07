package com.workforcehub.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AddAttendanceGroupMemberRequest {

    @NotNull(message = "员工不能为空")
    private Long userId;

    @NotNull(message = "生效开始日不能为空")
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @NotBlank(message = "来源类型不能为空")
    private String sourceType;
}
