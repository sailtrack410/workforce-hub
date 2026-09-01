package com.workforcehub.auth.controller;

import com.workforcehub.auth.dto.BindEmailRequest;
import com.workforcehub.auth.dto.CurrentUserResponse;
import com.workforcehub.auth.dto.LoginRequest;
import com.workforcehub.auth.dto.LoginResponse;
import com.workforcehub.auth.dto.LoginSendCodeRequest;
import com.workforcehub.auth.dto.LoginSendCodeResponse;
import com.workforcehub.auth.dto.ResetPasswordRequest;
import com.workforcehub.auth.dto.SendCodeRequest;
import com.workforcehub.auth.service.AccountService;
import com.workforcehub.auth.service.EmailCodeService;
import com.workforcehub.auth.service.LoginService;
import com.workforcehub.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 员工端认证接口
 */
// 员工认证：登录/注册、邮箱验证码、密码重置、绑定邮箱、当前用户信息
@RestController
@RequestMapping("/api/employee/auth")
public class EmployeeAuthController {

    private final LoginService loginService;
    private final AccountService accountService;
    private final EmailCodeService emailCodeService;

    public EmployeeAuthController(LoginService loginService,
                                  AccountService accountService,
                                  EmailCodeService emailCodeService) {
        this.loginService = loginService;
        this.accountService = accountService;
        this.emailCodeService = emailCodeService;
    }

    @Operation(summary = "员工登录", operationId = "loginEmployee")
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                  HttpServletRequest httpRequest) {
        return R.ok(loginService.loginEmployee(request, httpRequest));
    }

    @Operation(summary = "发送登录验证码", operationId = "sendEmployeeLoginCode")
    @PostMapping("/login/send-code")
    public R<LoginSendCodeResponse> sendLoginCode(@Valid @RequestBody LoginSendCodeRequest request) {
        return R.ok(loginService.sendLoginCode(request.getIdentifier()));
    }

    @Operation(summary = "员工登出", operationId = "logoutEmployee")
    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest httpRequest) {
        loginService.logout(httpRequest);
        return R.ok();
    }

    @Operation(summary = "查询当前员工信息", operationId = "getCurrentEmployee")
    @GetMapping("/me")
    public R<CurrentUserResponse> me() {
        return R.ok(accountService.getCurrentUser());
    }

    @Operation(summary = "发送邮箱验证码", operationId = "sendEmployeeEmailCode")
    @PostMapping("/email/send-code")
    public R<Void> sendCode(@Valid @RequestBody SendCodeRequest request) {
        emailCodeService.sendCode(request.getEmail(), request.getPurpose());
        return R.ok();
    }

    @Operation(summary = "绑定邮箱", operationId = "bindEmployeeEmail")
    @PostMapping("/email/bind")
    public R<Void> bindEmail(@Valid @RequestBody BindEmailRequest request) {
        accountService.bindEmail(request);
        return R.ok();
    }

    @Operation(summary = "重置密码", operationId = "resetEmployeePassword")
    @PostMapping("/password/reset")
    public R<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        accountService.resetPassword(request);
        return R.ok();
    }
}
