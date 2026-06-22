package com.workforcehub.agent.repository;

import com.workforcehub.common.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * module-agent 第一批 JDBC 仓储。
 */
@Repository
public class AgentRepository {

    private final JdbcTemplate jdbcTemplate;

    public AgentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsAgentCode(String agentCode) {
        return exists("SELECT COUNT(*) FROM agt_agent WHERE agent_code = ? AND is_deleted = 0", agentCode);
    }

    public boolean existsToolCode(String toolCode) {
        return exists("SELECT COUNT(*) FROM agt_tool WHERE tool_code = ? AND is_deleted = 0", toolCode);
    }

    public boolean existsAgentVersion(Long agentId, Integer versionNo) {
        return exists("SELECT COUNT(*) FROM agt_agent_version WHERE agent_id = ? AND version_no = ?", agentId, versionNo);
    }

    public boolean existsAgentVersionLabel(Long agentId, String versionLabel) {
        return exists("SELECT COUNT(*) FROM agt_agent_version WHERE agent_id = ? AND version_label = ?", agentId, versionLabel);
    }

    public void insertAgent(Long agentId,
                            String agentCode,
                            String agentName,
                            String agentType,
                            String identityMode,
                            Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO agt_agent
                (id, agent_code, agent_name, agent_type, identity_mode, status, created_by, updated_by)
            VALUES
                (?, ?, ?, ?, ?, 'DRAFT', ?, ?)
            """, agentId, agentCode, agentName, agentType, identityMode, operatorId, operatorId);
    }

    public void insertAgentVersion(Long versionId,
                                   Long agentId,
                                   Integer versionNo,
                                   String versionLabel,
                                   String modelConfigJson,
                                   String systemPrompt,
                                   String workflowConfigJson,
                                   Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO agt_agent_version
                (id, agent_id, version_no, version_label, model_config_json, system_prompt, workflow_config_json,
                 publish_status, created_by, updated_by)
            VALUES
                (?, ?, ?, ?, CAST(? AS JSON), ?, CAST(? AS JSON), 'DRAFT', ?, ?)
            """, versionId, agentId, versionNo, versionLabel, modelConfigJson, systemPrompt, workflowConfigJson, operatorId, operatorId);
    }

    public void updateAgentVersion(Long versionId,
                                   String modelConfigJson,
                                   String systemPrompt,
                                   String workflowConfigJson,
                                   Long operatorId) {
        jdbcTemplate.update("""
            UPDATE agt_agent_version
            SET model_config_json = CAST(? AS JSON),
                system_prompt = ?,
                workflow_config_json = CAST(? AS JSON),
                workflow_test_signature = NULL,
                workflow_tested_at = NULL,
                updated_by = ?
            WHERE id = ?
            """, modelConfigJson, systemPrompt, workflowConfigJson, operatorId, versionId);
    }

    public void updateAgentVersionWorkflowTest(Long versionId,
                                               String modelConfigJson,
                                               String systemPrompt,
                                               String workflowConfigJson,
                                               String workflowTestSignature,
                                               Long operatorId) {
        int updated = jdbcTemplate.update("""
            UPDATE agt_agent_version
            SET model_config_json = CAST(? AS JSON),
                system_prompt = ?,
                workflow_config_json = CAST(? AS JSON),
                workflow_test_signature = ?,
                workflow_tested_at = NOW(),
                updated_by = ?
            WHERE id = ?
              AND publish_status = 'DRAFT'
            """, modelConfigJson, systemPrompt, workflowConfigJson, workflowTestSignature, operatorId, versionId);
        if (updated == 0) {
            throw new BizException(409, "只有草稿版本可以测试");
        }
    }

    public void insertTool(Long toolId,
                           String toolCode,
                           String toolName,
                           String toolType,
                           Long connectorId,
                           String riskLevel,
                           boolean readOnlyFlag,
                           String status,
                           Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO agt_tool
                (id, tool_code, tool_name, tool_type, connector_id, risk_level,
                 read_only_flag, status, created_by, updated_by)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, toolId, toolCode, toolName, toolType, connectorId, riskLevel,
            readOnlyFlag ? 1 : 0, status, operatorId, operatorId);
    }

    public void insertToolBinding(Long bindingId,
                                  Long versionId,
                                  Long toolId,
                                  String approvalMode,
                                  String argSchemaJson,
                                  String riskControlJson,
                                  String status,
                                  Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO agt_agent_tool
                (id, agent_version_id, tool_id, permission_mode, approval_mode,
                 arg_schema_json, risk_control_json, status, created_by, updated_by)
            VALUES
                (?, ?, ?, 'ALLOWED', ?, CAST(? AS JSON), CAST(? AS JSON), ?, ?, ?)
            """, bindingId, versionId, toolId, approvalMode, argSchemaJson, riskControlJson,
            status, operatorId, operatorId);
    }

    public void upsertAcl(Long aclId,
                          Long agentId,
                          String subjectType,
                          Long subjectId,
                          String permCode,
                          String status,
                          Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO agt_agent_acl
                (id, agent_id, subject_type, subject_id, perm_code, status, created_by, updated_by)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                status = VALUES(status),
                updated_by = VALUES(updated_by)
            """, aclId, agentId, subjectType, subjectId, permCode, status, operatorId, operatorId);
    }

    public void publishVersion(Long agentId, Long versionId, Long operatorId) {
        jdbcTemplate.update("""
            UPDATE agt_agent_version
            SET publish_status = 'ARCHIVED',
                updated_by = ?
            WHERE agent_id = ?
              AND id <> ?
              AND publish_status = 'PUBLISHED'
            """, operatorId, agentId, versionId);
        jdbcTemplate.update("""
            UPDATE agt_agent_version
            SET publish_status = 'PUBLISHED',
                published_at = NOW(),
                updated_by = ?
            WHERE id = ?
            """, operatorId, versionId);
        jdbcTemplate.update("""
            UPDATE agt_agent
            SET published_version_id = ?,
                status = 'ACTIVE',
                updated_by = ?
            WHERE id = ?
              AND is_deleted = 0
            """, versionId, operatorId, agentId);
    }

    public long countAgents() {
        return countAgents(null);
    }

    public long countAgents(String keyword) {
        List<Object> params = new ArrayList<>();
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM agt_agent aa
            """ + agentWhereSql(keyword, params), Long.class, params.toArray());
        return count == null ? 0L : count;
    }

    public List<AgentRow> pageAgents(long offset, long limit) {
        return pageAgents(null, offset, limit);
    }

    public List<AgentRow> pageAgents(String keyword, long offset, long limit) {
        List<Object> params = new ArrayList<>();
        String whereSql = agentWhereSql(keyword, params);
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(agentSelectSql() + """
            """ + whereSql + """
            ORDER BY aa.created_at DESC, aa.id DESC
            LIMIT ? OFFSET ?
            """, this::mapAgent, params.toArray());
    }

    public void disableAgent(Long agentId, Long operatorId) {
        jdbcTemplate.update("""
            UPDATE agt_agent
            SET status = 'INACTIVE',
                updated_by = ?
            WHERE id = ?
              AND is_deleted = 0
            """, operatorId, agentId);
    }

    public void enableAgent(Long agentId, Long operatorId) {
        jdbcTemplate.update("""
            UPDATE agt_agent
            SET status = 'ACTIVE',
                updated_by = ?
            WHERE id = ?
              AND is_deleted = 0
            """, operatorId, agentId);
    }

    public Long insertTask(Long taskId,
                           Long agentId,
                           Long versionId,
                           String triggerType,
                           String contextType,
                           Long contextId,
                           String triggeredByType,
                           Long triggeredById,
                           String status,
                           LocalDateTime waitingDeadlineAt,
                           Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO agt_task
                (id, agent_id, agent_version_id, trigger_type, context_type, context_id,
                 triggered_by_type, triggered_by_id, waiting_deadline_at, status, created_by, updated_by)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, taskId, agentId, versionId, triggerType, contextType, contextId,
            triggeredByType, triggeredById, waitingDeadlineAt, status, operatorId, operatorId);
        return taskId;
    }

    public void markTaskRunning(Long taskId, Long operatorId) {
        jdbcTemplate.update("""
            UPDATE agt_task
            SET status = 'RUNNING',
                started_at = NOW(),
                updated_by = ?
            WHERE id = ?
            """, operatorId, taskId);
    }

    public void finishTask(Long taskId, String status, String errorMessage, Long operatorId) {
        jdbcTemplate.update("""
            UPDATE agt_task
            SET status = ?,
                finished_at = CASE WHEN ? IN ('SUCCEEDED', 'FAILED', 'REJECTED', 'CANCELLED') THEN NOW() ELSE finished_at END,
                error_message = ?,
                updated_by = ?
            WHERE id = ?
            """, status, status, errorMessage, operatorId, taskId);
    }

    public void updateTaskWorkflowSnapshot(Long taskId, String workflowSnapshotJson, Long operatorId) {
        jdbcTemplate.update("""
            UPDATE agt_task
            SET workflow_snapshot_json = CAST(? AS JSON),
                updated_by = ?
            WHERE id = ?
            """, workflowSnapshotJson, operatorId, taskId);
    }

    public void updateTaskExecutionTrace(Long taskId, String executionTraceJson, Long operatorId) {
        jdbcTemplate.update("""
            UPDATE agt_task
            SET execution_trace_json = CAST(? AS JSON),
                updated_by = ?
            WHERE id = ?
            """, executionTraceJson, operatorId, taskId);
    }

    public void insertToolCallLog(Long logId,
                                  Long taskId,
                                  Long toolId,
                                  String requestPayloadJson,
                                  String responsePayloadJson,
                                  String approvalStatus,
                                  String resultStatus) {
        jdbcTemplate.update("""
            INSERT INTO agt_tool_call_log
                (id, task_id, tool_id, request_payload_json, response_payload_json,
                 approval_status, result_status)
            VALUES
                (?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), ?, ?)
            """, logId, taskId, toolId, requestPayloadJson, responsePayloadJson, approvalStatus, resultStatus);
    }

    public void insertAuditLog(Long logId,
                               Long taskId,
                               String actionType,
                               String targetType,
                               Long targetId,
                               String decisionSummary,
                               boolean riskFlag) {
        jdbcTemplate.update("""
            INSERT INTO agt_audit_log
                (id, task_id, action_type, target_type, target_id, decision_summary, risk_flag)
            VALUES
                (?, ?, ?, ?, ?, ?, ?)
            """, logId, taskId, actionType, targetType, targetId, decisionSummary, riskFlag ? 1 : 0);
    }

    public AgentRow getAgentOrThrow(Long agentId) {
        List<AgentRow> rows = jdbcTemplate.query(agentSelectSql() + """
            WHERE aa.id = ? AND aa.is_deleted = 0
            LIMIT 1
            """, this::mapAgent, agentId);
        if (rows.isEmpty()) {
            throw new BizException(404, "Agent不存在");
        }
        return rows.get(0);
    }

    public AgentRow findActivePreReviewAgent() {
        List<AgentRow> rows = jdbcTemplate.query(agentSelectSql() + """
            WHERE aa.agent_type = 'PRE_REVIEW'
              AND aa.status = 'ACTIVE'
              AND aa.published_version_id IS NOT NULL
              AND aa.is_deleted = 0
            ORDER BY aa.updated_at DESC, aa.id DESC
            LIMIT 1
            """, this::mapAgent);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public AgentVersionRow getVersionOrThrow(Long versionId) {
        List<AgentVersionRow> rows = jdbcTemplate.query(versionSelectSql() + """
            WHERE id = ?
            LIMIT 1
            """, this::mapVersion, versionId);
        if (rows.isEmpty()) {
            throw new BizException(404, "Agent版本不存在");
        }
        return rows.get(0);
    }

    public List<AgentVersionRow> listVersionsByAgent(Long agentId) {
        return jdbcTemplate.query(versionSelectSql() + """
            WHERE agent_id = ?
            ORDER BY version_no DESC, id DESC
            """, this::mapVersion, agentId);
    }

    public int nextAgentVersionNo(Long agentId) {
        Integer max = jdbcTemplate.queryForObject("""
            SELECT COALESCE(MAX(version_no), 0)
            FROM agt_agent_version
            WHERE agent_id = ?
            """, Integer.class, agentId);
        return (max == null ? 0 : max) + 1;
    }

    public AgentToolRow getToolOrThrow(Long toolId) {
        List<AgentToolRow> rows = jdbcTemplate.query(toolSelectSql() + """
            WHERE id = ? AND is_deleted = 0
            LIMIT 1
            """, this::mapTool, toolId);
        if (rows.isEmpty()) {
            throw new BizException(404, "Agent工具不存在");
        }
        return rows.get(0);
    }

    public AgentToolRow getActiveToolByCodeOrThrow(String toolCode) {
        List<AgentToolRow> rows = jdbcTemplate.query(toolSelectSql() + """
            WHERE tool_code = ? AND status = 'ACTIVE' AND is_deleted = 0
            LIMIT 1
            """, this::mapTool, toolCode);
        if (rows.isEmpty()) {
            throw new BizException(404, "Agent工具不存在或未启用");
        }
        return rows.get(0);
    }

    public List<AgentToolRow> listTools() {
        return jdbcTemplate.query(toolSelectSql() + """
            WHERE is_deleted = 0
            ORDER BY tool_code ASC, id DESC
            """, this::mapTool);
    }

    public List<AgentAclRow> listAclByAgent(Long agentId) {
        return jdbcTemplate.query("""
            SELECT id, agent_id, subject_type, subject_id, perm_code, status, created_at, updated_at
            FROM agt_agent_acl
            WHERE agent_id = ?
            ORDER BY updated_at DESC, id DESC
            """, this::mapAcl, agentId);
    }

    public boolean hasActiveUsableAcl(Long agentId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM agt_agent_acl
            WHERE agent_id = ?
              AND status = 'ACTIVE'
              AND LOWER(perm_code) IN ('use', 'manage')
            """, Long.class, agentId);
        return count != null && count > 0;
    }

    public AgentToolBindingRow getBindingByVersionAndToolCode(Long versionId, String toolCode) {
        List<AgentToolBindingRow> rows = jdbcTemplate.query(bindingSelectSql() + """
            WHERE aat.agent_version_id = ?
              AND at.tool_code = ?
              AND aat.permission_mode = 'ALLOWED'
              AND aat.status = 'ACTIVE'
              AND at.status = 'ACTIVE'
              AND at.is_deleted = 0
            LIMIT 1
            """, this::mapBinding, versionId, toolCode);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public AgentToolBindingRow getBindingOrThrow(Long bindingId) {
        List<AgentToolBindingRow> rows = jdbcTemplate.query(bindingSelectSql() + """
            WHERE aat.id = ?
            LIMIT 1
            """, this::mapBinding, bindingId);
        if (rows.isEmpty()) {
            throw new BizException(404, "Agent工具绑定不存在");
        }
        return rows.get(0);
    }

    public AgentAclRow getAclOrThrow(Long agentId, String subjectType, Long subjectId, String permCode) {
        List<AgentAclRow> rows = jdbcTemplate.query("""
            SELECT id, agent_id, subject_type, subject_id, perm_code, status, created_at, updated_at
            FROM agt_agent_acl
            WHERE agent_id = ?
              AND subject_type = ?
              AND subject_id = ?
              AND perm_code = ?
            LIMIT 1
            """, this::mapAcl, agentId, subjectType, subjectId, permCode);
        if (rows.isEmpty()) {
            throw new BizException(404, "Agent授权记录不存在");
        }
        return rows.get(0);
    }

    public AgentTaskRow getTaskOrThrow(Long taskId) {
        List<AgentTaskRow> rows = jdbcTemplate.query(taskSelectSql() + """
            WHERE id = ?
            LIMIT 1
            """, this::mapTask, taskId);
        if (rows.isEmpty()) {
            throw new BizException(404, "Agent任务不存在");
        }
        return rows.get(0);
    }

    public AgentTaskRow getTaskForUpdateOrThrow(Long taskId) {
        List<AgentTaskRow> rows = jdbcTemplate.query(taskSelectSql() + """
            WHERE id = ?
            LIMIT 1
            FOR UPDATE
            """, this::mapTask, taskId);
        if (rows.isEmpty()) {
            throw new BizException(404, "Agent任务不存在");
        }
        return rows.get(0);
    }

    public List<AgentRow> listUsableAgentsForUser(Long userId, String keyword) {
        List<Object> params = new ArrayList<>();
        params.add(userId);
        params.add(userId);
        params.add(userId);
        params.add(userId);
        params.add(userId);
        params.add(userId);
        String keywordSql = "";
        if (keyword != null && !keyword.isBlank()) {
            keywordSql = """
                AND (
                    aa.agent_name LIKE ?
                    OR aa.agent_code LIKE ?
                    OR aa.agent_type LIKE ?
                )
                """;
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        return jdbcTemplate.query(agentSelectSql() + """
            WHERE aa.status = 'ACTIVE'
              AND aa.published_version_id IS NOT NULL
              AND aa.is_deleted = 0
              AND EXISTS (
                  SELECT 1
                  FROM agt_agent_acl acl
                  WHERE acl.agent_id = aa.id
                    AND acl.status = 'ACTIVE'
                    AND LOWER(acl.perm_code) IN ('visible', 'use')
                    AND (
                        (acl.subject_type = 'USER' AND acl.subject_id = ?)
                        OR (
                            acl.subject_type = 'DEPARTMENT'
                            AND acl.subject_id IN (
                                SELECT primary_dept_id
                                FROM org_user
                                WHERE id = ? AND is_deleted = 0
                            )
                        )
                        OR (
                            acl.subject_type = 'GROUP'
                            AND EXISTS (
                                SELECT 1
                                FROM im_conversation_member gm
                                JOIN im_conversation gc ON gc.id = gm.conversation_id
                                JOIN im_group_profile gp ON gp.conversation_id = gc.id
                                WHERE gm.conversation_id = acl.subject_id
                                  AND gm.member_type = 'USER'
                                  AND gm.member_id = ?
                                  AND gm.status = 'ACTIVE'
                                  AND gc.conversation_type = 'GROUP'
                                  AND gc.status = 'ACTIVE'
                                  AND gc.is_deleted = 0
                                  AND gp.status = 'ACTIVE'
                            )
                        )
                        OR (
                            acl.subject_type = 'ROLE'
                            AND (
                                (acl.subject_id = 1 AND EXISTS (
                                    SELECT 1
                                    FROM org_user
                                    WHERE id = ?
                                      AND status = 'ACTIVE'
                                      AND employment_status IN ('ACTIVE', 'PROBATION', 'PENDING_OFFBOARD')
                                      AND is_deleted = 0
                                ))
                                OR (acl.subject_id = 2 AND (
                                    EXISTS (
                                        SELECT 1
                                        FROM org_user
                                        WHERE manager_user_id = ?
                                          AND status = 'ACTIVE'
                                          AND is_deleted = 0
                                    )
                                    OR EXISTS (
                                        SELECT 1
                                        FROM org_department_leader dl
                                        WHERE dl.user_id = ?
                                          AND dl.status = 'ACTIVE'
                                    )
                                ))
                            )
                        )
                    )
              )
            """ + keywordSql + """
            ORDER BY aa.agent_name ASC, aa.id DESC
            """, this::mapAgent, params.toArray());
    }

    public boolean canUseAgent(Long agentId, Long userId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM agt_agent aa
            WHERE aa.id = ?
              AND aa.status = 'ACTIVE'
              AND aa.published_version_id IS NOT NULL
              AND aa.is_deleted = 0
              AND EXISTS (
                  SELECT 1
                  FROM agt_agent_acl acl
                  WHERE acl.agent_id = aa.id
                    AND acl.status = 'ACTIVE'
                    AND LOWER(acl.perm_code) IN ('use', 'manage')
                    AND (
                        (acl.subject_type = 'USER' AND acl.subject_id = ?)
                        OR (
                            acl.subject_type = 'DEPARTMENT'
                            AND acl.subject_id IN (
                                SELECT primary_dept_id
                                FROM org_user
                                WHERE id = ? AND is_deleted = 0
                            )
                        )
                        OR (
                            acl.subject_type = 'GROUP'
                            AND EXISTS (
                                SELECT 1
                                FROM im_conversation_member gm
                                JOIN im_conversation gc ON gc.id = gm.conversation_id
                                JOIN im_group_profile gp ON gp.conversation_id = gc.id
                                WHERE gm.conversation_id = acl.subject_id
                                  AND gm.member_type = 'USER'
                                  AND gm.member_id = ?
                                  AND gm.status = 'ACTIVE'
                                  AND gc.conversation_type = 'GROUP'
                                  AND gc.status = 'ACTIVE'
                                  AND gc.is_deleted = 0
                                  AND gp.status = 'ACTIVE'
                            )
                        )
                        OR (
                            acl.subject_type = 'ROLE'
                            AND (
                                (acl.subject_id = 1 AND EXISTS (
                                    SELECT 1
                                    FROM org_user
                                    WHERE id = ?
                                      AND status = 'ACTIVE'
                                      AND employment_status IN ('ACTIVE', 'PROBATION', 'PENDING_OFFBOARD')
                                      AND is_deleted = 0
                                ))
                                OR (acl.subject_id = 2 AND (
                                    EXISTS (
                                        SELECT 1
                                        FROM org_user
                                        WHERE manager_user_id = ?
                                          AND status = 'ACTIVE'
                                          AND is_deleted = 0
                                    )
                                    OR EXISTS (
                                        SELECT 1
                                        FROM org_department_leader dl
                                        WHERE dl.user_id = ?
                                          AND dl.status = 'ACTIVE'
                                    )
                                ))
                            )
                        )
                    )
              )
            """, Long.class, agentId, userId, userId, userId, userId, userId, userId);
        return count != null && count > 0;
    }

    public int countToolCalls(Long taskId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM agt_tool_call_log WHERE task_id = ?",
            Integer.class,
            taskId
        );
        return count == null ? 0 : count;
    }

    public List<AgentPendingToolCallRow> listPendingToolCalls(Long taskId) {
        return jdbcTemplate.query("""
            SELECT log.id,
                   log.task_id,
                   log.tool_id,
                   log.request_payload_json,
                   at.tool_code,
                   at.tool_name,
                   at.tool_type,
                   at.risk_level,
                   at.read_only_flag,
                   aat.approval_mode
            FROM agt_tool_call_log log
            JOIN agt_task task ON task.id = log.task_id
            JOIN agt_agent_tool aat ON aat.agent_version_id = task.agent_version_id
                                    AND aat.tool_id = log.tool_id
            JOIN agt_tool at ON at.id = log.tool_id
            WHERE log.task_id = ?
              AND log.approval_status = 'PENDING'
              AND log.result_status = 'WAITING_CONFIRM'
              AND aat.permission_mode = 'ALLOWED'
              AND aat.status = 'ACTIVE'
              AND at.status = 'ACTIVE'
              AND at.is_deleted = 0
            ORDER BY log.called_at ASC, log.id ASC
            """, this::mapPendingToolCall, taskId);
    }

    public void markToolCallApprovedSuccess(Long logId, String responsePayloadJson) {
        jdbcTemplate.update("""
            UPDATE agt_tool_call_log
            SET approval_status = 'APPROVED',
                result_status = 'SUCCESS',
                response_payload_json = CAST(? AS JSON),
                called_at = NOW()
            WHERE id = ?
              AND approval_status = 'PENDING'
              AND result_status = 'WAITING_CONFIRM'
            """, responsePayloadJson, logId);
    }

    public void markToolCallApprovedFailed(Long logId, String responsePayloadJson) {
        jdbcTemplate.update("""
            UPDATE agt_tool_call_log
            SET approval_status = 'APPROVED',
                result_status = 'FAILED',
                response_payload_json = CAST(? AS JSON),
                called_at = NOW()
            WHERE id = ?
              AND approval_status = 'PENDING'
              AND result_status = 'WAITING_CONFIRM'
            """, responsePayloadJson, logId);
    }

    public void rejectWaitingTask(Long taskId, String responsePayloadJson, Long operatorId) {
        jdbcTemplate.update("""
            UPDATE agt_tool_call_log
            SET approval_status = 'REJECTED',
                result_status = 'REJECTED',
                response_payload_json = CAST(? AS JSON)
            WHERE task_id = ?
              AND approval_status = 'PENDING'
              AND result_status = 'WAITING_CONFIRM'
            """, responsePayloadJson, taskId);
        finishTask(taskId, "REJECTED", null, operatorId);
    }

    public void cancelWaitingTask(Long taskId, String responsePayloadJson, Long operatorId) {
        jdbcTemplate.update("""
            UPDATE agt_tool_call_log
            SET approval_status = 'CANCELLED',
                result_status = 'CANCELLED',
                response_payload_json = CAST(? AS JSON)
            WHERE task_id = ?
              AND approval_status = 'PENDING'
              AND result_status = 'WAITING_CONFIRM'
            """, responsePayloadJson, taskId);
        finishTask(taskId, "CANCELLED", null, operatorId);
    }

    public long countAdminAgentTasks(AgentTaskQueryCriteria criteria) {
        List<Object> params = new ArrayList<>();
        String sql = """
            SELECT COUNT(*)
            FROM agt_task task
            JOIN agt_agent agent ON agent.id = task.agent_id
            """ + adminTaskWhereSql(criteria, params);
        Long count = jdbcTemplate.queryForObject(sql, Long.class, params.toArray());
        return count == null ? 0L : count;
    }

    public List<AdminAgentTaskRow> pageAdminAgentTasks(AgentTaskQueryCriteria criteria, long offset, long limit) {
        List<Object> params = new ArrayList<>();
        String sql = adminTaskSelectSql()
            + adminTaskWhereSql(criteria, params)
            + """
            GROUP BY task.id,
                     task.agent_id,
                     agent.agent_code,
                     agent.agent_name,
                     task.agent_version_id,
                     task.trigger_type,
                     task.context_type,
                     task.context_id,
                     task.triggered_by_type,
                     task.triggered_by_id,
                     task.waiting_deadline_at,
                     task.workflow_snapshot_json,
                     task.execution_trace_json,
                     task.status,
                     task.started_at,
                     task.finished_at,
                     task.error_message,
                     task.created_at,
                     task.updated_at
            ORDER BY task.created_at DESC, task.id DESC
            LIMIT ? OFFSET ?
            """;
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql, this::mapAdminTask, params.toArray());
    }

    public AdminAgentTaskRow getAdminAgentTaskOrThrow(Long taskId) {
        List<AdminAgentTaskRow> rows = jdbcTemplate.query(adminTaskSelectSql() + """
            WHERE task.id = ?
              AND agent.is_deleted = 0
            GROUP BY task.id,
                     task.agent_id,
                     agent.agent_code,
                     agent.agent_name,
                     task.agent_version_id,
                     task.trigger_type,
                     task.context_type,
                     task.context_id,
                     task.triggered_by_type,
                     task.triggered_by_id,
                     task.waiting_deadline_at,
                     task.workflow_snapshot_json,
                     task.execution_trace_json,
                     task.status,
                     task.started_at,
                     task.finished_at,
                     task.error_message,
                     task.created_at,
                     task.updated_at
            LIMIT 1
            """, this::mapAdminTask, taskId);
        if (rows.isEmpty()) {
            throw new BizException(404, "Agent任务不存在");
        }
        return rows.get(0);
    }

    public AdminAgentTaskMetricsRow getAdminAgentTaskMetrics() {
        AdminAgentTaskMetricsRow metrics = jdbcTemplate.queryForObject("""
            SELECT COUNT(task.id) AS total_task_count,
                   COALESCE(SUM(CASE WHEN task.status = 'PENDING' THEN 1 ELSE 0 END), 0) AS pending_task_count,
                   COALESCE(SUM(CASE WHEN task.status = 'RUNNING' THEN 1 ELSE 0 END), 0) AS running_task_count,
                   COALESCE(SUM(CASE WHEN task.status = 'SUCCEEDED' THEN 1 ELSE 0 END), 0) AS succeeded_task_count,
                   COALESCE(SUM(CASE WHEN task.status = 'FAILED' THEN 1 ELSE 0 END), 0) AS failed_task_count,
                   COALESCE(SUM(CASE WHEN task.status = 'WAITING_HUMAN_CONFIRM' THEN 1 ELSE 0 END), 0) AS waiting_human_confirm_task_count,
                   COALESCE(SUM(CASE WHEN task.status = 'REJECTED' THEN 1 ELSE 0 END), 0) AS rejected_task_count,
                   COALESCE(SUM(CASE WHEN task.status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelled_task_count,
                   COALESCE((
                       SELECT COUNT(*)
                       FROM agt_tool_call_log log
                       JOIN agt_task log_task ON log_task.id = log.task_id
                       JOIN agt_agent log_agent ON log_agent.id = log_task.agent_id
                       WHERE log_agent.is_deleted = 0
                   ), 0) AS total_tool_call_count,
                   COALESCE((
                       SELECT COUNT(*)
                       FROM agt_tool_call_log log
                       JOIN agt_task log_task ON log_task.id = log.task_id
                       JOIN agt_agent log_agent ON log_agent.id = log_task.agent_id
                       WHERE log.result_status = 'FAILED'
                         AND log_agent.is_deleted = 0
                   ), 0) AS failed_tool_call_count,
                   CAST(COALESCE(AVG(CASE
                       WHEN task.started_at IS NOT NULL AND task.finished_at IS NOT NULL
                       THEN TIMESTAMPDIFF(MICROSECOND, task.started_at, task.finished_at) / 1000
                       ELSE NULL
                   END), 0) AS SIGNED) AS average_finished_duration_millis,
                   MAX(task.created_at) AS last_task_created_at
            FROM agt_task task
            JOIN agt_agent agent ON agent.id = task.agent_id
            WHERE agent.is_deleted = 0
            """, this::mapAdminTaskMetrics);
        return metrics == null ? new AdminAgentTaskMetricsRow(
            0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, null
        ) : metrics;
    }

    public List<AdminAgentTaskFailureSummaryRow> listAdminAgentTaskFailureSummary() {
        return jdbcTemplate.query("""
            SELECT COALESCE(NULLIF(task.error_message, ''), 'UNKNOWN') AS error_message,
                   COUNT(DISTINCT task.id) AS task_count,
                   COUNT(log.id) AS tool_call_count,
                   MIN(COALESCE(task.finished_at, task.updated_at, task.created_at)) AS first_occurred_at,
                   MAX(COALESCE(task.finished_at, task.updated_at, task.created_at)) AS last_occurred_at
            FROM agt_task task
            JOIN agt_agent agent ON agent.id = task.agent_id
            LEFT JOIN agt_tool_call_log log ON log.task_id = task.id
                                             AND log.result_status = 'FAILED'
            WHERE task.status = 'FAILED'
              AND agent.is_deleted = 0
            GROUP BY COALESCE(NULLIF(task.error_message, ''), 'UNKNOWN')
            ORDER BY task_count DESC, last_occurred_at DESC, error_message ASC
            LIMIT 20
            """, this::mapAdminTaskFailureSummary);
    }

    public List<AdminAgentTaskToolCallRow> listAdminAgentTaskToolCalls(Long taskId) {
        return jdbcTemplate.query("""
            SELECT log.id,
                   log.task_id,
                   log.tool_id,
                   tool.tool_code,
                   tool.tool_name,
                   tool.tool_type,
                   tool.risk_level,
                   tool.read_only_flag,
                   log.request_payload_json,
                   log.response_payload_json,
                   log.approval_status,
                   log.result_status,
                   log.called_at
            FROM agt_tool_call_log log
            LEFT JOIN agt_tool tool ON tool.id = log.tool_id
            WHERE log.task_id = ?
            ORDER BY log.called_at ASC, log.id ASC
            """, this::mapAdminTaskToolCall, taskId);
    }

    public List<AdminAgentTaskAuditRow> listAdminAgentTaskAudits(Long taskId) {
        return jdbcTemplate.query("""
            SELECT id,
                   task_id,
                   action_type,
                   target_type,
                   target_id,
                   decision_summary,
                   risk_flag,
                   occurred_at
            FROM agt_audit_log
            WHERE task_id = ?
            ORDER BY occurred_at ASC, id ASC
            """, this::mapAdminTaskAudit, taskId);
    }

    private boolean exists(String sql, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        return count != null && count > 0;
    }

    private String agentSelectSql() {
        return """
            SELECT aa.id, aa.agent_code, aa.agent_name, aa.agent_type, aa.identity_mode,
                   aa.published_version_id, pv.version_no AS published_version_no, pv.version_label AS published_version_label,
                   aa.status, aa.created_at, aa.updated_at
            FROM agt_agent aa
            LEFT JOIN agt_agent_version pv ON pv.id = aa.published_version_id
            """;
    }

    private String agentWhereSql(String keyword, List<Object> params) {
        StringBuilder where = new StringBuilder("""
            WHERE aa.is_deleted = 0
            """);
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            where.append("""
              AND (
                  aa.agent_name LIKE ?
                  OR aa.agent_code LIKE ?
                  OR aa.agent_type LIKE ?
              )
            """);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        return where.toString();
    }

    private String versionSelectSql() {
        return """
            SELECT id, agent_id, version_no, version_label, model_config_json, system_prompt, workflow_config_json,
                   publish_status, workflow_test_signature, workflow_tested_at, published_at, created_at, updated_at
            FROM agt_agent_version
            """;
    }

    private String toolSelectSql() {
        return """
            SELECT id, tool_code, tool_name, tool_type, connector_id, risk_level,
                   read_only_flag, status, created_at, updated_at
            FROM agt_tool
            """;
    }

    private String bindingSelectSql() {
        return """
            SELECT aat.id,
                   aat.agent_version_id,
                   aat.tool_id,
                   at.tool_code,
                   at.tool_name,
                   at.tool_type,
                   at.risk_level,
                   at.read_only_flag,
                   aat.approval_mode,
                   aat.status,
                   aat.created_at,
                   aat.updated_at
            FROM agt_agent_tool aat
            JOIN agt_tool at ON at.id = aat.tool_id
            """;
    }

    private String taskSelectSql() {
        return """
            SELECT id, agent_id, agent_version_id, trigger_type, context_type, context_id,
                   triggered_by_type, triggered_by_id, waiting_deadline_at,
                   workflow_snapshot_json, execution_trace_json, status,
                   started_at, finished_at, error_message, created_at, updated_at
            FROM agt_task
            """;
    }

    private String adminTaskSelectSql() {
        return """
            SELECT task.id,
                   task.agent_id,
                   agent.agent_code,
                   agent.agent_name,
                   task.agent_version_id,
                   task.trigger_type,
                   task.context_type,
                   task.context_id,
                   task.triggered_by_type,
                   task.triggered_by_id,
                   task.waiting_deadline_at,
                   task.workflow_snapshot_json,
                   task.execution_trace_json,
                   task.status,
                   task.started_at,
                   task.finished_at,
                   task.error_message,
                   task.created_at,
                   task.updated_at,
                   COUNT(log.id) AS tool_call_count
            FROM agt_task task
            JOIN agt_agent agent ON agent.id = task.agent_id
            LEFT JOIN agt_tool_call_log log ON log.task_id = task.id
            """;
    }

    private String adminTaskWhereSql(AgentTaskQueryCriteria criteria, List<Object> params) {
        StringBuilder where = new StringBuilder("""
            WHERE agent.is_deleted = 0
            """);
        if (criteria.status() != null) {
            where.append("  AND task.status = ?\n");
            params.add(criteria.status());
        }
        if (criteria.agentId() != null) {
            where.append("  AND task.agent_id = ?\n");
            params.add(criteria.agentId());
        }
        if (criteria.triggeredByType() != null) {
            where.append("  AND task.triggered_by_type = ?\n");
            params.add(criteria.triggeredByType());
        }
        if (criteria.triggeredById() != null) {
            where.append("  AND task.triggered_by_id = ?\n");
            params.add(criteria.triggeredById());
        }
        if (criteria.contextType() != null) {
            where.append("  AND task.context_type = ?\n");
            params.add(criteria.contextType());
        }
        if (criteria.createdFrom() != null) {
            where.append("  AND task.created_at >= ?\n");
            params.add(criteria.createdFrom());
        }
        if (criteria.createdTo() != null) {
            where.append("  AND task.created_at <= ?\n");
            params.add(criteria.createdTo());
        }
        return where.toString();
    }

    private AgentRow mapAgent(ResultSet rs, int rowNum) throws SQLException {
        return new AgentRow(
            rs.getLong("id"),
            rs.getString("agent_code"),
            rs.getString("agent_name"),
            rs.getString("agent_type"),
            rs.getString("identity_mode"),
            rs.getObject("published_version_id", Long.class),
            rs.getObject("published_version_no", Integer.class),
            rs.getString("published_version_label"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private AgentVersionRow mapVersion(ResultSet rs, int rowNum) throws SQLException {
        return new AgentVersionRow(
            rs.getLong("id"),
            rs.getLong("agent_id"),
            rs.getInt("version_no"),
            rs.getString("version_label"),
            rs.getString("model_config_json"),
            rs.getString("system_prompt"),
            rs.getString("workflow_config_json"),
            rs.getString("publish_status"),
            rs.getString("workflow_test_signature"),
            rs.getTimestamp("workflow_tested_at") == null ? null : rs.getTimestamp("workflow_tested_at").toLocalDateTime(),
            rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toLocalDateTime(),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private AgentToolRow mapTool(ResultSet rs, int rowNum) throws SQLException {
        return new AgentToolRow(
            rs.getLong("id"),
            rs.getString("tool_code"),
            rs.getString("tool_name"),
            rs.getString("tool_type"),
            rs.getObject("connector_id", Long.class),
            rs.getString("risk_level"),
            rs.getInt("read_only_flag") == 1,
            rs.getString("status"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private AgentToolBindingRow mapBinding(ResultSet rs, int rowNum) throws SQLException {
        return new AgentToolBindingRow(
            rs.getLong("id"),
            rs.getLong("agent_version_id"),
            rs.getLong("tool_id"),
            rs.getString("tool_code"),
            rs.getString("tool_name"),
            rs.getString("tool_type"),
            rs.getString("risk_level"),
            rs.getInt("read_only_flag") == 1,
            rs.getString("approval_mode"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private AgentAclRow mapAcl(ResultSet rs, int rowNum) throws SQLException {
        return new AgentAclRow(
            rs.getLong("id"),
            rs.getLong("agent_id"),
            rs.getString("subject_type"),
            rs.getLong("subject_id"),
            rs.getString("perm_code"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private AgentTaskRow mapTask(ResultSet rs, int rowNum) throws SQLException {
        return new AgentTaskRow(
            rs.getLong("id"),
            rs.getLong("agent_id"),
            rs.getLong("agent_version_id"),
            rs.getString("trigger_type"),
            rs.getString("context_type"),
            rs.getObject("context_id", Long.class),
            rs.getString("triggered_by_type"),
            rs.getObject("triggered_by_id", Long.class),
            rs.getTimestamp("waiting_deadline_at") == null ? null : rs.getTimestamp("waiting_deadline_at").toLocalDateTime(),
            rs.getString("workflow_snapshot_json"),
            rs.getString("execution_trace_json"),
            rs.getString("status"),
            rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toLocalDateTime(),
            rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toLocalDateTime(),
            rs.getString("error_message"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private AdminAgentTaskRow mapAdminTask(ResultSet rs, int rowNum) throws SQLException {
        return new AdminAgentTaskRow(
            rs.getLong("id"),
            rs.getLong("agent_id"),
            rs.getString("agent_code"),
            rs.getString("agent_name"),
            rs.getLong("agent_version_id"),
            rs.getString("trigger_type"),
            rs.getString("context_type"),
            rs.getObject("context_id", Long.class),
            rs.getString("triggered_by_type"),
            rs.getObject("triggered_by_id", Long.class),
            rs.getString("status"),
            rs.getInt("tool_call_count"),
            rs.getTimestamp("waiting_deadline_at") == null ? null : rs.getTimestamp("waiting_deadline_at").toLocalDateTime(),
            rs.getString("workflow_snapshot_json"),
            rs.getString("execution_trace_json"),
            rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toLocalDateTime(),
            rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toLocalDateTime(),
            rs.getString("error_message"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private AdminAgentTaskMetricsRow mapAdminTaskMetrics(ResultSet rs, int rowNum) throws SQLException {
        return new AdminAgentTaskMetricsRow(
            rs.getLong("total_task_count"),
            rs.getLong("pending_task_count"),
            rs.getLong("running_task_count"),
            rs.getLong("succeeded_task_count"),
            rs.getLong("failed_task_count"),
            rs.getLong("waiting_human_confirm_task_count"),
            rs.getLong("rejected_task_count"),
            rs.getLong("cancelled_task_count"),
            rs.getLong("total_tool_call_count"),
            rs.getLong("failed_tool_call_count"),
            rs.getLong("average_finished_duration_millis"),
            rs.getTimestamp("last_task_created_at") == null ? null : rs.getTimestamp("last_task_created_at").toLocalDateTime()
        );
    }

    private AdminAgentTaskFailureSummaryRow mapAdminTaskFailureSummary(ResultSet rs, int rowNum) throws SQLException {
        return new AdminAgentTaskFailureSummaryRow(
            rs.getString("error_message"),
            rs.getLong("task_count"),
            rs.getLong("tool_call_count"),
            rs.getTimestamp("first_occurred_at") == null ? null : rs.getTimestamp("first_occurred_at").toLocalDateTime(),
            rs.getTimestamp("last_occurred_at") == null ? null : rs.getTimestamp("last_occurred_at").toLocalDateTime()
        );
    }

    private AdminAgentTaskToolCallRow mapAdminTaskToolCall(ResultSet rs, int rowNum) throws SQLException {
        return new AdminAgentTaskToolCallRow(
            rs.getLong("id"),
            rs.getLong("task_id"),
            rs.getLong("tool_id"),
            rs.getString("tool_code"),
            rs.getString("tool_name"),
            rs.getString("tool_type"),
            rs.getString("risk_level"),
            rs.getInt("read_only_flag") == 1,
            rs.getString("request_payload_json"),
            rs.getString("response_payload_json"),
            rs.getString("approval_status"),
            rs.getString("result_status"),
            rs.getTimestamp("called_at").toLocalDateTime()
        );
    }

    private AdminAgentTaskAuditRow mapAdminTaskAudit(ResultSet rs, int rowNum) throws SQLException {
        return new AdminAgentTaskAuditRow(
            rs.getLong("id"),
            rs.getLong("task_id"),
            rs.getString("action_type"),
            rs.getString("target_type"),
            rs.getObject("target_id", Long.class),
            rs.getString("decision_summary"),
            rs.getInt("risk_flag") == 1,
            rs.getTimestamp("occurred_at").toLocalDateTime()
        );
    }

    private AgentPendingToolCallRow mapPendingToolCall(ResultSet rs, int rowNum) throws SQLException {
        return new AgentPendingToolCallRow(
            rs.getLong("id"),
            rs.getLong("task_id"),
            rs.getLong("tool_id"),
            rs.getString("request_payload_json"),
            rs.getString("tool_code"),
            rs.getString("tool_name"),
            rs.getString("tool_type"),
            rs.getString("risk_level"),
            rs.getInt("read_only_flag") == 1,
            rs.getString("approval_mode")
        );
    }

    // ==================== LLM Model ====================

    public List<LlmModelRow> listLlmModels() {
        return jdbcTemplate.query(llmModelSelectSql() + " WHERE is_deleted = 0 ORDER BY created_at DESC", this::mapLlmModel);
    }

    public LlmModelRow getLlmModel(Long modelId) {
        List<LlmModelRow> list = jdbcTemplate.query(llmModelSelectSql() + " WHERE id = ? AND is_deleted = 0", this::mapLlmModel, modelId);
        return list.isEmpty() ? null : list.get(0);
    }

    public void insertLlmModel(Long modelId, String provider, String modelName, String displayName, String endpoint, String apiKey, String status, Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO agt_llm_model (id, provider, model_name, display_name, endpoint, api_key, status, created_by, updated_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, modelId, provider, modelName, displayName, endpoint, apiKey, status, operatorId, operatorId);
    }

    public void updateLlmModel(Long modelId, String provider, String modelName, String displayName, String endpoint, String apiKey, String status, Long operatorId) {
        jdbcTemplate.update("""
            UPDATE agt_llm_model
            SET provider = ?, model_name = ?, display_name = ?, endpoint = ?, api_key = ?, status = ?, updated_by = ?
            WHERE id = ? AND is_deleted = 0
            """, provider, modelName, displayName, endpoint, apiKey, status, operatorId, modelId);
    }

    public void deleteLlmModel(Long modelId, Long operatorId) {
        jdbcTemplate.update("UPDATE agt_llm_model SET is_deleted = 1, updated_by = ? WHERE id = ?", operatorId, modelId);
    }

    private String llmModelSelectSql() {
        return """
            SELECT id, provider, model_name, display_name, endpoint, api_key, status, created_at, updated_at
            FROM agt_llm_model
            """;
    }

    private LlmModelRow mapLlmModel(ResultSet rs, int rowNum) throws SQLException {
        return new LlmModelRow(
            rs.getLong("id"),
            rs.getString("provider"),
            rs.getString("model_name"),
            rs.getString("display_name"),
            rs.getString("endpoint"),
            rs.getString("api_key"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }
}
