package com.workforcehub.system.controller;

import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.response.R;
import com.workforcehub.system.dto.AdminAuditLogQuery;
import com.workforcehub.system.dto.AdminAuditLogResponse;
import com.workforcehub.system.dto.AdminPermissionResponse;
import com.workforcehub.system.dto.AdminRoleResponse;
import com.workforcehub.system.service.AdminSystemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台系统治理接口。
 */
@RestController
@RequestMapping("/api/admin/system")
public class AdminSystemController {

    private final AdminSystemService adminSystemService;

    public AdminSystemController(AdminSystemService adminSystemService) {
        this.adminSystemService = adminSystemService;
    }

    @GetMapping("/roles")
    public R<List<AdminRoleResponse>> roles() {
        return R.ok(adminSystemService.listRoles());
    }

    @GetMapping("/permissions")
    public R<List<AdminPermissionResponse>> permissions() {
        return R.ok(adminSystemService.listPermissions());
    }

    @GetMapping("/audit-logs")
    public R<PageResult<AdminAuditLogResponse>> auditLogs(@ModelAttribute AdminAuditLogQuery query) {
        return R.ok(adminSystemService.pageAuditLogs(query));
    }
}
