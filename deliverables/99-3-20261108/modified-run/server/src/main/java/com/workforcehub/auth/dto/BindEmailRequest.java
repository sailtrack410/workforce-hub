package com.workforcehub.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BindEmailRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank(message = "验证码不能为空")
    private String code;
}
