package com.workforcehub.auth.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工/领导业务主表
 * 对应数据库表 org_user
 */
@Data
@TableName("org_user")
public class OrgUser {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String employeeNo;
    private String realName;
    private String gender;
    private String mobile;
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

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
