package com.workforcehub.system.controller;

import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.response.R;
import com.workforcehub.system.service.AdminAlertService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/alerts")
public class AdminAlertController {

    private final AdminAlertService adminAlertService;

    public AdminAlertController(AdminAlertService adminAlertService) {
        this.adminAlertService = adminAlertService;
    }

    @Operation(summary = "查询活跃告警列表", operationId = "listActiveAlerts")
    @GetMapping
    public R<List<Map<String, Object>>> activeAlerts() {
        return R.ok(adminAlertService.listActiveAlerts());
    }

    @Operation(summary = "查询告警渠道列表", operationId = "listAlertChannels")
    @GetMapping("/channels")
    public R<List<Map<String, Object>>> channels() {
        return R.ok(adminAlertService.listChannels());
    }

    @Operation(summary = "创建告警渠道", operationId = "createAlertChannel")
    @PostMapping("/channels")
    public R<Map<String, Object>> createChannel(@RequestBody Map<String, Object> request) {
        return R.ok(adminAlertService.createChannel(request));
    }

    @Operation(summary = "更新告警规则", operationId = "updateAlertRule")
    @PutMapping("/rules/{alertCode}")
    public R<Map<String, Object>> updateRule(@PathVariable String alertCode, @RequestBody Map<String, Object> request) {
        return R.ok(adminAlertService.updateRule(alertCode, request));
    }

    @Operation(summary = "创建告警处理记录", operationId = "createAlertResolution")
    @PostMapping("/{alertCode}/resolutions")
    public R<Map<String, Object>> createResolution(@PathVariable String alertCode, @RequestBody Map<String, Object> request) {
        return R.ok(adminAlertService.createResolution(alertCode, request));
    }

    @Operation(summary = "分页查询告警处理记录", operationId = "pageAlertResolutions")
    @GetMapping("/resolutions")
    public R<PageResult<Map<String, Object>>> resolutions(@RequestParam(required = false) String alertCode,
                                                          @RequestParam(defaultValue = "1") long pageNo,
                                                          @RequestParam(defaultValue = "20") long pageSize) {
        return R.ok(adminAlertService.pageResolutions(alertCode, pageNo, pageSize));
    }
}
