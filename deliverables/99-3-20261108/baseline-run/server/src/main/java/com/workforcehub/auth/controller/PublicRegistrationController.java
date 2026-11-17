package com.workforcehub.auth.controller;

import com.workforcehub.auth.service.EmployeeRegistrationService;
import com.workforcehub.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 员工自助注册接口（接口需求表 #12，匿名可访问）。
 */
@Tag(name = "员工注册(公开)")
@RestController
@RequestMapping("/api/public/auth")
@Validated
public class PublicRegistrationController {

    private final EmployeeRegistrationService registrationService;

    public PublicRegistrationController(EmployeeRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @Operation(summary = "发送注册短信验证码", operationId = "sendRegisterSmsCode")
    @PostMapping("/register/send-sms-code")
    public R<Map<String, Object>> sendSmsCode(@Valid @RequestBody SendRegisterSmsCodeRequest request) {
        return R.ok(registrationService.sendRegisterSmsCode(request.getPhone()));
    }

    @Operation(summary = "员工自助注册", operationId = "registerEmployee")
    @PostMapping("/register")
    public R<Map<String, Object>> register(@Valid @RequestBody RegisterEmployeeRequest request) {
        return R.ok(registrationService.register(
            request.getRealName(),
            request.getPhone(),
            request.getSmsCode(),
            request.getPassword(),
            request.getInviteCode()
        ));
    }

    @Data
    public static class SendRegisterSmsCodeRequest {
        @Schema(description = "手机号", example = "13800138000")
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "1\\d{10}", message = "手机号格式不正确")
        private String phone;
    }

    @Data
    public static class RegisterEmployeeRequest {
        @Schema(description = "真实姓名", example = "王五")
        @NotBlank(message = "姓名不能为空")
        private String realName;
        @Schema(description = "手机号", example = "13800138000")
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "1\\d{10}", message = "手机号格式不正确")
        private String phone;
        @Schema(description = "短信验证码", example = "123456")
        @NotBlank(message = "验证码不能为空")
        private String smsCode;
        @Schema(description = "密码(8位以上含字母和数字)", example = "Abcd1234")
        @NotBlank(message = "密码不能为空")
        private String password;
        @Schema(description = "企业邀请码", example = "INV2026X9K2")
        @NotBlank(message = "邀请码不能为空")
        private String inviteCode;
    }
}
