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
        appendFilter(where, args, "sal.operator_type", query.getOperatorType());
        appendFilter(where, args, "sal.action_type", query.getActionType());
        appendFilter(where, args, "sal.target_type", query.getTargetType());

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_audit_log sal" + where, Long.class, args.toArray());
        args.add((pageNo - 1) * pageSize);
        args.add(pageSize);
        List<AdminAuditLogResponse> list = jdbcTemplate.query("""
            SELECT sal.id,
                   sal.operator_type,
                   sal.operator_id,
                   COALESCE(ou.real_name, au.display_name) AS operator_name,
                   COALESCE(aua.login_name, au.username) AS operator_account,
                   sal.action_type,
                   sal.target_type,
                   sal.target_id,
                   CASE
                       WHEN sal.target_type = 'WORKFLOW_REQUEST' THEN wr.title
                       WHEN sal.target_type = 'DOWNLOAD_TICKET' THEN ar.display_name
                       WHEN sal.target_type = 'AGENT_TASK' THEN aa.agent_name
                       WHEN sal.target_type = 'AGENT' THEN agent.agent_name
                       WHEN sal.target_type = 'ATTACHMENT_REF' THEN ar.display_name
                       WHEN sal.target_type = 'FILE_OBJECT' THEN fo.file_name
                       WHEN sal.target_type = 'NOTIFY_CHANNEL' THEN ncc.channel_name
                       WHEN sal.target_type = 'LEAVE_TYPE' THEN lt.leave_name
                       WHEN sal.target_type = 'LEAVE_ACCOUNT' THEN CONCAT(la_user.real_name, '的', la_type.leave_name, '账户')
                       WHEN sal.target_type = 'ATTENDANCE_CALENDAR' THEN awc.calendar_name
                       WHEN sal.target_type = 'ATTENDANCE_SHIFT' THEN ash.shift_name
                       WHEN sal.target_type = 'ATTENDANCE_GROUP' THEN ag.group_name
                       WHEN sal.target_type = 'ATTENDANCE_GROUP_MEMBER' THEN CONCAT(agm_user.real_name, '的考勤组成员')
                       WHEN sal.target_type = 'ATTENDANCE_SCHEDULE' THEN CONCAT(as_user.real_name, ' ', DATE_FORMAT(asch.work_date, '%Y-%m-%d'), '排班')
                       WHEN sal.target_type = 'ATTENDANCE_DAILY_RESULT' THEN CONCAT(adr_user.real_name, ' ', DATE_FORMAT(adr.work_date, '%Y-%m-%d'), '日结果')
                       ELSE NULL
                   END AS target_display_name,
                   CASE
                       WHEN sal.target_type = 'WORKFLOW_REQUEST' THEN wr.request_no
                       WHEN sal.target_type = 'DOWNLOAD_TICKET' THEN dt.ticket_no
                       ELSE NULL
                   END AS target_no,
                   sal.result,
                   sal.detail_json,
                   sal.occurred_at
            FROM sys_audit_log sal
            LEFT JOIN org_user ou ON sal.operator_type = 'USER' AND ou.id = sal.operator_id
            LEFT JOIN auth_user_account aua ON sal.operator_type = 'USER' AND aua.user_id = sal.operator_id
            LEFT JOIN admin_user au ON sal.operator_type IN ('ADMIN', 'ADMIN_USER') AND au.id = sal.operator_id
            LEFT JOIN wf_request wr ON sal.target_type = 'WORKFLOW_REQUEST' AND wr.id = sal.target_id
            LEFT JOIN agt_task at ON sal.target_type = 'AGENT_TASK' AND at.id = sal.target_id
            LEFT JOIN agt_agent aa ON aa.id = at.agent_id
            LEFT JOIN agt_agent agent ON sal.target_type = 'AGENT' AND agent.id = sal.target_id
            LEFT JOIN sys_download_ticket dt ON sal.target_type = 'DOWNLOAD_TICKET' AND dt.id = sal.target_id
            LEFT JOIN sys_attachment_ref ar ON (
                (sal.target_type = 'ATTACHMENT_REF' AND ar.id = sal.target_id)
                OR (sal.target_type = 'DOWNLOAD_TICKET' AND ar.id = dt.attachment_ref_id)
            )
            LEFT JOIN sys_file_object fo ON sal.target_type = 'FILE_OBJECT' AND fo.id = sal.target_id
            LEFT JOIN notify_channel_config ncc ON sal.target_type = 'NOTIFY_CHANNEL' AND ncc.id = sal.target_id
            LEFT JOIN lv_type lt ON sal.target_type = 'LEAVE_TYPE' AND lt.id = sal.target_id
            LEFT JOIN lv_account la ON sal.target_type = 'LEAVE_ACCOUNT' AND la.id = sal.target_id
            LEFT JOIN org_user la_user ON la_user.id = la.user_id
            LEFT JOIN lv_type la_type ON la_type.id = la.leave_type_id
            LEFT JOIN att_work_calendar awc ON sal.target_type = 'ATTENDANCE_CALENDAR' AND awc.id = sal.target_id
            LEFT JOIN att_shift ash ON sal.target_type = 'ATTENDANCE_SHIFT' AND ash.id = sal.target_id
            LEFT JOIN att_group ag ON sal.target_type = 'ATTENDANCE_GROUP' AND ag.id = sal.target_id
            LEFT JOIN att_group_member agm ON sal.target_type = 'ATTENDANCE_GROUP_MEMBER' AND agm.id = sal.target_id
            LEFT JOIN org_user agm_user ON agm_user.id = agm.user_id
            LEFT JOIN att_schedule asch ON sal.target_type = 'ATTENDANCE_SCHEDULE' AND asch.id = sal.target_id
            LEFT JOIN org_user as_user ON as_user.id = asch.user_id
            LEFT JOIN att_daily_result adr ON sal.target_type = 'ATTENDANCE_DAILY_RESULT' AND adr.id = sal.target_id
            LEFT JOIN org_user adr_user ON adr_user.id = adr.user_id
            """ + where + """
            ORDER BY sal.occurred_at DESC, sal.id DESC
            LIMIT ?, ?
            """,
            (rs, rowNum) -> {
                AdminAuditLogResponse response = new AdminAuditLogResponse();
                response.setAuditLogId(rs.getLong("id"));
                response.setOperatorType(rs.getString("operator_type"));
                response.setOperatorTypeName(operatorTypeName(rs.getString("operator_type")));
                response.setOperatorId(rs.getObject("operator_id", Long.class));
                response.setOperatorName(rs.getString("operator_name"));
                response.setOperatorAccount(rs.getString("operator_account"));
                response.setActionType(rs.getString("action_type"));
                response.setActionTypeName(actionTypeName(rs.getString("action_type")));
                response.setTargetType(rs.getString("target_type"));
                response.setTargetTypeName(targetTypeName(rs.getString("target_type")));
                response.setTargetId(rs.getObject("target_id", Long.class));
                response.setTargetDisplayName(rs.getString("target_display_name"));
                response.setTargetNo(rs.getString("target_no"));
                response.setResult(rs.getString("result"));
                response.setResultName(resultName(rs.getString("result")));
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

    private String operatorTypeName(String value) {
        if ("USER".equals(value)) return "员工";
        if ("ADMIN".equals(value) || "ADMIN_USER".equals(value)) return "管理员";
        if ("SYSTEM".equals(value)) return "系统";
        if ("AGENT".equals(value)) return "AI 助手";
        return value == null ? null : "系统来源";
    }

    private String actionTypeName(String value) {
        if ("FILE_DOWNLOAD_TICKET_CREATED".equals(value)) return "创建下载凭证";
        if ("FILE_DOWNLOAD_EXECUTED".equals(value)) return "下载文件";
        if ("FILE_ADMIN_DOWNLOAD_TICKET_CREATED".equals(value)) return "管理员创建下载凭证";
        if ("FILE_ADMIN_DOWNLOAD_EXECUTED".equals(value)) return "管理员下载文件";
        if ("WORKFLOW_ATTACHMENT_DOWNLOAD_TICKET_CREATED".equals(value)) return "创建审批附件下载凭证";
        if ("WORKFLOW_ATTACHMENT_DOWNLOAD_EXECUTED".equals(value)) return "下载审批附件";
        if ("NOTIFY_CHANNEL_ENABLED".equals(value)) return "启用通知渠道";
        if ("NOTIFY_CHANNEL_DISABLED".equals(value)) return "停用通知渠道";
        if ("NOTIFY_CHANNEL_UPDATED".equals(value)) return "修改通知渠道";
        if ("NOTIFY_CHANNEL_TEST".equals(value)) return "测试通知渠道";
        if ("TASK_CREATED".equals(value)) return "创建 AI 任务";
        if ("TASK_SUCCEEDED".equals(value)) return "AI 任务完成";
        if ("TASK_FAILED".equals(value)) return "AI 任务失败";
        if ("PLUGIN_ENABLED".equals(value)) return "启用插件";
        if ("PLUGIN_DISABLED".equals(value)) return "停用插件";
        if ("PLUGIN_UNINSTALLED".equals(value)) return "卸载插件";
        if ("APPROVE".equals(value)) return "审批通过";
        if ("REJECT".equals(value)) return "审批驳回";
        if ("SUBMIT".equals(value)) return "提交申请";
        if ("REVOKE".equals(value)) return "撤回申请";
        return value == null ? null : "业务操作";
    }

    private String targetTypeName(String value) {
        if ("DOWNLOAD_TICKET".equals(value)) return "下载凭证";
        if ("UPLOAD_TICKET".equals(value)) return "上传凭证";
        if ("ATTACHMENT_REF".equals(value) || "FILE_OBJECT".equals(value)) return "文件";
        if ("WORKFLOW_REQUEST".equals(value)) return "审批申请";
        if ("WORKFLOW_ATTACHMENT".equals(value)) return "审批附件";
        if ("AGENT_TASK".equals(value)) return "AI 任务";
        if ("AGENT".equals(value)) return "AI 助手";
        if ("NOTIFY_CHANNEL".equals(value)) return "通知渠道";
        if ("LEAVE_TYPE".equals(value)) return "假种";
        if ("LEAVE_ACCOUNT".equals(value)) return "假期账户";
        if ("ATTENDANCE_CALENDAR".equals(value)) return "工作日历";
        if ("ATTENDANCE_SHIFT".equals(value)) return "班次";
        if ("ATTENDANCE_GROUP".equals(value)) return "考勤组";
        if ("ATTENDANCE_GROUP_MEMBER".equals(value)) return "考勤组成员";
        if ("ATTENDANCE_SCHEDULE".equals(value)) return "排班";
        if ("ATTENDANCE_DAILY_RESULT".equals(value)) return "考勤日结果";
        if ("OPS_ALERT".equals(value)) return "系统告警";
        if ("PLUGIN".equals(value)) return "插件";
        return value == null ? null : "业务对象";
    }

    private String resultName(String value) {
        if ("SUCCESS".equals(value) || "SUCCEEDED".equals(value)) return "成功";
        if ("FAIL".equals(value) || "FAILED".equals(value)) return "失败";
        if ("BLOCKED".equals(value)) return "已阻止";
        return value == null ? null : "未知结果";
    }
}
