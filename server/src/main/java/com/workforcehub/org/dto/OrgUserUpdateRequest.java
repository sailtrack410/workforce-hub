package com.workforcehub.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 员工编辑请求
 */
@Data
public class OrgUserUpdateRequest {

    @NotBlank(message = "employeeNo 不能为空")
    @Size(max = 64, message = "employeeNo 长度不能超过 64")
    private String employeeNo;

    @NotBlank(message = "realName 不能为空")
    @Size(max = 64, message = "realName 长度不能超过 64")
    private String realName;

    @Size(max = 16, message = "gender 长度不能超过 16")
    private String gender;

    @Size(max = 32, message = "mobile 长度不能超过 32")
    private String mobile;

    @NotNull(message = "primaryDeptId 不能为空")
    private Long primaryDeptId;

    private Long positionId;

    private Long managerUserId;

    private LocalDate hireDate;

    private LocalDate regularizedAt;

    private LocalDate serviceStartDate;

    private LocalDate annualLeaveStartDate;

    @Size(max = 128, message = "workLocation 长度不能超过 128")
    private String workLocation;

    @Size(max = 32, message = "status 长度不能超过 32")
    private String status;
}
