package com.workforcehub.org.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 组织部门
 */
@Data
@TableName("org_department")
public class OrgDepartment {

    @TableId(type = IdType.ASSIGN_ID)
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

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
