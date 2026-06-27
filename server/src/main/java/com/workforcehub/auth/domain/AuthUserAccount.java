package com.workforcehub.auth.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务用户登录账号
 * 对应数据库表 auth_user_account
 * 员工与领导共用此账号体系
 */
@Data
public class AuthUserAccount {

    private Long id;

    /**
     * 关联 org_user.id
     */
    private Long userId;

    private String loginName;
    private String passwordHash;
    private String passwordSalt;
    private String email;
    private LocalDateTime emailVerifiedAt;

    /**
     * 账号状态：ACTIVE/LOCKED/DISABLED
     */
    private String accountStatus;

    private LocalDateTime lastLoginAt;
    private LocalDateTime passwordUpdatedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;

    private Integer isDeleted;
}
