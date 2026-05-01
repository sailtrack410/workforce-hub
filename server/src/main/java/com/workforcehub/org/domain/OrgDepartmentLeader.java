package com.workforcehub.org.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部门领导成员
 */
@Data
@TableName("org_department_leader")
public class OrgDepartmentLeader {

    @TableId(type = IdType.ASSIGN_ID)
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
