package com.workforcehub.auth.dto;

import com.workforcehub.common.enums.UserType;
import lombok.Data;

@Data
public class LoginResponse {

    private Long userId;
    private UserType userType;
    private String displayName;
    private String loginName;

    /**
     * 是否需要邮箱二次验证。仅员工端可能为 true：账号已绑定邮箱且 emailCode 缺失时返回 true。
     * 返回 true 时不签发会话，前端应跳转到验证码输入步骤。
     */
    private boolean requiresEmailCode;

    /**
     * 用于二验提示的脱敏邮箱（如 j***n@e**.com）。仅当 requiresEmailCode=true 时有值。
     */
    private String maskedEmail;

    /**
     * 是否需要先绑定邮箱。仅员工端可能为 true：账号未绑定邮箱且未提交 bindEmail/emailCode 时返回 true。
     * 返回 true 时不签发会话，前端应展示邮箱输入 + 验证码步骤。
     */
    private boolean requiresEmailBinding;
}
