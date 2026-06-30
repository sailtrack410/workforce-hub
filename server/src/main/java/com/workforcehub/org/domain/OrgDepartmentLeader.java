package com.workforcehub.org.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部门领导成员
 */
@Data
public class OrgDepartmentLeader {

    private Long id;

    private Long deptId;
    private Long userId;
    private String leaderType;
    private Integer isPrimary;
    private Integer sortOrder;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
