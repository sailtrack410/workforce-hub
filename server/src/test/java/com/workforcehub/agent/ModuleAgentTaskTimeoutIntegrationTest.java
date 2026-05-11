package com.workforcehub.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.agent.scheduler.AgentTaskTimeoutJobService;
import com.workforcehub.spi.WorkflowAgentResultCallback;
import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * module-agent 等待人工确认任务超时扫描集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
@Import(ModuleAgentTaskTimeoutIntegrationTest.TestWorkflowAgentCallbackConfiguration.class)
class ModuleAgentTaskTimeoutIntegrationTest extends LocalIntegrationTestSupport {

    private static final long EMPLOYEE_USER_ID = 2003L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AgentTaskTimeoutJobService timeoutJobService;

    @Autowired
    private RecordingWorkflowAgentResultCallback workflowCallback;

    @Test
    @DisplayName("等待人工确认任务超过截止时间后应自动取消并写入审计")
    void waitingHumanConfirmTaskShouldBeCancelledWhenDeadlineExpired() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long taskId = createWaitingTask(adminSession, employeeSession);
        jdbcTemplate.update("""
            UPDATE agt_task
            SET waiting_deadline_at = ?
            WHERE id = ?
            """, LocalDateTime.now().minusMinutes(1), taskId);

        long cancelledCount = timeoutJobService.cancelExpiredWaitingTasks(10);

        assertEquals(1L, cancelledCount);
        assertEquals("CANCELLED", taskStatus(taskId));
        assertEquals("人工确认超时，任务已取消", taskErrorMessage(taskId));
        assertEquals("TIMEOUT", toolCallApprovalStatus(taskId));
        assertEquals("CANCELLED", toolCallResultStatus(taskId));
        assertTrue(toolResponsePayload(taskId).contains("人工确认超时"));
        assertEquals(1L, auditCount(taskId, "TASK_TIMEOUT_CANCELLED"));
    }

    @Test
    @DisplayName("关联审批申请的Agent超时任务取消后应回调workflow收口预审")
    void workflowPreReviewWaitingTaskShouldNotifyWorkflowWhenTimeoutCancelled() throws Exception {
        workflowCallback.reset();
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long taskId = createWaitingTask(adminSession, employeeSession);
        long requestId = 990001L;
        jdbcTemplate.update("""
            UPDATE agt_task
            SET context_type = 'WF_REQUEST',
                context_id = ?,
                waiting_deadline_at = ?
            WHERE id = ?
            """, requestId, LocalDateTime.now().minusMinutes(1), taskId);

        long cancelledCount = timeoutJobService.cancelExpiredWaitingTasks(10);

        assertEquals(1L, cancelledCount);
        assertEquals("CANCELLED", taskStatus(taskId));
        assertEquals(requestId, workflowCallback.requestId);
        assertEquals(taskId, workflowCallback.agentTaskId);
        assertTrue(workflowCallback.decisionJson.contains("\"decision\":\"TIMEOUT\""));
        assertTrue(workflowCallback.decisionJson.contains("人工确认超时"));
    }

    @Test
    @DisplayName("未超过截止时间的等待任务不应被超时扫描取消")
    void waitingHumanConfirmTaskShouldRemainWhenDeadlineNotExpired() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long taskId = createWaitingTask(adminSession, employeeSession);

        long cancelledCount = timeoutJobService.cancelExpiredWaitingTasks(10);

        assertEquals(0L, cancelledCount);
        assertEquals("WAITING_HUMAN_CONFIRM", taskStatus(taskId));
        assertEquals("PENDING", toolCallApprovalStatus(taskId));
        assertEquals("WAITING_CONFIRM", toolCallResultStatus(taskId));
        assertEquals(0L, auditCount(taskId, "TASK_TIMEOUT_CANCELLED"));
    }

    @Test
    @DisplayName("后台应能手动触发等待人工确认任务超时扫描")
    void adminShouldTriggerTimeoutScanManually() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long taskId = createWaitingTask(adminSession, employeeSession);
        jdbcTemplate.update("""
            UPDATE agt_task
            SET waiting_deadline_at = ?
            WHERE id = ?
            """, LocalDateTime.now().minusMinutes(1), taskId);

        mockMvc.perform(post("/api/admin/agent-tasks/timeout-scan")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "batchSize": 10
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.jobRunId").isString())
            .andExpect(jsonPath("$.data.triggerType").value("MANUAL"))
            .andExpect(jsonPath("$.data.jobStatus").value("SUCCESS"))
            .andExpect(jsonPath("$.data.batchSize").value(10))
            .andExpect(jsonPath("$.data.scannedCount").value(1))
            .andExpect(jsonPath("$.data.cancelledCount").value(1));

        assertEquals("CANCELLED", taskStatus(taskId));
        assertEquals("人工确认超时，任务已取消", taskErrorMessage(taskId));
        assertEquals("TIMEOUT", toolCallApprovalStatus(taskId));
        assertEquals("CANCELLED", toolCallResultStatus(taskId));
        assertEquals(1L, auditCount(taskId, "TASK_TIMEOUT_CANCELLED"));
        assertEquals(1L, timeoutJobRunCount("MANUAL", "SUCCESS"));
    }

    @Test
    @DisplayName("后台手动触发超时扫描显式传空批次时应使用默认批次")
    void adminTimeoutScanShouldUseDefaultBatchSizeWhenBatchSizeIsNull() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long taskId = createWaitingTask(adminSession, employeeSession);
        jdbcTemplate.update("""
            UPDATE agt_task
            SET waiting_deadline_at = ?
            WHERE id = ?
            """, LocalDateTime.now().minusMinutes(1), taskId);

        mockMvc.perform(post("/api/admin/agent-tasks/timeout-scan")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "batchSize": null
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.cancelledCount").value(1));

        assertEquals("CANCELLED", taskStatus(taskId));
    }

    @Test
    @DisplayName("生产化超时扫描应生成批次记录并返回运行摘要")
    void timeoutScanShouldCreateJobRunRecordAndReturnSummary() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long taskId = createWaitingTask(adminSession, employeeSession);
        jdbcTemplate.update("""
            UPDATE agt_task
            SET waiting_deadline_at = ?
            WHERE id = ?
            """, LocalDateTime.now().minusMinutes(1), taskId);

        JsonNode result = timeoutScanResult("SCHEDULED", 10);

        assertTrue(result.get("jobRunId").asText().startsWith("AGT-TIMEOUT-"));
        assertEquals("SCHEDULED", result.get("triggerType").asText());
        assertEquals("SUCCESS", result.get("jobStatus").asText());
        assertEquals(10, result.get("batchSize").asInt());
        assertEquals(1L, result.get("scannedCount").asLong());
        assertEquals(1L, result.get("cancelledCount").asLong());
        assertEquals(0L, result.get("skippedCount").asLong());
        assertEquals("CANCELLED", taskStatus(taskId));
        assertEquals(1L, timeoutJobRunCount("SCHEDULED", "SUCCESS"));
        assertEquals(1L, timeoutJobRunCancelledCount(result.get("jobRunId").asText()));
    }

    @Test
    @DisplayName("生产化超时扫描拿不到锁时应跳过并记录批次")
    void timeoutScanShouldSkipWhenAnotherInstanceHoldsLock() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long taskId = createWaitingTask(adminSession, employeeSession);
        jdbcTemplate.update("""
            UPDATE agt_task
            SET waiting_deadline_at = ?
            WHERE id = ?
            """, LocalDateTime.now().minusMinutes(1), taskId);
        jdbcTemplate.update("""
            INSERT INTO sys_job_lock
                (lock_name, owner_id, locked_at, locked_until)
            VALUES
                ('AGENT_TASK_TIMEOUT_SCAN', 'other-node', ?, ?)
            """, LocalDateTime.now(), LocalDateTime.now().plusMinutes(5));

        JsonNode result = timeoutScanResult("SCHEDULED", 10);

        assertTrue(result.get("jobRunId").asText().startsWith("AGT-TIMEOUT-"));
        assertEquals("SCHEDULED", result.get("triggerType").asText());
        assertEquals("SKIPPED", result.get("jobStatus").asText());
        assertEquals(0L, result.get("scannedCount").asLong());
        assertEquals(0L, result.get("cancelledCount").asLong());
        assertEquals(1L, result.get("skippedCount").asLong());
        assertEquals("WAITING_HUMAN_CONFIRM", taskStatus(taskId));
        assertEquals(0L, auditCount(taskId, "TASK_TIMEOUT_CANCELLED"));
        assertEquals(1L, timeoutJobRunCount("SCHEDULED", "SKIPPED"));
    }

    @Test
    @DisplayName("超时扫描服务应暴露真实定时入口")
    void timeoutScanServiceShouldExposeScheduledEntry() {
        Method scheduledMethod;
        try {
            scheduledMethod = AgentTaskTimeoutJobService.class.getDeclaredMethod("scheduledTimeoutScan");
        } catch (NoSuchMethodException e) {
            fail("AgentTaskTimeoutJobService 应提供 scheduledTimeoutScan 定时入口");
            return;
        }
        Scheduled scheduled = scheduledMethod.getAnnotation(Scheduled.class);
        assertNotNull(scheduled, "scheduledTimeoutScan 必须标记 @Scheduled");
        assertEquals("${workforce.agent.timeout-scan.fixed-delay-ms:60000}", scheduled.fixedDelayString());
        assertEquals("${workforce.agent.timeout-scan.initial-delay-ms:60000}", scheduled.initialDelayString());
    }

    @Test
    @DisplayName("测试环境关闭定时超时扫描时调用定时入口不应产生副作用")
    void scheduledTimeoutScanShouldSkipWhenDisabledInTestProfile() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long taskId = createWaitingTask(adminSession, employeeSession);
        jdbcTemplate.update("""
            UPDATE agt_task
            SET waiting_deadline_at = ?
            WHERE id = ?
            """, LocalDateTime.now().minusMinutes(1), taskId);

        timeoutJobService.scheduledTimeoutScan();

        assertEquals("WAITING_HUMAN_CONFIRM", taskStatus(taskId));
        assertEquals(0L, timeoutJobRunCount("SCHEDULED", "SUCCESS"));
        assertEquals(0L, timeoutJobRunCount("SCHEDULED", "SKIPPED"));
    }

    @Test
    @DisplayName("后台应能分页查询人工确认超时扫描批次记录")
    void adminShouldListTimeoutScanRuns() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long taskId = createWaitingTask(adminSession, employeeSession);
        jdbcTemplate.update("""
            UPDATE agt_task
            SET waiting_deadline_at = ?
            WHERE id = ?
            """, LocalDateTime.now().minusMinutes(1), taskId);
        JsonNode scanResult = timeoutScanResult("MANUAL", 10);

        mockMvc.perform(get("/api/admin/agent-tasks/timeout-scan-runs")
                .cookie(adminSession)
                .param("pageNo", "1")
                .param("pageSize", "10")
                .param("triggerType", "MANUAL")
                .param("status", "SUCCESS"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.list[0].jobRunId").value(scanResult.get("jobRunId").asText()))
            .andExpect(jsonPath("$.data.list[0].triggerType").value("MANUAL"))
            .andExpect(jsonPath("$.data.list[0].status").value("SUCCESS"))
            .andExpect(jsonPath("$.data.list[0].batchSize").value(10))
            .andExpect(jsonPath("$.data.list[0].scannedCount").value(1))
            .andExpect(jsonPath("$.data.list[0].cancelledCount").value(1))
            .andExpect(jsonPath("$.data.list[0].skippedCount").value(0));
    }

    @Test
    @DisplayName("后台查询人工确认超时扫描批次时应拒绝非法状态")
    void adminTimeoutScanRunListShouldRejectUnsupportedStatus() throws Exception {
        Cookie adminSession = loginAdmin();

        mockMvc.perform(get("/api/admin/agent-tasks/timeout-scan-runs")
                .cookie(adminSession)
                .param("status", "UNKNOWN_STATUS"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("Agent超时扫描批次状态不支持"));
    }

    private long createWaitingTask(Cookie adminSession, Cookie employeeSession) throws Exception {
        long toolId = createTool(adminSession);
        long agentId = createAgent(adminSession);
        long versionId = createVersion(adminSession, agentId);
        bindTool(adminSession, versionId, toolId);
        grantAcl(adminSession, agentId);
        publishVersion(adminSession, versionId);

        MvcResult taskResult = mockMvc.perform(post("/api/employee/agents/{agentId}/tasks", agentId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "triggerType": "MANUAL",
                      "contextType": "USER_QUERY",
                      "inputPayloadJson": "{\\"query\\":\\"等待确认超时测试\\"}",
                      "toolCalls": [
                        {
                          "toolCode": "ORG_GET_USER_SUMMARY",
                          "requestPayloadJson": "{\\"userId\\":2003}"
                        }
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("WAITING_HUMAN_CONFIRM"))
            .andReturn();
        return data(taskResult).get("taskId").asLong();
    }

    private long createTool(Cookie adminSession) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/agents/tools")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "toolCode": "ORG_GET_USER_SUMMARY",
                      "toolName": "组织用户摘要",
                      "toolType": "SPI",
                      "riskLevel": "HIGH",
                      "readOnlyFlag": false,
                      "status": "ACTIVE"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return data(result).get("toolId").asLong();
    }

    private long createAgent(Cookie adminSession) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/agents")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "agentCode": "TIMEOUT_AGENT",
                      "agentName": "超时测试助手",
                      "agentType": "AUTOMATION",
                      "identityMode": "SYSTEM_IDENTITY"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return data(result).get("agentId").asLong();
    }

    private long createVersion(Cookie adminSession, long agentId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/agents/{agentId}/versions", agentId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "versionNo": 1,
                      "modelConfigJson": "{\\"provider\\":\\"mock\\",\\"model\\":\\"agent-timeout-test\\"}",
                      "systemPrompt": "你是企业协同助手。",
                      "workflowConfigJson": "{\\"mode\\":\\"tool-first\\"}"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return data(result).get("versionId").asLong();
    }

    private void bindTool(Cookie adminSession, long versionId, long toolId) throws Exception {
        mockMvc.perform(post("/api/admin/agents/versions/{versionId}/tools", versionId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "toolId": %d,
                      "approvalMode": "REQUIRE_CONFIRM",
                      "argSchemaJson": "{\\"type\\":\\"object\\"}",
                      "status": "ACTIVE"
                    }
                    """.formatted(toolId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    private void grantAcl(Cookie adminSession, long agentId) throws Exception {
        mockMvc.perform(post("/api/admin/agents/{agentId}/acl", agentId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "subjectType": "USER",
                      "subjectId": %d,
                      "permCode": "use",
                      "status": "ACTIVE"
                    }
                    """.formatted(EMPLOYEE_USER_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    private void publishVersion(Cookie adminSession, long versionId) throws Exception {
        mockMvc.perform(post("/api/admin/agents/versions/{versionId}/publish", versionId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    private Cookie loginAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "admin.demo",
                      "password": "Admin@123456"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session, "后台登录后应返回 SESSION");
        return session;
    }

    private Cookie loginEmployee(String loginName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "Employee@123456"
                    }
                    """.formatted(loginName)))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session, "员工登录后应返回 SESSION");
        return session;
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private JsonNode timeoutScanResult(String triggerType, int batchSize) throws Exception {
        Method method;
        try {
            method = timeoutJobService.getClass().getMethod("runTimeoutScan", String.class, int.class);
        } catch (NoSuchMethodException e) {
            fail("AgentTaskTimeoutJobService 应提供 runTimeoutScan(triggerType, batchSize) 生产化扫描入口");
            return objectMapper.createObjectNode();
        }
        Object response = method.invoke(timeoutJobService, triggerType, batchSize);
        return objectMapper.valueToTree(response);
    }

    private String taskStatus(long taskId) {
        return jdbcTemplate.queryForObject("SELECT status FROM agt_task WHERE id = ?", String.class, taskId);
    }

    private String taskErrorMessage(long taskId) {
        return jdbcTemplate.queryForObject("SELECT error_message FROM agt_task WHERE id = ?", String.class, taskId);
    }

    private String toolCallApprovalStatus(long taskId) {
        return jdbcTemplate.queryForObject("""
            SELECT approval_status
            FROM agt_tool_call_log
            WHERE task_id = ?
            """, String.class, taskId);
    }

    private String toolCallResultStatus(long taskId) {
        return jdbcTemplate.queryForObject("""
            SELECT result_status
            FROM agt_tool_call_log
            WHERE task_id = ?
            """, String.class, taskId);
    }

    private String toolResponsePayload(long taskId) {
        String payload = jdbcTemplate.queryForObject("""
            SELECT response_payload_json
            FROM agt_tool_call_log
            WHERE task_id = ?
            """, String.class, taskId);
        return payload == null ? "" : payload;
    }

    private long auditCount(long taskId, String actionType) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM agt_audit_log
            WHERE task_id = ? AND action_type = ?
            """, Long.class, taskId, actionType);
        return count == null ? 0L : count;
    }

    private long timeoutJobRunCount(String triggerType, String status) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM agt_task_timeout_job_run
            WHERE trigger_type = ? AND status = ?
            """, Long.class, triggerType, status);
        return count == null ? 0L : count;
    }

    private long timeoutJobRunCancelledCount(String jobRunId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT cancelled_count
            FROM agt_task_timeout_job_run
            WHERE job_run_id = ?
            """, Long.class, jobRunId);
        return count == null ? 0L : count;
    }

    @TestConfiguration
    static class TestWorkflowAgentCallbackConfiguration {

        @Bean
        @Primary
        RecordingWorkflowAgentResultCallback recordingWorkflowAgentResultCallback() {
            return new RecordingWorkflowAgentResultCallback();
        }
    }

    static class RecordingWorkflowAgentResultCallback implements WorkflowAgentResultCallback {

        private Long requestId;
        private Long agentTaskId;
        private String decisionJson;

        @Override
        public void onPreReviewApproved(Long requestId, Long agentTaskId, String decisionJson) {
            throw new UnsupportedOperationException("本测试只验证超时回调");
        }

        @Override
        public void onPreReviewRejected(Long requestId, Long agentTaskId, String decisionJson) {
            throw new UnsupportedOperationException("本测试只验证超时回调");
        }

        @Override
        public void onPreReviewTimeout(Long requestId, Long agentTaskId, String decisionJson) {
            this.requestId = requestId;
            this.agentTaskId = agentTaskId;
            this.decisionJson = decisionJson;
        }

        @Override
        public void onPreReviewToHuman(Long requestId, Long agentTaskId, String decisionJson) {
            throw new UnsupportedOperationException("本测试只验证超时回调");
        }

        private void reset() {
            this.requestId = null;
            this.agentTaskId = null;
            this.decisionJson = null;
        }
    }
}
