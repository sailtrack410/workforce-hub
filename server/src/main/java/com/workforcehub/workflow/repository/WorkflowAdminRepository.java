package com.workforcehub.workflow.repository;

import com.workforcehub.common.exception.BizException;
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
}
