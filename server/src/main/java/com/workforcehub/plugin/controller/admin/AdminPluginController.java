package com.workforcehub.plugin.controller.admin;

import com.workforcehub.common.response.R;
import com.workforcehub.plugin.dto.AdminPluginResponse;
import com.workforcehub.plugin.runtime.AdminPluginManagementService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/plugins")
public class AdminPluginController {

    private final AdminPluginManagementService service;

    public AdminPluginController(AdminPluginManagementService service) {
        this.service = service;
    }

    @GetMapping
    public R<List<AdminPluginResponse>> list() {
        return R.ok(service.list());
    }

    @PutMapping("/{pluginId}/enable")
    public R<AdminPluginResponse> enable(@PathVariable String pluginId) {
        return R.ok(service.enable(pluginId));
    }

    @PutMapping("/{pluginId}/disable")
    public R<AdminPluginResponse> disable(@PathVariable String pluginId) {
        return R.ok(service.disable(pluginId));
    }

    @DeleteMapping("/{pluginId}")
    public R<AdminPluginResponse> uninstall(@PathVariable String pluginId) {
        return R.ok(service.uninstall(pluginId));
    }
}
