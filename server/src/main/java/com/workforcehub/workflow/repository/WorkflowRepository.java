package com.workforcehub.workflow.repository;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.workflow.dto.LeaveRequestCreateRequest;
import com.workforcehub.workflow.dto.WorkflowRequestListQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * module-workflow 第一版 JDBC 仓储。
 */
@Repository
public class WorkflowRepository {

    private final JdbcTemplate jdbcTemplate;

    public WorkflowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public WorkflowTemplateVersionRow getPublishedTemplateVersion(String processCode) {
        List<WorkflowTemplateVersionRow> rows = jdbcTemplate.query("""
            SELECT wtv.id, wtv.flowable_definition_key
            FROM wf_process_type wpt
            JOIN wf_template wt ON wt.process_type_id = wpt.id
            JOIN wf_template_version wtv ON wtv.template_id = wt.id
            WHERE wpt.process_code = ?
              AND wpt.status = 'ACTIVE'
              AND wpt.is_deleted = 0
              AND wt.status = 'ACTIVE'
              AND wt.is_deleted = 0
              AND wtv.status = 'PUBLISHED'
              AND wtv.effective_from <= NOW()
              AND (wtv.effective_to IS NULL OR wtv.effective_to > NOW())
            ORDER BY wtv.version_no DESC, wtv.id DESC
            LIMIT 1
            """,
            (rs, rowNum) -> new WorkflowTemplateVersionRow(
                rs.getLong("id"),
                rs.getString("flowable_definition_key")
            ),
            processCode
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "未找到已发布的流程模板");
        }
        return rows.get(0);
    }

    public boolean existsActiveLeaveType(Long leaveTypeId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM lv_type
            WHERE id = ? AND status = 'ACTIVE' AND is_deleted = 0
            """, Long.class, leaveTypeId);
        return count != null && count > 0;
    }

    public void insertWorkflowRequest(Long requestId,
                                      String requestNo,
                                      Long leaveDetailId,
                                      Long applicantUserId,
                                      Long applicantDeptId,
                                      Long applicantPositionId,
                                      String title,
                                      Long templateVersionId) {
        jdbcTemplate.update("""
            INSERT INTO wf_request
                (id, request_no, biz_type, biz_id, applicant_user_id, applicant_dept_id,
                 applicant_position_id, title, template_version_id, status,
                 current_step_key, current_step_name, submitted_at, created_by, updated_by)
            VALUES
                (?, ?, 'LEAVE', ?, ?, ?, ?, ?, ?, 'IN_APPROVAL',
                 'manager_approval', '直属上级审批', NOW(), ?, ?)
            """,
            requestId,
            requestNo,
            leaveDetailId,
            applicantUserId,
            applicantDeptId,
            applicantPositionId,
            title,
            templateVersionId,
            applicantUserId,
            applicantUserId
        );
    }

    public void insertLeaveDetail(Long detailId, Long requestId, LeaveRequestCreateRequest request) {
        jdbcTemplate.update("""
            INSERT INTO wf_req_leave
                (id, request_id, leave_type_id, start_at, end_at, duration_minutes, reason)
            VALUES
                (?, ?, ?, ?, ?, ?, ?)
            """,
            detailId,
            requestId,
            request.getLeaveTypeId(),
            Timestamp.valueOf(request.getStartAt()),
            Timestamp.valueOf(request.getEndAt()),
            request.getDurationMinutes(),
            request.getReason()
        );
    }

    public void insertRequestAttachment(Long requestId, Long attachmentRefId, int sortOrder, Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO wf_request_attachment
                (id, request_id, attachment_ref_id, sort_order, created_by)
            VALUES
                (?, ?, ?, ?, ?)
            """,
            IdWorker.getId(),
            requestId,
            attachmentRefId,
            sortOrder,
            operatorId
        );
    }

    public Long createWorkflowAttachmentRef(Long requestId,
                                            WorkflowAttachmentRow sourceAttachment,
                                            Long uploaderId) {
        Long workflowAttachmentRefId = IdWorker.getId();
        jdbcTemplate.update("""
            INSERT INTO sys_attachment_ref
                (id, owner_type, owner_id, file_object_id, original_file_name,
                 display_name, uploader_type, uploader_id, visibility_scope, created_by)
            VALUES
                (?, 'WF_REQUEST', ?, ?, ?, ?, 'USER', ?, 'WORKFLOW_PARTICIPANT', ?)
            """,
            workflowAttachmentRefId,
            requestId,
            sourceAttachment.fileObjectId(),
            sourceAttachment.displayName(),
            sourceAttachment.displayName(),
            uploaderId,
            uploaderId
        );
        jdbcTemplate.update("""
            UPDATE sys_file_object
            SET ref_count = ref_count + 1
            WHERE id = ? AND is_deleted = 0
            """, sourceAttachment.fileObjectId());
        return workflowAttachmentRefId;
    }

    public void updateProcessInstanceId(Long requestId, String processInstanceId) {
        jdbcTemplate.update("""
            UPDATE wf_request
            SET flowable_process_instance_id = ?
            WHERE id = ? AND is_deleted = 0
            """, processInstanceId, requestId);
    }

    public void insertTodo(Long requestId,
                           Long ownerUserId,
                           String taskId,
                           String title,
                           String summary) {
        jdbcTemplate.update("""
            INSERT INTO sys_todo_item
                (id, owner_type, owner_id, source_type, source_id, title, summary,
                 route_path, status, priority)
            VALUES
                (?, 'USER', ?, 'WORKFLOW_TASK', ?, ?, ?, ?, 'PENDING', 'NORMAL')
            """,
            IdWorker.getId(),
            ownerUserId,
            requestId,
            title,
            summary,
            "/workflow/requests/" + requestId + "?taskId=" + taskId
        );
    }

    public List<WorkflowTodoRow> listPendingTodos(Long ownerUserId) {
        return jdbcTemplate.query("""
            SELECT sti.id AS todo_id,
                   sti.source_id AS request_id,
                   art.ID_ AS task_id,
                   sti.title,
                   sti.summary,
                   sti.status,
                   sti.priority,
                   sti.created_at
            FROM sys_todo_item sti
            JOIN wf_request wr ON wr.id = sti.source_id
            JOIN ACT_RU_TASK art ON art.PROC_INST_ID_ = wr.flowable_process_instance_id
                                AND art.ASSIGNEE_ = CAST(sti.owner_id AS CHAR)
            WHERE sti.owner_type = 'USER'
              AND sti.owner_id = ?
              AND sti.source_type = 'WORKFLOW_TASK'
              AND sti.status = 'PENDING'
              AND wr.is_deleted = 0
            ORDER BY sti.created_at DESC, sti.id DESC
            """,
            (rs, rowNum) -> new WorkflowTodoRow(
                rs.getLong("todo_id"),
                rs.getLong("request_id"),
                rs.getString("task_id"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("status"),
                rs.getString("priority"),
                rs.getTimestamp("created_at").toLocalDateTime()
            ),
            ownerUserId
        );
    }

    public long countRequests(Long applicantUserId, WorkflowRequestListQuery query) {
        List<Object> args = new ArrayList<>();
        String whereSql = buildRequestListWhereSql(applicantUserId, query, args);
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM wf_request wr
            """ + whereSql,
            Long.class,
            args.toArray()
        );
        return count == null ? 0L : count;
    }

    public List<WorkflowRequestSummaryRow> pageRequests(Long applicantUserId, WorkflowRequestListQuery query) {
        List<Object> args = new ArrayList<>();
        String whereSql = buildRequestListWhereSql(applicantUserId, query, args);
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
            (rs, rowNum) -> new WorkflowRequestSummaryRow(
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
            ),
            args.toArray()
        );
    }

    public WorkflowRequestRow getRequestByTaskIdOrThrow(String taskId) {
        List<WorkflowRequestRow> rows = jdbcTemplate.query("""
            SELECT wr.id, wr.request_no, wr.status, wr.title, wr.applicant_user_id,
                   wr.flowable_process_instance_id
            FROM wf_request wr
            JOIN ACT_RU_TASK art ON art.PROC_INST_ID_ = wr.flowable_process_instance_id
            WHERE art.ID_ = ?
              AND wr.is_deleted = 0
            LIMIT 1
            """,
            this::mapRequest,
            taskId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "审批任务不存在");
        }
        return rows.get(0);
    }

    public void insertTaskAction(Long requestId,
                                 String taskId,
                                 Long actorId,
                                 String actionType,
                                 String comment,
                                 String beforeStatus,
                                 String afterStatus) {
        jdbcTemplate.update("""
            INSERT INTO wf_task_action
                (id, request_id, flowable_task_id, step_key, step_name, actor_type,
                 actor_id, action_type, action_comment, before_status, after_status)
            VALUES
                (?, ?, ?, 'manager_approval', '直属上级审批', 'USER',
                 ?, ?, ?, ?, ?)
            """,
            IdWorker.getId(),
            requestId,
            taskId,
            actorId,
            actionType,
            comment,
            beforeStatus,
            afterStatus
        );
    }

    public void finishRequest(Long requestId, String status, Long operatorId) {
        jdbcTemplate.update("""
            UPDATE wf_request
            SET status = ?,
                current_step_key = NULL,
                current_step_name = NULL,
                finished_at = ?,
                updated_by = ?
            WHERE id = ? AND is_deleted = 0
            """, status, LocalDateTime.now(), operatorId, requestId);
    }

    public void closePendingTodo(Long requestId, Long ownerUserId) {
        jdbcTemplate.update("""
            UPDATE sys_todo_item
            SET status = 'DONE'
            WHERE source_type = 'WORKFLOW_TASK'
              AND source_id = ?
              AND owner_type = 'USER'
              AND owner_id = ?
              AND status = 'PENDING'
            """, requestId, ownerUserId);
    }

    public void closeAllPendingTodos(Long requestId) {
        jdbcTemplate.update("""
            UPDATE sys_todo_item
            SET status = 'CLOSED'
            WHERE source_type = 'WORKFLOW_TASK'
              AND source_id = ?
              AND status = 'PENDING'
            """, requestId);
    }

    public WorkflowRequestRow getRequestByIdOrThrow(Long requestId) {
        List<WorkflowRequestRow> rows = jdbcTemplate.query("""
            SELECT wr.id, wr.request_no, wr.status, wr.title, wr.applicant_user_id,
                   wr.flowable_process_instance_id
            FROM wf_request wr
            WHERE wr.id = ?
              AND wr.is_deleted = 0
            LIMIT 1
            """,
            this::mapRequest,
            requestId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "审批申请不存在");
        }
        return rows.get(0);
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
            (rs, rowNum) -> new WorkflowRequestDetailRow(
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
            ),
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

    public WorkflowAttachmentRow getAttachmentByIdOrThrow(Long attachmentRefId) {
        List<WorkflowAttachmentRow> rows = jdbcTemplate.query("""
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
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE ar.id = ?
            LIMIT 1
            """,
            this::mapAttachment,
            attachmentRefId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "附件引用不存在");
        }
        return rows.get(0);
    }

    public List<WorkflowTaskActionRow> listTaskActions(Long requestId) {
        return jdbcTemplate.query("""
            SELECT id, request_id, flowable_task_id, step_key, step_name, actor_type,
                   actor_id, action_type, action_comment, before_status, after_status, acted_at
            FROM wf_task_action
            WHERE request_id = ?
            ORDER BY acted_at ASC, id ASC
            """,
            (rs, rowNum) -> new WorkflowTaskActionRow(
                rs.getLong("id"),
                rs.getLong("request_id"),
                rs.getString("flowable_task_id"),
                rs.getString("step_key"),
                rs.getString("step_name"),
                rs.getString("actor_type"),
                rs.getLong("actor_id"),
                rs.getString("action_type"),
                rs.getString("action_comment"),
                rs.getString("before_status"),
                rs.getString("after_status"),
                rs.getTimestamp("acted_at").toLocalDateTime()
            ),
            requestId
        );
    }

    public boolean isRequestParticipant(Long requestId, Long userId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM wf_request wr
            WHERE wr.id = ?
              AND wr.is_deleted = 0
              AND (
                  wr.applicant_user_id = ?
                  OR EXISTS (
                      SELECT 1
                      FROM sys_todo_item sti
                      WHERE sti.source_type = 'WORKFLOW_TASK'
                        AND sti.source_id = wr.id
                        AND sti.owner_type = 'USER'
                        AND sti.owner_id = ?
                  )
                  OR EXISTS (
                      SELECT 1
                      FROM wf_task_action wta
                      WHERE wta.request_id = wr.id
                        AND wta.actor_type = 'USER'
                        AND wta.actor_id = ?
                  )
              )
            """, Long.class, requestId, userId, userId, userId);
        return count != null && count > 0;
    }

    private String buildRequestListWhereSql(Long applicantUserId,
                                            WorkflowRequestListQuery query,
                                            List<Object> args) {
        StringBuilder sql = new StringBuilder("""
            WHERE wr.applicant_user_id = ?
              AND wr.is_deleted = 0
            """);
        args.add(applicantUserId);
        if (StringUtils.hasText(query.getStatus())) {
            sql.append(" AND wr.status = ?\n");
            args.add(query.getStatus().trim());
        }
        if (StringUtils.hasText(query.getBizType())) {
            sql.append(" AND wr.biz_type = ?\n");
            args.add(query.getBizType().trim());
        }
        return sql.toString();
    }

    private WorkflowAttachmentRow mapAttachment(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new WorkflowAttachmentRow(
            rs.getLong("attachment_ref_id"),
            rs.getLong("file_object_id"),
            rs.getString("owner_type"),
            rs.getObject("owner_id", Long.class),
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

    private WorkflowRequestRow mapRequest(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new WorkflowRequestRow(
            rs.getLong("id"),
            rs.getString("request_no"),
            rs.getString("status"),
            rs.getString("title"),
            rs.getLong("applicant_user_id"),
            rs.getString("flowable_process_instance_id")
        );
    }
}
