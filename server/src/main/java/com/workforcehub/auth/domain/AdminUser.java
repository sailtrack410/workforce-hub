package com.workforcehub.auth.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台管理员账号
 * 对应数据库表 admin_user
 * 独立于业务用户体系
 */
@Data
@TableName("admin_user")
public class AdminUser {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;
    private String passwordHash;
    private String passwordSalt;
    private String displayName;
    private String email;

    /**
     * 状态：ACTIVE/LOCKED/DISABLED
     */
    private String status;

    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
