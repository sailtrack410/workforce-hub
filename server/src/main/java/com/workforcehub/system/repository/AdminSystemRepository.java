package com.workforcehub.system.repository;

import com.workforcehub.common.response.PageResult;
import com.workforcehub.system.dto.AdminAuditLogQuery;
import com.workforcehub.system.dto.AdminAuditLogResponse;
import com.workforcehub.system.dto.AdminPermissionResponse;
import com.workforcehub.system.dto.AdminRoleResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 后台系统治理查询仓储。
 */
@Repository
public class AdminSystemRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminSystemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AdminRoleResponse> listRoles() {
        return jdbcTemplate.query("""
            SELECT id, role_code, role_name, status, created_at, updated_at
            FROM admin_role
            WHERE is_deleted = 0
            ORDER BY role_code ASC, id ASC
            """,
            (rs, rowNum) -> {
                AdminRoleResponse response = new AdminRoleResponse();
                response.setRoleId(rs.getLong("id"));
                response.setRoleCode(rs.getString("role_code"));
                response.setRoleName(rs.getString("role_name"));
                response.setStatus(rs.getString("status"));
                response.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                response.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                return response;
            });
    }

    public List<AdminPermissionResponse> listPermissions() {
        return jdbcTemplate.query("""
            SELECT id, perm_code, perm_name, module_code, action_code, status, created_at, updated_at
            FROM admin_permission
            WHERE is_deleted = 0
            ORDER BY module_code ASC, perm_code ASC, id ASC
            """,
            (rs, rowNum) -> {
                AdminPermissionResponse response = new AdminPermissionResponse();
                response.setPermissionId(rs.getLong("id"));
                response.setPermCode(rs.getString("perm_code"));
                response.setPermName(rs.getString("perm_name"));
                response.setModuleCode(rs.getString("module_code"));
                response.setActionCode(rs.getString("action_code"));
                response.setStatus(rs.getString("status"));
                response.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                response.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                return response;
            });
    }

    public PageResult<AdminAuditLogResponse> pageAuditLogs(AdminAuditLogQuery query) {
        long pageNo = Math.max(query.getPageNo() == null ? 1L : query.getPageNo(), 1L);
        long pageSize = Math.min(Math.max(query.getPageSize() == null ? 20L : query.getPageSize(), 1L), 100L);
        ArrayList<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1\n");
        appendFilter(where, args, "operator_type", query.getOperatorType());
        appendFilter(where, args, "action_type", query.getActionType());
        appendFilter(where, args, "target_type", query.getTargetType());

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_audit_log" + where, Long.class, args.toArray());
        args.add((pageNo - 1) * pageSize);
        args.add(pageSize);
        List<AdminAuditLogResponse> list = jdbcTemplate.query("""
            SELECT id, operator_type, operator_id, action_type, target_type, target_id, result, detail_json, occurred_at
            FROM sys_audit_log
            """ + where + """
            ORDER BY occurred_at DESC, id DESC
            LIMIT ?, ?
            """,
            (rs, rowNum) -> {
                AdminAuditLogResponse response = new AdminAuditLogResponse();
                response.setAuditLogId(rs.getLong("id"));
                response.setOperatorType(rs.getString("operator_type"));
                response.setOperatorId(rs.getObject("operator_id", Long.class));
                response.setActionType(rs.getString("action_type"));
                response.setTargetType(rs.getString("target_type"));
                response.setTargetId(rs.getObject("target_id", Long.class));
                response.setResult(rs.getString("result"));
                response.setDetailJson(rs.getString("detail_json"));
                response.setOccurredAt(rs.getTimestamp("occurred_at").toLocalDateTime());
                return response;
            },
            args.toArray());
        return PageResult.of(pageNo, pageSize, total == null ? 0L : total, list);
    }

    private void appendFilter(StringBuilder where, List<Object> args, String column, String value) {
        if (!StringUtils.hasText(value)) return;
        where.append(" AND ").append(column).append(" = ?\n");
        args.add(value.trim());
    }
}
