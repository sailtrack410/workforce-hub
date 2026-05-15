package com.workforcehub.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    /**
     * 登录标识：员工端可为登录名或绑定邮箱；管理端仅登录名。
     */
    @NotBlank(message = "登录名不能为空")
    private String loginName;

    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 邮箱二次验证码（员工端，当账号已绑定邮箱时必填）。
     * 账号未绑定邮箱时，配合 bindEmail 用于首次绑定并完成登录。
     */
    private String emailCode;

    /**
     * 员工端首次登录绑定邮箱。仅账号未绑定邮箱时使用。
     */
    @Email(message = "邮箱格式不正确")
    private String bindEmail;
}
