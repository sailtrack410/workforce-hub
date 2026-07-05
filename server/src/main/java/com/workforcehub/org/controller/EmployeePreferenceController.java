package com.workforcehub.org.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.org.dto.EmployeePreferenceResponse;
import com.workforcehub.org.dto.UpdateEmployeePreferenceRequest;
import com.workforcehub.org.service.EmployeePreferenceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工系统偏好（云端持久化）接口
 */
// 员工偏好接口：主题/密度/通知等个性化设置的读取和更新
@RestController
@RequestMapping("/api/employee/me/preferences")
public class EmployeePreferenceController {

    private final EmployeePreferenceService preferenceService;

    public EmployeePreferenceController(EmployeePreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public R<EmployeePreferenceResponse> getPreferences() {
        return R.ok(preferenceService.getCurrent());
    }

    @PutMapping
    public R<EmployeePreferenceResponse> updatePreferences(@Valid @RequestBody UpdateEmployeePreferenceRequest request) {
        return R.ok(preferenceService.updateCurrent(request));
    }
}
