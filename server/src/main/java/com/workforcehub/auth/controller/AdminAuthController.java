package com.workforcehub.auth.controller;

import com.workforcehub.auth.dto.CurrentUserResponse;
import com.workforcehub.auth.dto.LoginRequest;
import com.workforcehub.auth.dto.LoginResponse;
import com.workforcehub.auth.service.AccountService;
import com.workforcehub.auth.service.LoginService;
import com.workforcehub.common.response.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 后台管理员认证接口
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final LoginService loginService;
    private final AccountService accountService;

    public AdminAuthController(LoginService loginService, AccountService accountService) {
        this.loginService = loginService;
        this.accountService = accountService;
    }

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                  HttpServletRequest httpRequest) {
        return R.ok(loginService.loginAdmin(request, httpRequest));
    }

    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest httpRequest) {
        loginService.logout(httpRequest);
        return R.ok();
    }

    @GetMapping("/me")
    public R<CurrentUserResponse> me() {
        return R.ok(accountService.getCurrentUser());
    }
}
