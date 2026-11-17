package com.workforcehub.system.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.system.dto.SysNotificationResponse;
import com.workforcehub.system.service.SysNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 员工站内通知接口（接口需求表 #10）。
 */
@Tag(name = "员工通知")
@RestController
@RequestMapping("/api/employee/notifications")
public class EmployeeNotificationController {

    private final SysNotificationService notificationService;

    public EmployeeNotificationController(SysNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "分页查询我的通知", operationId = "listMyNotifications")
    @GetMapping
    public R<List<SysNotificationResponse>> list(
        @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
        @RequestParam(name = "pageSize", defaultValue = "20") long pageSize
    ) {
        return R.ok(notificationService.pageMyNotifications(pageNo, pageSize));
    }

    @Operation(summary = "查询我的未读通知数", operationId = "countMyUnreadNotifications")
    @GetMapping("/unread-count")
    public R<Map<String, Long>> unreadCount() {
        return R.ok(Map.of("unreadCount", notificationService.unreadCount()));
    }

    @Operation(summary = "标记通知已读", operationId = "markMyNotificationRead")
    @PostMapping("/{notificationId}/read")
    public R<Void> markRead(@PathVariable String notificationId) {
        notificationService.markRead(notificationId);
        return R.ok(null);
    }

    @Operation(summary = "全部标记已读", operationId = "markAllMyNotificationsRead")
    @PostMapping("/read-all")
    public R<Void> markAllRead() {
        notificationService.markAllRead();
        return R.ok(null);
    }
}
