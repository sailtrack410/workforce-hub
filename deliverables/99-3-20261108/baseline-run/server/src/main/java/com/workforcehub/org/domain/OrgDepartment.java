package com.workforcehub.org.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 组织部门
 */
@Data
public class OrgDepartment {

    private Long id;

    private Long parentId;
    private String treePath;
    private String deptCode;
    private String deptName;
    private String deptType;
    private Integer deptLevel;
    private Long leaderUserId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;

    private Integer isDeleted;
}
