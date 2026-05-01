package com.workforcehub.org.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 岗位定义
 */
@Data
@TableName("org_position")
public class OrgPosition {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String positionCode;
    private String positionName;
    private String positionCategory;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
