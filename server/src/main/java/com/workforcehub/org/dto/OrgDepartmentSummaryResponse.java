package com.workforcehub.org.dto;

import lombok.Data;

/**
 * 部门摘要
 */
@Data
public class OrgDepartmentSummaryResponse {

    private Long id;
    private Long parentId;
    private String treePath;
    private String deptCode;
    private String deptName;
    private String deptType;
    private String deptTypeName;
    private Integer deptLevel;
    private Long leaderUserId;
    private String leaderName;
    private String status;
    private String statusName;
}
