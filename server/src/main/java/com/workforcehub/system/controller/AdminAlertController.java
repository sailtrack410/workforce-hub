package com.workforcehub.system.controller;

import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.response.R;
import com.workforcehub.system.service.AdminAlertService;
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

    @GetMapping
    public R<List<Map<String, Object>>> activeAlerts() {
        return R.ok(adminAlertService.listActiveAlerts());
    }

    @GetMapping("/channels")
    public R<List<Map<String, Object>>> channels() {
        return R.ok(adminAlertService.listChannels());
    }

    @PostMapping("/channels")
    public R<Map<String, Object>> createChannel(@RequestBody Map<String, Object> request) {
        return R.ok(adminAlertService.createChannel(request));
    }

    @PutMapping("/rules/{alertCode}")
    public R<Map<String, Object>> updateRule(@PathVariable String alertCode, @RequestBody Map<String, Object> request) {
        return R.ok(adminAlertService.updateRule(alertCode, request));
    }

    @PostMapping("/{alertCode}/resolutions")
    public R<Map<String, Object>> createResolution(@PathVariable String alertCode, @RequestBody Map<String, Object> request) {
        return R.ok(adminAlertService.createResolution(alertCode, request));
    }

    @GetMapping("/resolutions")
    public R<PageResult<Map<String, Object>>> resolutions(@RequestParam(required = false) String alertCode,
                                                          @RequestParam(defaultValue = "1") long pageNo,
                                                          @RequestParam(defaultValue = "20") long pageSize) {
        return R.ok(adminAlertService.pageResolutions(alertCode, pageNo, pageSize));
    }
}
