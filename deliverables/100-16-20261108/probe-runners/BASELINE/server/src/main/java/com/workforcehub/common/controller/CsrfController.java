package com.workforcehub.common.controller;

import com.workforcehub.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class CsrfController {

    @Operation(summary = "获取 CSRF Token", operationId = "getCsrfToken")
    @GetMapping("/api/csrf")
    public R<Map<String, String>> csrf(CsrfToken token, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return R.ok(Map.of("headerName", token.getHeaderName(), "token", token.getToken()));
    }
}
