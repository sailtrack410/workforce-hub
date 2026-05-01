package com.workforcehub.common.util;

import com.workforcehub.auth.security.WfhUserDetails;
import com.workforcehub.common.enums.UserType;
import com.workforcehub.common.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具类
 * 提供给业务代码方便取当前登录用户信息
 */
public final class CurrentUserHolder {

    private CurrentUserHolder() {}

    /**
     * 获取当前登录用户主体
     * @return WfhUserDetails，未登录返回 null
     */
    public static WfhUserDetails current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof WfhUserDetails user) {
            return user;
        }
        return null;
    }

    /**
     * 获取当前用户ID，未登录抛异常
     */
    public static Long requireUserId() {
        WfhUserDetails user = current();
        if (user == null) {
            throw new BizException(401, "未登录");
        }
        return user.getUserId();
    }

    /**
     * 获取当前用户类型
     */
    public static UserType requireUserType() {
        WfhUserDetails user = current();
        if (user == null) {
            throw new BizException(401, "未登录");
        }
        return user.getUserType();
    }

    /**
     * 是否为员工端用户
     */
    public static boolean isEmployee() {
        WfhUserDetails user = current();
        return user != null && user.getUserType() == UserType.USER;
    }

    /**
     * 是否为后台管理员
     */
    public static boolean isAdmin() {
        WfhUserDetails user = current();
        return user != null && user.getUserType() == UserType.ADMIN;
    }
}
