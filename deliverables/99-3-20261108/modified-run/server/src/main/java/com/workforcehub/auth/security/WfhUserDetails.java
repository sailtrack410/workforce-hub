package com.workforcehub.auth.security;

import com.workforcehub.common.enums.UserType;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 统一用户主体接口
 * 业务代码只关心 userType 和 userId，不直接持有 ORM 实体
 */
public interface WfhUserDetails extends UserDetails {

    /**
     * 用户类型：USER（员工）/ ADMIN（管理员）
     */
    UserType getUserType();

    /**
     * 用户类型对应的主键 ID
     * USER：org_user.id
     * ADMIN：admin_user.id
     */
    Long getUserId();

    /**
     * 显示名称
     */
    String getDisplayName();
}
