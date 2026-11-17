package com.workforcehub.auth.domain;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工/领导业务主表
 * 对应数据库表 org_user
 */
@Data
public class OrgUser {

    private Long id;

    private String employeeNo;
    private String realName;
    private String gender;
    private String mobile;
    private Long avatarAttachmentRefId;
    private Long primaryDeptId;
    private Long positionId;
    private Long managerUserId;

    /**
     * 在职状态：PENDING_ONBOARD/PROBATION/ACTIVE/PENDING_OFFBOARD/OFFBOARDED
     */
    private String employmentStatus;

    private LocalDate hireDate;
    private LocalDate regularizedAt;
    private LocalDate serviceStartDate;
    private LocalDate annualLeaveStartDate;
    private String workLocation;

    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;

    private Integer isDeleted;
}
