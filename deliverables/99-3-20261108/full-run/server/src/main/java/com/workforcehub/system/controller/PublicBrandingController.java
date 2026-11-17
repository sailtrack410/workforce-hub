package com.workforcehub.system.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.system.service.BrandingService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开的品牌化配置接口（无需登录），供登录页等未登录场景读取
 */
@RestController
@RequestMapping("/api/public/branding")
public class PublicBrandingController {

    private final BrandingService brandingService;

    public PublicBrandingController(BrandingService brandingService) {
        this.brandingService = brandingService;
    }

    public record BrandingResponse(List<String> taglines) {
    }

    @Operation(summary = "登录页品牌标语", operationId = "getPublicBranding")
    @GetMapping
    public R<BrandingResponse> branding() {
        return R.ok(new BrandingResponse(brandingService.loginTaglines()));
    }
}
