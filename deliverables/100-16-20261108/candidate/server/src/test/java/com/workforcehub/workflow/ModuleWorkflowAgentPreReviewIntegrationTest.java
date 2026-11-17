package com.workforcehub.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.support.WorkflowAgentPreReviewTestConfiguration;
import com.workforcehub.support.WorkflowAgentPreReviewTestConfiguration.TestWorkflowAgentPreReviewTrigger;
import com.workforcehub.support.WorkflowFlowableIntegrationTestSupport;
import com.workforcehub.support.WorkflowFlowableTestConfiguration;
import com.workforcehub.workflow.service.WorkflowAgentResultCallback;
import jakarta.servlet.http.Cookie;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * module-workflow Agent 预审入口集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
@Import({WorkflowFlowableTestConfiguration.class, WorkflowAgentPreReviewTestConfiguration.class})
class ModuleWorkflowAgentPreReviewIntegrationTest extends WorkflowFlowableIntegrationTestSupport {

    private static final long EMPLOYEE_USER_ID = 2003L;
    private static final long MANAGER_USER_ID = 2002L;
    private static final long AGENT_TASK_ID = 88001L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private WorkflowAgentResultCallback workflowAgentResultCallback;

    @Autowired
    private TestWorkflowAgentPreReviewTrigger agentTrigger;

    @BeforeEach
    void resetAgentTrigger() {
        agentTrigger.reset();
    }

    @Test
    @DisplayName("触发Agent预审后请假申请应先停留在待预审状态且不生成上级待办")
    void leaveRequestShouldWaitForAgentPreReviewBeforeHumanApproval() throws Exception {
        Cookie employeeSession = loginEmployee();
        Long leaveTypeId = seedWorkflowAndLeaveType();

        MvcResult result = mockMvc.perform(post("/api/employee/workflow/leave-requests")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(leaveRequestJson(leaveTypeId, 480, "家中有事请假一天")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
            .andExpect(jsonLong("$.data.agentTaskId", AGENT_TASK_ID))
            .andExpect(jsonPath("$.data.flowableProcessInstanceId").doesNotExist())
            .andReturn();

        long requestId = data(result).get("requestId").asLong();
        assertEquals(requestId, agentTrigger.getLastRequestId());
        assertEquals("LEAVE", agentTrigger.getLastProcessTypeCode());
        assertEquals("LEAVE", agentTrigger.getLastBizType());
        assertEquals(EMPLOYEE_USER_ID, agentTrigger.getLastApplicantUserId());
        assertEquals("PENDING_REVIEW", requestStatus(requestId));
        assertEquals("agent_pre_review", currentStepKey(requestId));
        assertNull(processInstanceId(requestId), "Agent 预审完成前不应启动 Flowable 流程实例");
        assertEquals(0L, pendingTodoCount(requestId, MANAGER_USER_ID));
    }

    @Test
    @DisplayName("通用OA申请应先触发Agent预审再进入直属上级审批")
    void genericRequestShouldWaitForAgentPreReviewBeforeHumanApproval() throws Exception {
        Cookie employeeSession = loginEmployee();
        seedWorkflowDefinitions();

        MvcResult result = mockMvc.perform(post("/api/employee/workflow/generic-requests")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "processCode": "BUSINESS_TRIP",
                      "title": "出差申请",
                      "summary": "客户拜访",
                      "formPayload": {
                        "processKey": "businessTrip",
                        "processTitle": "出差申请",
                        "fields": [
                          {"key":"destination","label":"出差地点","type":"text","value":"上海","displayValue":"上海"},
                          {"key":"reason","label":"出差事由","type":"textarea","value":"客户拜访","displayValue":"客户拜访"}
                        ]
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
            .andExpect(jsonLong("$.data.agentTaskId", AGENT_TASK_ID))
            .andExpect(jsonPath("$.data.flowableProcessInstanceId").doesNotExist())
            .andReturn();

        long requestId = data(result).get("requestId").asLong();
        assertEquals(requestId, agentTrigger.getLastRequestId());
        assertEquals("BUSINESS_TRIP", agentTrigger.getLastProcessTypeCode());
        assertEquals("BUSINESS_TRIP", agentTrigger.getLastBizType());
        assertEquals(EMPLOYEE_USER_ID, agentTrigger.getLastApplicantUserId());
        assertEquals("PENDING_REVIEW", requestStatus(requestId));
        assertEquals("agent_pre_review", currentStepKey(requestId));
        assertNull(processInstanceId(requestId), "Agent 预审完成前不应启动 Flowable 流程实例");
        assertEquals(0L, pendingTodoCount(requestId, MANAGER_USER_ID));
    }

    @Test
    @DisplayName("Agent预审通过回调后应启动Flowable并进入直属上级审批")
    void agentApprovedCallbackShouldStartHumanApproval() throws Exception {
        Cookie employeeSession = loginEmployee();
        Cookie managerSession = loginManager();
        Long leaveTypeId = seedWorkflowAndLeaveType();
        long requestId = submitLeaveRequest(employeeSession, leaveTypeId, 480, "家中有事请假一天");

        workflowAgentResultCallback.onPreReviewApproved(
            requestId,
            AGENT_TASK_ID,
            "{\"decision\":\"APPROVED\",\"reason\":\"规则校验通过\"}"
        );

        String processInstanceId = processInstanceId(requestId);
        assertNotNull(processInstanceId, "Agent 预审通过后应启动 Flowable 流程实例");
        Task managerTask = taskService.createTaskQuery()
            .processInstanceId(processInstanceId)
            .taskAssignee(String.valueOf(MANAGER_USER_ID))
            .singleResult();
        assertNotNull(managerTask, "Agent 预审通过后直属上级应收到审批任务");
        assertEquals("IN_APPROVAL", requestStatus(requestId));
        assertEquals(1L, actionCount(requestId, "AGENT_PRE_REVIEW", "IN_APPROVAL"));
        assertEquals(1L, pendingTodoCount(requestId, MANAGER_USER_ID));

        mockMvc.perform(get("/api/employee/workflow/todos")
                .cookie(managerSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data[0].requestId", requestId))
            .andExpect(jsonPath("$.data[0].taskId").value(managerTask.getId()));
    }

    @Test
    @DisplayName("Agent预审驳回回调后应直接终止申请且不启动Flowable")
    void agentRejectedCallbackShouldRejectRequestWithoutHumanTask() throws Exception {
        Cookie employeeSession = loginEmployee();
        Long leaveTypeId = seedWorkflowAndLeaveType();
        long requestId = submitLeaveRequest(employeeSession, leaveTypeId, 480, "请假理由不完整");

        workflowAgentResultCallback.onPreReviewRejected(
            requestId,
            AGENT_TASK_ID,
            "{\"decision\":\"REJECTED\",\"reason\":\"缺少证明材料\"}"
        );

        assertEquals("REJECTED", requestStatus(requestId));
        assertNull(processInstanceId(requestId), "Agent 预审驳回不应启动 Flowable 流程实例");
        assertEquals(0L, pendingTodoCount(requestId, MANAGER_USER_ID));
        assertEquals(1L, actionCount(requestId, "AGENT_PRE_REVIEW", "REJECTED"));
        assertEquals(9600, availableMinutes(leaveTypeId));
        assertEquals(0, frozenMinutes(leaveTypeId));

        mockMvc.perform(get("/api/employee/workflow/requests/{requestId}/actions", requestId)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].actorType").value("AGENT"))
            .andExpect(jsonLong("$.data[0].actorId", AGENT_TASK_ID))
            .andExpect(jsonPath("$.data[0].actionType").value("AGENT_PRE_REVIEW"))
            .andExpect(jsonPath("$.data[0].afterStatus").value("REJECTED"))
            .andExpect(jsonPath("$.data[0].agentDecisionJson").value("{\"decision\":\"REJECTED\",\"reason\":\"缺少证明材料\"}"));
    }

    @Test
    @DisplayName("Agent预审超时回调后应终止申请且不启动Flowable")
    void agentTimeoutCallbackShouldRejectRequestWithoutHumanTask() throws Exception {
        Cookie employeeSession = loginEmployee();
        Long leaveTypeId = seedWorkflowAndLeaveType();
        long requestId = submitLeaveRequest(employeeSession, leaveTypeId, 480, "等待预审超时");

        workflowAgentResultCallback.onPreReviewTimeout(
            requestId,
            AGENT_TASK_ID,
            "{\"decision\":\"TIMEOUT\",\"status\":\"CANCELLED\",\"reason\":\"人工确认超时，任务已取消\"}"
        );

        assertEquals("REJECTED", requestStatus(requestId));
        assertNull(processInstanceId(requestId), "Agent 预审超时不应启动 Flowable 流程实例");
        assertEquals(0L, pendingTodoCount(requestId, MANAGER_USER_ID));
        assertEquals(1L, actionCount(requestId, "AGENT_PRE_REVIEW", "REJECTED"));
        assertEquals(9600, availableMinutes(leaveTypeId));
        assertEquals(0, frozenMinutes(leaveTypeId));

        mockMvc.perform(get("/api/employee/workflow/requests/{requestId}/actions", requestId)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].actorType").value("AGENT"))
            .andExpect(jsonLong("$.data[0].actorId", AGENT_TASK_ID))
            .andExpect(jsonPath("$.data[0].actionType").value("AGENT_PRE_REVIEW"))
            .andExpect(jsonPath("$.data[0].afterStatus").value("REJECTED"))
            .andExpect(jsonPath("$.data[0].agentDecisionJson")
                .value("{\"decision\":\"TIMEOUT\",\"reason\":\"人工确认超时，任务已取消\",\"status\":\"CANCELLED\"}"));
    }

    @Test
    @DisplayName("Agent预审回调必须匹配提交时生成的Agent任务ID")
    void agentCallbackShouldMatchPendingAgentTaskId() throws Exception {
        Cookie employeeSession = loginEmployee();
        Long leaveTypeId = seedWorkflowAndLeaveType();
        long requestId = submitLeaveRequest(employeeSession, leaveTypeId, 480, "家中有事请假一天");

        BizException exception = assertThrows(
            BizException.class,
            () -> workflowAgentResultCallback.onPreReviewRejected(
                requestId,
                99001L,
                "{\"decision\":\"REJECTED\",\"reason\":\"错误任务回调\"}"
            )
        );

        assertEquals("AI预审任务不匹配", exception.getMessage());
        assertEquals("PENDING_REVIEW", requestStatus(requestId));
        assertNull(processInstanceId(requestId), "错误任务回调不能启动或终止 Flowable 流程");
        assertEquals(0L, actionCount(requestId, "AGENT_PRE_REVIEW", "REJECTED"));
    }

    @Test
    @DisplayName("Agent预审回调任务ID为空时应按任务不匹配处理")
    void agentCallbackShouldRejectNullAgentTaskId() throws Exception {
        Cookie employeeSession = loginEmployee();
        Long leaveTypeId = seedWorkflowAndLeaveType();
        long requestId = submitLeaveRequest(employeeSession, leaveTypeId, 480, "家中有事请假一天");

        BizException exception = assertThrows(
            BizException.class,
            () -> workflowAgentResultCallback.onPreReviewRejected(
                requestId,
                null,
                "{\"decision\":\"REJECTED\",\"reason\":\"空任务回调\"}"
            )
        );

        assertEquals("AI预审任务不匹配", exception.getMessage());
        assertEquals("PENDING_REVIEW", requestStatus(requestId));
        assertNull(processInstanceId(requestId), "空任务回调不能启动或终止 Flowable 流程");
        assertEquals(0L, actionCount(requestId, "AGENT_PRE_REVIEW", "REJECTED"));
    }

    @Test
    @DisplayName("没有Agent触发器任务时应保持原直属上级审批闭环")
    void leaveRequestShouldKeepHumanApprovalWhenAgentTriggerReturnsNull() throws Exception {
        agentTrigger.disable();
        Cookie employeeSession = loginEmployee();
        Long leaveTypeId = seedWorkflowAndLeaveType();

        MvcResult result = mockMvc.perform(post("/api/employee/workflow/leave-requests")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(leaveRequestJson(leaveTypeId, 480, "家中有事请假一天")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("IN_APPROVAL"))
            .andExpect(jsonPath("$.data.flowableProcessInstanceId").isString())
            .andExpect(jsonPath("$.data.agentTaskId").doesNotExist())
            .andReturn();

        long requestId = data(result).get("requestId").asLong();
        assertEquals("IN_APPROVAL", requestStatus(requestId));
        assertNotNull(processInstanceId(requestId));
        assertEquals(1L, pendingTodoCount(requestId, MANAGER_USER_ID));
        assertNull(agentTrigger.getLastRequestId(), "触发器关闭时不应记录 Agent 预审任务上下文");
    }

    @Test
    @DisplayName("Agent预审通过回调并发重复投递时不能重复启动Flowable")
    void concurrentAgentApprovedCallbacksShouldNotStartDuplicateFlowableInstances() throws Exception {
        Cookie employeeSession = loginEmployee();
        Long leaveTypeId = seedWorkflowAndLeaveType();
        long requestId = submitLeaveRequest(employeeSession, leaveTypeId, 480, "家中有事请假一天");

        try (Connection lockConnection = dataSource.getConnection()) {
            lockConnection.setAutoCommit(false);
            try (PreparedStatement statement = lockConnection.prepareStatement(
                    "SELECT id FROM wf_request WHERE id = ? FOR UPDATE")) {
                statement.setLong(1, requestId);
                statement.executeQuery();

                ExecutorService executor = Executors.newFixedThreadPool(2);
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                try {
                    Future<Integer> first = executor.submit(
                        () -> approvedCallbackCode(requestId, "第一次回调", ready, start)
                    );
                    Future<Integer> second = executor.submit(
                        () -> approvedCallbackCode(requestId, "重复回调", ready, start)
                    );
                    ready.await();
                    start.countDown();
                    Thread.sleep(200L);
                    lockConnection.commit();

                    List<Integer> resultCodes = List.of(first.get(), second.get());
                    long successCount = resultCodes.stream().filter(code -> code == 0).count();
                    long rejectedCount = resultCodes.stream().filter(code -> code == 100400).count();
                    assertEquals(1L, successCount, () -> "resultCodes=" + resultCodes);
                    assertEquals(1L, rejectedCount, () -> "resultCodes=" + resultCodes);
                } finally {
                    executor.shutdownNow();
                }
            }
        }

        List<ProcessInstance> instances = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(String.valueOf(requestId))
            .list();
        assertEquals(1, instances.size(), "重复 Agent 回调不能为同一申请创建多个 Flowable 实例");
        assertEquals(1L, pendingTodoCount(requestId, MANAGER_USER_ID));
        assertEquals(1L, actionCount(requestId, "AGENT_PRE_REVIEW", "IN_APPROVAL"));
    }

    private int approvedCallbackCode(Long requestId,
                                     String reason,
                                     CountDownLatch ready,
                                     CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            workflowAgentResultCallback.onPreReviewApproved(
                requestId,
                AGENT_TASK_ID,
                "{\"decision\":\"APPROVED\",\"reason\":\"%s\"}".formatted(reason)
            );
            return 0;
        } catch (BizException ex) {
            return ex.getCode();
        }
    }

    private long submitLeaveRequest(Cookie employeeSession, Long leaveTypeId, int durationMinutes, String reason)
        throws Exception {
        MvcResult result = mockMvc.perform(post("/api/employee/workflow/leave-requests")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(leaveRequestJson(leaveTypeId, durationMinutes, reason)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.requestId").isString())
            .andReturn();
        return data(result).get("requestId").asLong();
    }

    private Long seedWorkflowAndLeaveType() {
        seedWorkflowDefinitions();
        return seedAnnualLeaveType();
    }

    private void seedWorkflowDefinitions() {
        jdbcTemplate.update("""
            INSERT INTO wf_process_type
                (id, process_code, process_name, biz_type, status)
            VALUES
                (9101, 'LEAVE', '请假流程', 'LEAVE', 'ACTIVE'),
                (9102, 'BUSINESS_TRIP', '出差申请流程', 'BUSINESS_TRIP', 'ACTIVE')
            """);
        jdbcTemplate.update("""
            INSERT INTO wf_template
                (id, process_type_id, template_code, template_name, status)
            VALUES
                (9201, 9101, 'LEAVE_MANAGER_APPROVAL', '请假直属上级审批', 'ACTIVE'),
                (9202, 9102, 'BUSINESS_TRIP_MANAGER_APPROVAL', '出差申请直属上级审批', 'ACTIVE')
            """);
        jdbcTemplate.update("""
            INSERT INTO wf_template_version
                (id, template_id, version_no, flowable_definition_key, status, published_by, published_at, effective_from,
                 rule_snapshot_json)
            VALUES
                (9301, 9201, 1, 'leave_manager_approval', 'PUBLISHED', 8001, NOW(), DATE_SUB(NOW(), INTERVAL 1 DAY),
                 CAST('{"preReviewEnabled":true,"maxDurationMinutes":480}' AS JSON)),
                (9302, 9202, 1, 'business_trip_manager_approval', 'PUBLISHED', 8001, NOW(), DATE_SUB(NOW(), INTERVAL 1 DAY),
                 CAST('{"preReview":{"mode":"KNOWLEDGE_BASE","required":true}}' AS JSON))
            """);
    }

    private Long seedAnnualLeaveType() {
        long leaveTypeId = 9401L;
        jdbcTemplate.update("""
            INSERT INTO lv_type
                (id, leave_code, leave_name, balance_mode, status)
            VALUES
                (?, 'ANNUAL_LEAVE', '年假', 'DEDUCT', 'ACTIVE')
            """, leaveTypeId);
        jdbcTemplate.update("""
            INSERT INTO lv_account
                (id, user_id, leave_type_id, available_minutes, frozen_minutes, version, status)
            VALUES
                (9402, ?, ?, 9600, 0, 0, 'ACTIVE')
            """, EMPLOYEE_USER_ID, leaveTypeId);
        jdbcTemplate.update("""
            INSERT INTO lv_account_txn
                (id, account_id, txn_type, change_minutes, balance_after,
                 source_type, source_id, source_txn_key, effective_at)
            VALUES
                (9403, 9402, 'ADJUST', 9600, 9600,
                 'ADMIN', 8001, 'workflow-agent-seed-leave-balance', NOW())
            """);
        return leaveTypeId;
    }

    private String leaveRequestJson(Long leaveTypeId, int durationMinutes, String reason) {
        return """
            {
              "leaveTypeId": %d,
              "startAt": "2026-05-07 09:00:00",
              "endAt": "2026-05-07 18:00:00",
              "durationMinutes": %d,
              "reason": "%s"
            }
            """.formatted(leaveTypeId, durationMinutes, reason);
    }

    private Cookie loginEmployee() throws Exception {
        return login("/api/employee/auth/login", "zhangsan.employee", "Employee@123456");
    }

    private Cookie loginManager() throws Exception {
        return login("/api/employee/auth/login", "lisi.manager", "Employee@123456");
    }

    private Cookie login(String path, String loginName, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "%s"
                    }
                    """.formatted(loginName, password)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(cookie().exists("SESSION"))
            .andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private String requestStatus(long requestId) {
        return jdbcTemplate.queryForObject("SELECT status FROM wf_request WHERE id = ?", String.class, requestId);
    }

    private String currentStepKey(long requestId) {
        return jdbcTemplate.queryForObject("SELECT current_step_key FROM wf_request WHERE id = ?", String.class, requestId);
    }

    private String processInstanceId(long requestId) {
        return jdbcTemplate.queryForObject(
            "SELECT flowable_process_instance_id FROM wf_request WHERE id = ?",
            String.class,
            requestId
        );
    }

    private long pendingTodoCount(long requestId, long ownerUserId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_todo_item
            WHERE source_type = 'WORKFLOW_TASK'
              AND source_id = ?
              AND owner_type = 'USER'
              AND owner_id = ?
              AND status = 'PENDING'
            """, Long.class, requestId, ownerUserId);
        return count == null ? 0L : count;
    }

    private long actionCount(long requestId, String actionType, String afterStatus) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM wf_task_action
            WHERE request_id = ?
              AND action_type = ?
              AND after_status = ?
            """, Long.class, requestId, actionType, afterStatus);
        return count == null ? 0L : count;
    }

    private int availableMinutes(long leaveTypeId) {
        Integer minutes = jdbcTemplate.queryForObject("""
            SELECT available_minutes
            FROM lv_account
            WHERE user_id = ? AND leave_type_id = ?
            """, Integer.class, EMPLOYEE_USER_ID, leaveTypeId);
        return minutes == null ? 0 : minutes;
    }

    private int frozenMinutes(long leaveTypeId) {
        Integer minutes = jdbcTemplate.queryForObject("""
            SELECT frozen_minutes
            FROM lv_account
            WHERE user_id = ? AND leave_type_id = ?
            """, Integer.class, EMPLOYEE_USER_ID, leaveTypeId);
        return minutes == null ? 0 : minutes;
    }
}
