package com.workforcehub.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendCodeRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 用途：BIND（绑定邮箱）/ RESET_PASSWORD（找回密码）
     */
    @NotBlank(message = "用途不能为空")
    private String purpose;
}
