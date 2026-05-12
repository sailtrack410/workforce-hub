package com.workforcehub.org.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 员工详情
 */
@Data
public class OrgUserDetailResponse {

    private Long userId;
    private String employeeNo;
    private String realName;
    private String gender;
    private String mobile;
    private Long avatarAttachmentRefId;
    private Long primaryDeptId;
    private String primaryDeptName;
    private Long positionId;
    private String positionName;
    private Long managerUserId;
    private String managerName;
    private String employmentStatus;
    private String status;
    private LocalDate hireDate;
    private LocalDate regularizedAt;
    private LocalDate serviceStartDate;
    private LocalDate annualLeaveStartDate;
    private String workLocation;
}
