package com.workforcehub.auth.controller;

import com.workforcehub.auth.dto.CurrentUserResponse;
import com.workforcehub.auth.dto.LoginRequest;
import com.workforcehub.auth.dto.LoginResponse;
import com.workforcehub.auth.service.AccountService;
import com.workforcehub.auth.service.LoginService;
import com.workforcehub.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 后台管理员认证接口
 */
// 管理员认证：登录、当前用户信息
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final LoginService loginService;
    private final AccountService accountService;

    public AdminAuthController(LoginService loginService, AccountService accountService) {
        this.loginService = loginService;
        this.accountService = accountService;
    }

    @Operation(summary = "管理员登录", operationId = "loginAdmin")
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                  HttpServletRequest httpRequest) {
        return R.ok(loginService.loginAdmin(request, httpRequest));
    }

    @Operation(summary = "管理员登出", operationId = "logoutAdmin")
    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest httpRequest) {
        loginService.logout(httpRequest);
        return R.ok();
    }

    @Operation(summary = "查询当前管理员信息", operationId = "getCurrentAdmin")
    @GetMapping("/me")
    public R<CurrentUserResponse> me() {
        return R.ok(accountService.getCurrentUser());
    }
}
