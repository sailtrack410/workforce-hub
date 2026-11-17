package com.workforcehub.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 员工端登录验证码发送请求。
 * identifier 可为登录名或绑定邮箱。
 */
@Data
public class LoginSendCodeRequest {

    @NotBlank(message = "登录标识不能为空")
    private String identifier;
}
