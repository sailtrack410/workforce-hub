package com.workforcehub.org.dto;

import lombok.Data;

/**
 * 员工摘要
 */
@Data
public class OrgUserSummaryResponse {

    private Long userId;
    private String employeeNo;
    private String realName;
    private Long primaryDeptId;
    private String primaryDeptName;
    private Long positionId;
    private String positionName;
    private Long managerUserId;
    private String managerName;
    private String employmentStatus;
    private String status;
}
