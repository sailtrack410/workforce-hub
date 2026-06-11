package com.workforcehub.workflow.repository;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.workflow.dto.AdminWorkflowRequestQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * module-workflow 后台流程模板治理 JDBC 仓储。
 */
@Repository
public class WorkflowAdminRepository {

    private final JdbcTemplate jdbcTemplate;

    public WorkflowAdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsProcessCode(String processCode) {
        return exists("""
            SELECT COUNT(*)
            FROM wf_process_type
            WHERE process_code = ? AND is_deleted = 0
            """, processCode);
    }

    public boolean existsTemplateCode(String templateCode) {
        return exists("""
            SELECT COUNT(*)
            FROM wf_template
            WHERE template_code = ? AND is_deleted = 0
            """, templateCode);
    }

    public boolean existsTemplateVersion(Long templateId, Integer versionNo) {
        return exists("""
            SELECT COUNT(*)
            FROM wf_template_version
            WHERE template_id = ? AND version_no = ?
            """, templateId, versionNo);
    }

    public boolean existsActiveProcessType(Long processTypeId) {
        return exists("""
            SELECT COUNT(*)
            FROM wf_process_type
            WHERE id = ? AND status = 'ACTIVE' AND is_deleted = 0
            """, processTypeId);
    }

    public boolean existsActiveTemplate(Long templateId) {
        return exists("""
            SELECT COUNT(*)
            FROM wf_template
            WHERE id = ? AND status = 'ACTIVE' AND is_deleted = 0
            """, templateId);
    }

    public void insertProcessType(Long processTypeId,
                                  String processCode,
                                  String processName,
                                  String bizType,
                                  String status,
                                  Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO wf_process_type
                (id, process_code, process_name, biz_type, status, created_by, updated_by)
            VALUES
                (?, ?, ?, ?, ?, ?, ?)
            """,
            processTypeId,
            processCode,
            processName,
            bizType,
            status,
            operatorId,
            operatorId
        );
    }

    public void insertTemplate(Long templateId,
                               Long processTypeId,
                               String templateCode,
                               String templateName,
                               String status,
                               Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO wf_template
                (id, process_type_id, template_code, template_name, status, created_by, updated_by)
            VALUES
                (?, ?, ?, ?, ?, ?, ?)
            """,
            templateId,
            processTypeId,
            templateCode,
            templateName,
            status,
            operatorId,
            operatorId
        );
    }

    public void insertTemplateVersion(Long versionId,
                                      Long templateId,
                                      Integer versionNo,
                                      String formSchemaJson,
                                      String ruleSnapshotJson,
                                      Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO wf_template_version
                (id, template_id, version_no, form_schema_json, rule_snapshot_json,
                 status, created_by, updated_by)
            VALUES
                (?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), 'DRAFT', ?, ?)
            """,
            versionId,
            templateId,
            versionNo,
            formSchemaJson,
            ruleSnapshotJson,
            operatorId,
            operatorId
        );
    }

    public void archiveCurrentPublishedVersions(Long templateId, Long excludedVersionId, Long operatorId) {
        jdbcTemplate.update("""
            UPDATE wf_template_version
            SET status = 'ARCHIVED',
                effective_to = NOW(),
                updated_by = ?
            WHERE template_id = ?
              AND id <> ?
              AND status = 'PUBLISHED'
              AND effective_from <= NOW()
              AND (effective_to IS NULL OR effective_to > NOW())
            """, operatorId, templateId, excludedVersionId);
    }

    public void publishTemplateVersion(Long versionId,
                                       String flowableDefinitionKey,
                                       String flowableDefinitionId,
                                       Long operatorId) {
        jdbcTemplate.update("""
            UPDATE wf_template_version
            SET flowable_definition_key = ?,
                flowable_definition_id = ?,
                status = 'PUBLISHED',
                published_by = ?,
                published_at = NOW(),
                effective_from = NOW(),
                effective_to = NULL,
                updated_by = ?
            WHERE id = ?
            """,
            flowableDefinitionKey,
            flowableDefinitionId,
            operatorId,
            operatorId,
            versionId
        );
    }

    public WorkflowProcessTypeAdminRow getProcessTypeOrThrow(Long processTypeId) {
        List<WorkflowProcessTypeAdminRow> rows = jdbcTemplate.query("""
            SELECT id, process_code, process_name, biz_type, status, created_at, updated_at
            FROM wf_process_type
            WHERE id = ? AND is_deleted = 0
            LIMIT 1
            """,
            this::mapProcessType,
            processTypeId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "流程类型不存在");
        }
        return rows.get(0);
    }

    public List<WorkflowProcessTypeAdminRow> listProcessTypes(String processCode) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT id, process_code, process_name, biz_type, status, created_at, updated_at
            FROM wf_process_type
            WHERE is_deleted = 0
            """);
        if (StringUtils.hasText(processCode)) {
            sql.append("  AND process_code = ?\n");
            args.add(processCode.trim());
        }
        sql.append("ORDER BY process_code ASC, id ASC");
        return jdbcTemplate.query(sql.toString(), this::mapProcessType, args.toArray());
    }

    public WorkflowTemplateAdminRow getTemplateOrThrow(Long templateId) {
        List<WorkflowTemplateAdminRow> rows = jdbcTemplate.query("""
            SELECT wt.id,
                   wt.process_type_id,
                   wpt.process_code,
                   wpt.process_name,
                   wt.template_code,
                   wt.template_name,
                   wt.status,
                   wt.created_at,
                   wt.updated_at,
                   current_wtv.id AS current_version_id,
                   current_wtv.version_no AS current_version_no,
                   current_wtv.flowable_definition_key AS current_flowable_definition_key,
                   current_wtv.flowable_definition_id AS current_flowable_definition_id,
                   current_wtv.status AS current_version_status,
                   current_wtv.effective_from AS current_effective_from,
                   current_wtv.effective_to AS current_effective_to
            FROM wf_template wt
            JOIN wf_process_type wpt ON wpt.id = wt.process_type_id
            LEFT JOIN wf_template_version current_wtv
                ON current_wtv.id = (
                    SELECT wtv.id
                    FROM wf_template_version wtv
                    WHERE wtv.template_id = wt.id
                      AND wtv.status = 'PUBLISHED'
                      AND wtv.effective_from <= NOW()
                      AND (wtv.effective_to IS NULL OR wtv.effective_to > NOW())
                    ORDER BY wtv.version_no DESC, wtv.id DESC
                    LIMIT 1
                )
            WHERE wt.id = ?
              AND wt.is_deleted = 0
              AND wpt.is_deleted = 0
            LIMIT 1
            """,
            this::mapTemplate,
            templateId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "流程模板不存在");
        }
        return rows.get(0);
    }

    public WorkflowTemplateVersionAdminRow getTemplateVersionOrThrow(Long versionId) {
        List<WorkflowTemplateVersionAdminRow> rows = jdbcTemplate.query("""
            SELECT id, template_id, version_no, flowable_definition_key, flowable_definition_id,
                   form_schema_json, rule_snapshot_json, published_by, published_at,
                   effective_from, effective_to, status, created_at, updated_at
            FROM wf_template_version
            WHERE id = ?
            LIMIT 1
            """,
            this::mapTemplateVersion,
            versionId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "流程模板版本不存在");
        }
        return rows.get(0);
    }

    public List<WorkflowTemplateAdminRow> listTemplates(String processCode) {
        List<Object> args = new ArrayList<>();
        StringBuilder whereSql = new StringBuilder("""
            WHERE wt.is_deleted = 0
              AND wpt.is_deleted = 0
            """);
        if (StringUtils.hasText(processCode)) {
            whereSql.append("  AND wpt.process_code = ?\n");
            args.add(processCode.trim());
        }
        return jdbcTemplate.query("""
            SELECT wt.id,
                   wt.process_type_id,
                   wpt.process_code,
                   wpt.process_name,
                   wt.template_code,
                   wt.template_name,
                   wt.status,
                   wt.created_at,
                   wt.updated_at,
                   current_wtv.id AS current_version_id,
                   current_wtv.version_no AS current_version_no,
                   current_wtv.flowable_definition_key AS current_flowable_definition_key,
                   current_wtv.flowable_definition_id AS current_flowable_definition_id,
                   current_wtv.status AS current_version_status,
                   current_wtv.effective_from AS current_effective_from,
                   current_wtv.effective_to AS current_effective_to
            FROM wf_template wt
            JOIN wf_process_type wpt ON wpt.id = wt.process_type_id
            LEFT JOIN wf_template_version current_wtv
                ON current_wtv.id = (
                    SELECT wtv.id
                    FROM wf_template_version wtv
                    WHERE wtv.template_id = wt.id
                      AND wtv.status = 'PUBLISHED'
                      AND wtv.effective_from <= NOW()
                      AND (wtv.effective_to IS NULL OR wtv.effective_to > NOW())
                    ORDER BY wtv.version_no DESC, wtv.id DESC
                    LIMIT 1
                )
            """ + whereSql + """
            ORDER BY wpt.process_code ASC, wt.template_code ASC, wt.id DESC
            """,
            this::mapTemplate,
            args.toArray()
        );
    }

    public List<WorkflowTemplateVersionAdminRow> listTemplateVersions(Long templateId) {
        return jdbcTemplate.query("""
            SELECT id, template_id, version_no, flowable_definition_key, flowable_definition_id,
                   form_schema_json, rule_snapshot_json, published_by, published_at,
                   effective_from, effective_to, status, created_at, updated_at
            FROM wf_template_version
            WHERE template_id = ?
            ORDER BY version_no DESC, id DESC
            """,
            this::mapTemplateVersion,
            templateId
        );
    }

    public long countRequests(AdminWorkflowRequestQuery query) {
        List<Object> args = new ArrayList<>();
        String whereSql = buildRequestWhereSql(query, args);
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM wf_request wr
            """ + whereSql,
            Long.class,
            args.toArray()
        );
        return count == null ? 0L : count;
    }

    public List<WorkflowRequestSummaryRow> pageRequests(AdminWorkflowRequestQuery query) {
        List<Object> args = new ArrayList<>();
        String whereSql = buildRequestWhereSql(query, args);
        args.add(Math.max(query.getPageSize(), 1L));
        args.add((Math.max(query.getPageNo(), 1L) - 1) * Math.max(query.getPageSize(), 1L));
        return jdbcTemplate.query("""
            SELECT wr.id, wr.request_no, wr.biz_type, wr.biz_id, wr.title, wr.status,
                   wr.current_step_key, wr.current_step_name, wr.submitted_at, wr.finished_at
            FROM wf_request wr
            """ + whereSql + """
            ORDER BY wr.submitted_at DESC, wr.id DESC
            LIMIT ? OFFSET ?
            """,
            this::mapRequestSummary,
            args.toArray()
        );
    }

    public WorkflowRequestDetailRow getRequestDetailOrThrow(Long requestId) {
        List<WorkflowRequestDetailRow> rows = jdbcTemplate.query("""
            SELECT id, request_no, biz_type, biz_id, applicant_user_id, title, status,
                   current_step_key, current_step_name, flowable_process_instance_id,
                   submitted_at, finished_at
            FROM wf_request
            WHERE id = ?
              AND is_deleted = 0
            LIMIT 1
            """,
            this::mapRequestDetail,
            requestId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "审批申请不存在");
        }
        return rows.get(0);
    }

    public WorkflowLeaveDetailRow getLeaveDetailByRequestId(Long requestId) {
        List<WorkflowLeaveDetailRow> rows = jdbcTemplate.query("""
            SELECT leave_type_id, start_at, end_at, duration_minutes, reason
            FROM wf_req_leave
            WHERE request_id = ?
            LIMIT 1
            """,
            (rs, rowNum) -> new WorkflowLeaveDetailRow(
                rs.getLong("leave_type_id"),
                rs.getTimestamp("start_at").toLocalDateTime(),
                rs.getTimestamp("end_at").toLocalDateTime(),
                rs.getInt("duration_minutes"),
                rs.getString("reason")
            ),
            requestId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public WorkflowClockCorrectionDetailRow getClockCorrectionDetailByRequestId(Long requestId) {
        List<WorkflowClockCorrectionDetailRow> rows = jdbcTemplate.query("""
            SELECT target_date, missing_punch_type, expected_punch_at, reason
            FROM wf_req_clock_correction
            WHERE request_id = ?
            LIMIT 1
            """,
            (rs, rowNum) -> new WorkflowClockCorrectionDetailRow(
                rs.getDate("target_date").toLocalDate(),
                rs.getString("missing_punch_type"),
                rs.getTimestamp("expected_punch_at").toLocalDateTime(),
                rs.getString("reason")
            ),
            requestId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<WorkflowAttachmentRow> listAttachmentsByRequestId(Long requestId) {
        return jdbcTemplate.query("""
            SELECT ar.id AS attachment_ref_id,
                   ar.file_object_id,
                   ar.owner_type,
                   ar.owner_id,
                   ar.display_name,
                   ar.uploader_type,
                   ar.uploader_id,
                   ar.deleted_at,
                   ar.is_deleted AS attachment_deleted,
                   fo.is_deleted AS file_deleted,
                   fo.mime_type,
                   fo.file_size,
                   fo.content_sha256,
                   fo.scan_status
            FROM wf_request_attachment wra
            JOIN sys_attachment_ref ar ON ar.id = wra.attachment_ref_id
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE wra.request_id = ?
              AND wra.is_deleted = 0
            ORDER BY wra.sort_order ASC, wra.id ASC
            """,
            this::mapAttachment,
            requestId
        );
    }

    public List<WorkflowTaskActionRow> listTaskActions(Long requestId) {
        return jdbcTemplate.query("""
            SELECT id, request_id, flowable_task_id, step_key, step_name, actor_type,
                   actor_id, action_type, action_comment, agent_decision_json,
                   before_status, after_status, acted_at
            FROM wf_task_action
            WHERE request_id = ?
            ORDER BY acted_at ASC, id ASC
            """,
            this::mapTaskAction,
            requestId
        );
    }

    private String buildRequestWhereSql(AdminWorkflowRequestQuery query, List<Object> args) {
        StringBuilder whereSql = new StringBuilder("""
            WHERE wr.is_deleted = 0
            """);
        if (StringUtils.hasText(query.getStatus())) {
            whereSql.append("  AND wr.status = ?\n");
            args.add(query.getStatus().trim().toUpperCase());
        }
        if (StringUtils.hasText(query.getBizType())) {
            whereSql.append("  AND wr.biz_type = ?\n");
            args.add(query.getBizType().trim().toUpperCase());
        }
        if (query.getApplicantUserId() != null) {
            whereSql.append("  AND wr.applicant_user_id = ?\n");
            args.add(query.getApplicantUserId());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            whereSql.append("  AND (wr.request_no LIKE ? OR wr.title LIKE ?)\n");
            String keyword = "%" + query.getKeyword().trim() + "%";
            args.add(keyword);
            args.add(keyword);
        }
        return whereSql.toString();
    }

    private boolean exists(String sql, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        return count != null && count > 0;
    }

    private WorkflowProcessTypeAdminRow mapProcessType(ResultSet rs, int rowNum) throws SQLException {
        return new WorkflowProcessTypeAdminRow(
            rs.getLong("id"),
            rs.getString("process_code"),
            rs.getString("process_name"),
            rs.getString("biz_type"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private WorkflowTemplateAdminRow mapTemplate(ResultSet rs, int rowNum) throws SQLException {
        Long currentVersionId = rs.getObject("current_version_id", Long.class);
        return new WorkflowTemplateAdminRow(
            rs.getLong("id"),
            rs.getLong("process_type_id"),
            rs.getString("process_code"),
            rs.getString("process_name"),
            rs.getString("template_code"),
            rs.getString("template_name"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime(),
            currentVersionId,
            currentVersionId == null ? null : rs.getInt("current_version_no"),
            rs.getString("current_flowable_definition_key"),
            rs.getString("current_flowable_definition_id"),
            rs.getString("current_version_status"),
            rs.getTimestamp("current_effective_from") == null ? null : rs.getTimestamp("current_effective_from").toLocalDateTime(),
            rs.getTimestamp("current_effective_to") == null ? null : rs.getTimestamp("current_effective_to").toLocalDateTime()
        );
    }

    private WorkflowTemplateVersionAdminRow mapTemplateVersion(ResultSet rs, int rowNum) throws SQLException {
        return new WorkflowTemplateVersionAdminRow(
            rs.getLong("id"),
            rs.getLong("template_id"),
            rs.getInt("version_no"),
            rs.getString("flowable_definition_key"),
            rs.getString("flowable_definition_id"),
            rs.getString("form_schema_json"),
            rs.getString("rule_snapshot_json"),
            rs.getObject("published_by", Long.class),
            rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toLocalDateTime(),
            rs.getTimestamp("effective_from") == null ? null : rs.getTimestamp("effective_from").toLocalDateTime(),
            rs.getTimestamp("effective_to") == null ? null : rs.getTimestamp("effective_to").toLocalDateTime(),
            rs.getString("status"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private WorkflowRequestSummaryRow mapRequestSummary(ResultSet rs, int rowNum) throws SQLException {
        return new WorkflowRequestSummaryRow(
            rs.getLong("id"),
            rs.getString("request_no"),
            rs.getString("biz_type"),
            rs.getLong("biz_id"),
            rs.getString("title"),
            rs.getString("status"),
            rs.getString("current_step_key"),
            rs.getString("current_step_name"),
            rs.getTimestamp("submitted_at") == null ? null : rs.getTimestamp("submitted_at").toLocalDateTime(),
            rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toLocalDateTime()
        );
    }

    private WorkflowRequestDetailRow mapRequestDetail(ResultSet rs, int rowNum) throws SQLException {
        return new WorkflowRequestDetailRow(
            rs.getLong("id"),
            rs.getString("request_no"),
            rs.getString("biz_type"),
            rs.getLong("biz_id"),
            rs.getLong("applicant_user_id"),
            rs.getString("title"),
            rs.getString("status"),
            rs.getString("current_step_key"),
            rs.getString("current_step_name"),
            rs.getString("flowable_process_instance_id"),
            rs.getTimestamp("submitted_at") == null ? null : rs.getTimestamp("submitted_at").toLocalDateTime(),
            rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toLocalDateTime()
        );
    }

    private WorkflowAttachmentRow mapAttachment(ResultSet rs, int rowNum) throws SQLException {
        return new WorkflowAttachmentRow(
            rs.getLong("attachment_ref_id"),
            rs.getLong("file_object_id"),
            rs.getString("owner_type"),
            rs.getLong("owner_id"),
            rs.getString("display_name"),
            rs.getString("uploader_type"),
            rs.getLong("uploader_id"),
            rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toLocalDateTime(),
            rs.getInt("attachment_deleted"),
            rs.getInt("file_deleted"),
            rs.getString("mime_type"),
            rs.getLong("file_size"),
            rs.getString("content_sha256"),
            rs.getString("scan_status")
        );
    }

    private WorkflowTaskActionRow mapTaskAction(ResultSet rs, int rowNum) throws SQLException {
        return new WorkflowTaskActionRow(
            rs.getLong("id"),
            rs.getLong("request_id"),
            rs.getString("flowable_task_id"),
            rs.getString("step_key"),
            rs.getString("step_name"),
            rs.getString("actor_type"),
            rs.getLong("actor_id"),
            rs.getString("action_type"),
            rs.getString("action_comment"),
            rs.getString("agent_decision_json"),
            rs.getString("before_status"),
            rs.getString("after_status"),
            rs.getTimestamp("acted_at").toLocalDateTime()
        );
    }
}
