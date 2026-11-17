package com.workforcehub.common.enums;

/**
 * 登录用户类型
 * 用于区分员工端用户和后台管理员
 */
public enum UserType {

    /**
     * 业务用户（员工 / 领导）
     */
    USER,

    /**
     * 后台管理员
     */
    ADMIN
}
