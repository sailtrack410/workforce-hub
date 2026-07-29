package com.workforcehub.plugin.controller.admin;

import com.workforcehub.common.response.R;
import com.workforcehub.plugin.dto.PluginConfigResponse;
import com.workforcehub.plugin.dto.PluginConfigSaveRequest;
import com.workforcehub.plugin.runtime.PluginConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/plugins/{pluginId}/config")
public class AdminPluginConfigController {

    private final PluginConfigService service;

    public AdminPluginConfigController(PluginConfigService service) {
        this.service = service;
    }

    @GetMapping
    public R<List<PluginConfigResponse>> list(@PathVariable String pluginId) {
        return R.ok(service.list(pluginId));
    }

    @PutMapping("/{configKey}")
    public R<PluginConfigResponse> save(
            @PathVariable String pluginId,
            @PathVariable String configKey,
            @Valid @RequestBody PluginConfigSaveRequest request) {
        return R.ok(service.save(pluginId, configKey, request));
    }

    @DeleteMapping("/{configKey}")
    public R<Long> delete(
            @PathVariable String pluginId,
            @PathVariable String configKey,
            @RequestParam(required = false) Long expectedVersion) {
        return R.ok(service.delete(pluginId, configKey, expectedVersion));
    }
}
