package com.workforcehub.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.agent.scheduler.AgentTaskTimeoutJobService;
import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * module-agent 等待人工确认任务超时扫描集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
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
            .andExpect(jsonPath("$.data.cancelledCount").value(1));

        assertEquals("CANCELLED", taskStatus(taskId));
        assertEquals("人工确认超时，任务已取消", taskErrorMessage(taskId));
        assertEquals("TIMEOUT", toolCallApprovalStatus(taskId));
        assertEquals("CANCELLED", toolCallResultStatus(taskId));
        assertEquals(1L, auditCount(taskId, "TASK_TIMEOUT_CANCELLED"));
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
}
