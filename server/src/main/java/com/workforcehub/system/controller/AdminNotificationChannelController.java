package com.workforcehub.system.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.system.dto.NotificationChannelResponse;
import com.workforcehub.system.dto.NotificationChannelSaveRequest;
import com.workforcehub.system.dto.NotificationChannelTestRequest;
import com.workforcehub.system.dto.NotificationChannelTestResponse;
import com.workforcehub.system.service.NotificationChannelService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/notification-channels")
public class AdminNotificationChannelController {

    private final NotificationChannelService service;

    public AdminNotificationChannelController(NotificationChannelService service) {
        this.service = service;
    }

    @Operation(summary = "查询通知渠道列表", operationId = "listNotificationChannels")
    @GetMapping
    public R<List<NotificationChannelResponse>> listChannels() {
        return R.ok(service.listChannels());
    }

    @Operation(summary = "查询通知渠道详情", operationId = "getNotificationChannel")
    @GetMapping("/{channelCode}")
    public R<NotificationChannelResponse> getChannel(@PathVariable String channelCode) {
        return R.ok(service.getChannel(channelCode));
    }

    @Operation(summary = "更新通知渠道", operationId = "updateNotificationChannel")
    @PutMapping("/{channelCode}")
    public R<NotificationChannelResponse> updateChannel(@PathVariable String channelCode,
                                                        @Valid @RequestBody NotificationChannelSaveRequest request) {
        return R.ok(service.updateChannel(channelCode, request));
    }

    @Operation(summary = "测试通知渠道", operationId = "testNotificationChannel")
    @PostMapping("/{channelCode}/test")
    public R<NotificationChannelTestResponse> testChannel(@PathVariable String channelCode,
                                                          @Valid @RequestBody NotificationChannelTestRequest request) {
        return R.ok(service.testChannel(channelCode, request));
    }
}
