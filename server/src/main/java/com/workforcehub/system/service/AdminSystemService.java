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
        return adminSystemRepository.listRoles();
    }

    public List<AdminPermissionResponse> listPermissions() {
        return adminSystemRepository.listPermissions();
    }

    public PageResult<AdminAuditLogResponse> pageAuditLogs(AdminAuditLogQuery query) {
        return adminSystemRepository.pageAuditLogs(query);
    }
}
