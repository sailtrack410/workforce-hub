package com.workforcehub.system.service;

import com.workforcehub.common.response.PageResult;
import com.workforcehub.system.dto.AdminAuditLogQuery;
import com.workforcehub.system.dto.AdminAuditLogResponse;
import com.workforcehub.system.dto.AdminPermissionResponse;
import com.workforcehub.system.dto.AdminRoleResponse;
import com.workforcehub.system.repository.AdminSystemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 后台系统治理查询服务。
 */
// 系统治理查询：审计日志分页、角色列表、权限列表
@Service
public class AdminSystemService {

    private final AdminSystemRepository adminSystemRepository;

    public AdminSystemService(AdminSystemRepository adminSystemRepository) {
        this.adminSystemRepository = adminSystemRepository;
    }

    public List<AdminRoleResponse> listRoles() {
        return adminSystemRepository.listRoles().stream()
            .peek(item -> item.setStatusName(statusName(item.getStatus())))
            .toList();
    }

    public List<AdminPermissionResponse> listPermissions() {
        return adminSystemRepository.listPermissions().stream()
            .peek(item -> {
                item.setModuleName(moduleName(item.getModuleCode()));
                item.setActionName(actionName(item.getActionCode()));
                item.setStatusName(statusName(item.getStatus()));
            })
            .toList();
    }

    public PageResult<AdminAuditLogResponse> pageAuditLogs(AdminAuditLogQuery query) {
        return adminSystemRepository.pageAuditLogs(query);
    }

    private String statusName(String value) {
        if ("ACTIVE".equals(value)) return "启用";
        if ("INACTIVE".equals(value)) return "停用";
        return value == null ? null : "其他状态";
    }

    private String moduleName(String value) {
        if ("ORG".equals(value)) return "组织管理";
        if ("WORKFLOW".equals(value)) return "审批管理";
        if ("ATTENDANCE".equals(value)) return "考勤管理";
        if ("LEAVE".equals(value)) return "假期管理";
        if ("AGENT".equals(value)) return "AI 助手";
        if ("SYSTEM".equals(value)) return "系统治理";
        if ("FILE".equals(value)) return "文件管理";
        return value == null ? null : "业务模块";
    }

    private String actionName(String value) {
        if ("VIEW".equals(value)) return "查看";
        if ("MANAGE".equals(value)) return "管理";
        if ("AUDIT".equals(value)) return "审计";
        if ("CONFIG".equals(value)) return "配置";
        if ("APPROVE".equals(value)) return "审批";
        return value == null ? null : "业务操作";
    }
}
