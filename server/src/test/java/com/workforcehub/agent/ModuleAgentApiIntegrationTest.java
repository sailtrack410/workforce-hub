package com.workforcehub.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * module-agent 第一批 Agent 治理、授权、任务和工具调用接口集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleAgentApiIntegrationTest extends LocalIntegrationTestSupport {

    private static final long EMPLOYEE_USER_ID = 2003L;
    private static final long MANAGER_USER_ID = 2002L;
    private static final long OTHER_EMPLOYEE_USER_ID = 2004L;
    private static final long GROUP_CONVERSATION_ID = 7301L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("后台发布Agent并绑定只读组织工具后，授权员工应能创建成功任务并写入工具调用日志")
    void adminShouldPublishAgentAndAuthorizedEmployeeShouldCreateSucceededTask() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long toolId = createTool(adminSession, "ORG_GET_USER_SUMMARY", "组织用户摘要", "SPI", "READONLY", true);
        long agentId = createAgent(adminSession, "ORG_HELPER", "组织助手", "QA");
        long versionId = createVersion(adminSession, agentId, 1);
        bindTool(adminSession, versionId, toolId, "AUTO");
        grantAcl(adminSession, agentId, "USER", EMPLOYEE_USER_ID, "use");

        mockMvc.perform(post("/api/admin/agents/versions/{versionId}/publish", versionId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.agentId").value(agentId))
            .andExpect(jsonPath("$.data.publishedVersionId").value(versionId))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/employee/agents")
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].agentId").value(agentId))
            .andExpect(jsonPath("$.data[0].agentCode").value("ORG_HELPER"))
            .andExpect(jsonPath("$.data[0].publishedVersionId").value(versionId));

        MvcResult taskResult = mockMvc.perform(post("/api/employee/agents/{agentId}/tasks", agentId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "triggerType": "MANUAL",
                      "contextType": "USER_QUERY",
                      "contextId": 2003,
                      "inputPayloadJson": "{\\"query\\":\\"查询张三组织信息\\"}",
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
            .andExpect(jsonPath("$.data.agentId").value(agentId))
            .andExpect(jsonPath("$.data.agentVersionId").value(versionId))
            .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data.toolCallCount").value(1))
            .andReturn();

        long taskId = data(taskResult).get("taskId").asLong();
        assertEquals("SUCCEEDED", taskStatus(taskId));
        assertEquals(1L, toolCallCount(taskId, "SUCCESS"));
        assertTrue(toolResponsePayload(taskId).contains("张三"));
        assertEquals(1L, auditCount(taskId, "TASK_CREATED"));
        assertEquals(1L, auditCount(taskId, "TASK_RUNNING"));
        assertEquals(1L, auditCount(taskId, "TASK_SUCCEEDED"));
    }

    @Test
    @DisplayName("后台应能查询Agent列表、详情并停用已发布Agent")
    void adminShouldListGetAndDisablePublishedAgent() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long agentId = createPublishedAgentWithoutTool(adminSession, "ADMIN_MANAGED_AGENT", "后台治理助手");
        grantAcl(adminSession, agentId, "USER", EMPLOYEE_USER_ID, "use");

        mockMvc.perform(get("/api/admin/agents")
                .cookie(adminSession)
                .param("pageNo", "1")
                .param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.list", hasSize(1)))
            .andExpect(jsonPath("$.data.list[0].agentId").value(agentId))
            .andExpect(jsonPath("$.data.list[0].agentCode").value("ADMIN_MANAGED_AGENT"))
            .andExpect(jsonPath("$.data.list[0].status").value("ACTIVE"));

        mockMvc.perform(get("/api/admin/agents/{agentId}", agentId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.agentId").value(agentId))
            .andExpect(jsonPath("$.data.agentName").value("后台治理助手"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/api/admin/agents/{agentId}/disable", agentId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.agentId").value(agentId))
            .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        mockMvc.perform(get("/api/admin/agents/{agentId}", agentId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        mockMvc.perform(get("/api/employee/agents")
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(post("/api/employee/agents/{agentId}/tasks", agentId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "triggerType": "MANUAL",
                      "contextType": "USER_QUERY",
                      "inputPayloadJson": "{\\"query\\":\\"停用后不应执行\\"}"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409))
            .andExpect(jsonPath("$.message").value("Agent未发布或未启用"));
    }

    @Test
    @DisplayName("后台应能分页筛选并查看Agent任务详情")
    void adminShouldListAndGetAgentTasks() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long agentId = createPublishedAgentWithoutTool(adminSession, "TASK_QUERY_AGENT", "任务查询助手");
        grantAcl(adminSession, agentId, "USER", EMPLOYEE_USER_ID, "use");

        MvcResult taskResult = mockMvc.perform(post("/api/employee/agents/{agentId}/tasks", agentId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "triggerType": "MANUAL",
                      "contextType": "USER_QUERY",
                      "contextId": 2003,
                      "inputPayloadJson": "{\\"query\\":\\"后台任务查询验证\\"}"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
            .andReturn();
        long taskId = data(taskResult).get("taskId").asLong();

        mockMvc.perform(get("/api/admin/agent-tasks")
                .cookie(adminSession)
                .param("pageNo", "1")
                .param("pageSize", "20")
                .param("status", "SUCCEEDED")
                .param("agentId", String.valueOf(agentId))
                .param("triggeredById", String.valueOf(EMPLOYEE_USER_ID))
                .param("contextType", "USER_QUERY"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.pageNo").value(1))
            .andExpect(jsonPath("$.data.pageSize").value(20))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.list", hasSize(1)))
            .andExpect(jsonPath("$.data.list[0].taskId").value(taskId))
            .andExpect(jsonPath("$.data.list[0].agentId").value(agentId))
            .andExpect(jsonPath("$.data.list[0].agentCode").value("TASK_QUERY_AGENT"))
            .andExpect(jsonPath("$.data.list[0].agentName").value("任务查询助手"))
            .andExpect(jsonPath("$.data.list[0].agentVersionId").isNumber())
            .andExpect(jsonPath("$.data.list[0].triggerType").value("MANUAL"))
            .andExpect(jsonPath("$.data.list[0].contextType").value("USER_QUERY"))
            .andExpect(jsonPath("$.data.list[0].contextId").value(2003))
            .andExpect(jsonPath("$.data.list[0].triggeredByType").value("USER"))
            .andExpect(jsonPath("$.data.list[0].triggeredById").value(EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data.list[0].status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data.list[0].toolCallCount").value(0));

        mockMvc.perform(get("/api/admin/agent-tasks/{taskId}", taskId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.taskId").value(taskId))
            .andExpect(jsonPath("$.data.agentCode").value("TASK_QUERY_AGENT"))
            .andExpect(jsonPath("$.data.triggeredById").value(EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/admin/agent-tasks")
                .cookie(adminSession)
                .param("status", "WAITING_HUMAN_CONFIRM"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.total").value(0))
            .andExpect(jsonPath("$.data.list").isEmpty());
    }

    @Test
    @DisplayName("后台查询Agent任务时不支持非法任务状态")
    void adminTaskListShouldRejectUnsupportedStatus() throws Exception {
        Cookie adminSession = loginAdmin();

        mockMvc.perform(get("/api/admin/agent-tasks")
                .cookie(adminSession)
                .param("status", "UNKNOWN_STATUS"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("Agent任务状态不支持"));
    }

    @Test
    @DisplayName("员工不能看到或使用未授权Agent")
    void employeeShouldNotUseAgentWithoutAcl() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long agentId = createPublishedAgentWithoutTool(adminSession, "PRIVATE_AGENT", "未授权助手");

        mockMvc.perform(get("/api/employee/agents")
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(post("/api/employee/agents/{agentId}/tasks", agentId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "triggerType": "MANUAL",
                      "contextType": "USER_QUERY",
                      "inputPayloadJson": "{\\"query\\":\\"不应执行\\"}"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message").value("无权使用该Agent"));
    }

    @Test
    @DisplayName("员工创建无工具任务时显式传 null toolCalls 应按空工具列表处理")
    void nullToolCallsShouldBeTreatedAsEmptyList() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long agentId = createPublishedAgentWithoutTool(adminSession, "NULL_TOOL_CALL_AGENT", "空工具助手");
        grantAcl(adminSession, agentId, "USER", EMPLOYEE_USER_ID, "use");

        mockMvc.perform(post("/api/employee/agents/{agentId}/tasks", agentId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "triggerType": "MANUAL",
                      "contextType": "USER_QUERY",
                      "inputPayloadJson": "{\\"query\\":\\"只创建任务\\"}",
                      "toolCalls": null
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data.toolCallCount").value(0));
    }

    @Test
    @DisplayName("Agent 未绑定的工具不允许调用且不应创建任务")
    void unboundToolShouldBeRejectedWithoutCreatingTask() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        createTool(adminSession, "ATTENDANCE_GET_DAILY_RESULT", "考勤日结果", "SPI", "READONLY", true);
        long agentId = createPublishedAgentWithoutTool(adminSession, "NO_TOOL_AGENT", "未绑定工具助手");
        grantAcl(adminSession, agentId, "USER", EMPLOYEE_USER_ID, "use");

        mockMvc.perform(post("/api/employee/agents/{agentId}/tasks", agentId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "triggerType": "MANUAL",
                      "contextType": "USER_QUERY",
                      "inputPayloadJson": "{\\"query\\":\\"查询考勤\\"}",
                      "toolCalls": [
                        {
                          "toolCode": "ATTENDANCE_GET_DAILY_RESULT",
                          "requestPayloadJson": "{\\"userId\\":2003,\\"workDate\\":\\"2026-05-10\\"}"
                        }
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message").value("Agent未授权调用该工具"));

        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agt_task", Long.class));
    }

    @Test
    @DisplayName("工具执行失败时应保留失败任务、错误信息和失败工具日志")
    void toolExecutionFailureShouldKeepFailedTaskAndLog() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long toolId = createTool(adminSession, "UNKNOWN_SPI_TOOL", "未知SPI工具", "SPI", "READONLY", true);
        long agentId = createAgent(adminSession, "FAILED_TOOL_AGENT", "失败工具助手", "QA");
        long versionId = createVersion(adminSession, agentId, 1);
        bindTool(adminSession, versionId, toolId, "AUTO");
        grantAcl(adminSession, agentId, "USER", EMPLOYEE_USER_ID, "use");
        publishVersion(adminSession, versionId);

        MvcResult taskResult = mockMvc.perform(post("/api/employee/agents/{agentId}/tasks", agentId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "triggerType": "MANUAL",
                      "contextType": "USER_QUERY",
                      "inputPayloadJson": "{\\"query\\":\\"触发未知工具\\"}",
                      "toolCalls": [
                        {
                          "toolCode": "UNKNOWN_SPI_TOOL",
                          "requestPayloadJson": "{\\"userId\\":2003}"
                        }
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.errorMessage").value("暂不支持该SPI工具执行"))
            .andExpect(jsonPath("$.data.toolCallCount").value(1))
            .andReturn();

        long taskId = data(taskResult).get("taskId").asLong();
        assertEquals("FAILED", taskStatus(taskId));
        assertEquals(1L, toolCallCount(taskId, "FAILED"));
        assertEquals(1L, auditCount(taskId, "TASK_FAILED"));
        assertTrue(toolResponsePayload(taskId).contains("暂不支持该SPI工具执行"));
    }

    @Test
    @DisplayName("高风险工具绑定为人工确认时，任务应停留在等待人工确认且工具调用不执行")
    void highRiskToolShouldWaitForHumanConfirmation() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long toolId = createTool(adminSession, "CONNECTOR_WRITE_BACK", "连接器写入", "CONNECTOR", "HIGH", false);
        long agentId = createAgent(adminSession, "WRITE_AGENT", "写入助手", "AUTOMATION");
        long versionId = createVersion(adminSession, agentId, 1);
        bindTool(adminSession, versionId, toolId, "REQUIRE_CONFIRM");
        grantAcl(adminSession, agentId, "USER", EMPLOYEE_USER_ID, "use");
        publishVersion(adminSession, versionId);

        MvcResult taskResult = mockMvc.perform(post("/api/employee/agents/{agentId}/tasks", agentId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "triggerType": "MANUAL",
                      "contextType": "USER_QUERY",
                      "inputPayloadJson": "{\\"query\\":\\"执行高风险写入\\"}",
                      "toolCalls": [
                        {
                          "toolCode": "CONNECTOR_WRITE_BACK",
                          "requestPayloadJson": "{\\"action\\":\\"write\\"}"
                        }
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("WAITING_HUMAN_CONFIRM"))
            .andExpect(jsonPath("$.data.toolCallCount").value(1))
            .andReturn();

        long taskId = data(taskResult).get("taskId").asLong();
        assertEquals("WAITING_HUMAN_CONFIRM", taskStatus(taskId));
        assertEquals(1L, toolCallCount(taskId, "WAITING_CONFIRM"));
        assertEquals("PENDING", jdbcTemplate.queryForObject("""
            SELECT approval_status
            FROM agt_tool_call_log
            WHERE task_id = ?
            """, String.class, taskId));
        assertEquals(1L, auditCount(taskId, "TASK_WAITING_HUMAN_CONFIRM"));
    }

    @Test
    @DisplayName("等待人工确认的任务被确认后应继续执行待确认工具")
    void waitingTaskShouldContinueAfterHumanConfirmation() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long taskId = createWaitingTask(
            adminSession,
            employeeSession,
            "CONFIRM_CONTINUE_AGENT",
            "ORG_GET_USER_SUMMARY",
            "组织用户摘要",
            "SPI",
            "{\"userId\":2003}"
        );

        mockMvc.perform(post("/api/employee/agent-tasks/{taskId}/confirm", taskId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "approved": true,
                      "comment": "确认继续执行"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data.toolCallCount").value(1));

        assertEquals("SUCCEEDED", taskStatus(taskId));
        assertEquals("APPROVED", toolCallApprovalStatus(taskId));
        assertEquals("SUCCESS", toolCallResultStatus(taskId));
        assertTrue(toolResponsePayload(taskId).contains("张三"));
        assertEquals(1L, auditCount(taskId, "TASK_HUMAN_CONFIRMED"));
        assertEquals(1L, auditCount(taskId, "TASK_RUNNING"));
        assertEquals(1L, auditCount(taskId, "TASK_SUCCEEDED"));
    }

    @Test
    @DisplayName("等待人工确认的任务被拒绝后应终止且不执行工具")
    void waitingTaskShouldRejectWithoutExecutingTool() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long taskId = createWaitingTask(
            adminSession,
            employeeSession,
            "CONFIRM_REJECT_AGENT",
            "ORG_GET_USER_SUMMARY",
            "组织用户摘要",
            "SPI",
            "{\"userId\":2003}"
        );

        mockMvc.perform(post("/api/employee/agent-tasks/{taskId}/confirm", taskId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "approved": false,
                      "comment": "不同意继续执行"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
            .andExpect(jsonPath("$.data.toolCallCount").value(1));

        assertEquals("REJECTED", taskStatus(taskId));
        assertEquals("REJECTED", toolCallApprovalStatus(taskId));
        assertEquals("REJECTED", toolCallResultStatus(taskId));
        assertTrue(toolResponsePayload(taskId).contains("不同意继续执行"));
        assertEquals(1L, auditCount(taskId, "TASK_HUMAN_REJECTED"));
        assertEquals(0L, auditCount(taskId, "TASK_RUNNING"));
    }

    @Test
    @DisplayName("待确认工具绑定失效后不能把任务误确认为成功")
    void waitingTaskShouldNotSucceedWhenPendingToolBindingIsInactive() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long taskId = createWaitingTask(
            adminSession,
            employeeSession,
            "CONFIRM_INACTIVE_BINDING_AGENT",
            "ORG_GET_USER_SUMMARY",
            "组织用户摘要",
            "SPI",
            "{\"userId\":2003}"
        );
        jdbcTemplate.update("""
            UPDATE agt_agent_tool aat
            JOIN agt_task task ON task.agent_version_id = aat.agent_version_id
            SET aat.status = 'INACTIVE'
            WHERE task.id = ?
            """, taskId);

        mockMvc.perform(post("/api/employee/agent-tasks/{taskId}/confirm", taskId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "approved": true,
                      "comment": "确认继续执行"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409))
            .andExpect(jsonPath("$.message").value("Agent任务没有可继续执行的待确认工具"));

        assertEquals("WAITING_HUMAN_CONFIRM", taskStatus(taskId));
        assertEquals("PENDING", toolCallApprovalStatus(taskId));
        assertEquals("WAITING_CONFIRM", toolCallResultStatus(taskId));
        assertEquals(0L, auditCount(taskId, "TASK_SUCCEEDED"));
    }

    @Test
    @DisplayName("等待人工确认的任务只能由触发人取消")
    void waitingTaskShouldOnlyBeCancelledByTriggerUser() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        Cookie otherEmployeeSession = loginEmployee("wangwu.employee");
        long taskId = createWaitingTask(
            adminSession,
            employeeSession,
            "CONFIRM_CANCEL_AGENT",
            "CONNECTOR_WRITE_BACK",
            "连接器写入",
            "CONNECTOR",
            "{\"action\":\"write\"}"
        );

        mockMvc.perform(post("/api/employee/agent-tasks/{taskId}/cancel", taskId)
                .cookie(otherEmployeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "他人不能取消"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message").value("无权操作该Agent任务"));

        mockMvc.perform(post("/api/employee/agent-tasks/{taskId}/cancel", taskId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "当前业务已结束，无需继续执行"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        assertEquals("CANCELLED", taskStatus(taskId));
        assertEquals("CANCELLED", toolCallApprovalStatus(taskId));
        assertEquals("CANCELLED", toolCallResultStatus(taskId));
        assertTrue(toolResponsePayload(taskId).contains("当前业务已结束"));
        assertEquals(1L, auditCount(taskId, "TASK_CANCELLED"));
    }

    @Test
    @DisplayName("群组 ACL 应按当前活跃群成员控制 Agent 可见和使用")
    void groupAclShouldAuthorizeOnlyActiveGroupMembers() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        Cookie otherEmployeeSession = loginEmployee("wangwu.employee");
        seedCustomGroup();
        long agentId = createPublishedAgentWithoutTool(adminSession, "GROUP_AGENT", "群组助手");
        grantAcl(adminSession, agentId, "GROUP", GROUP_CONVERSATION_ID, "use");

        mockMvc.perform(get("/api/employee/agents")
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].agentId").value(agentId));

        MvcResult taskResult = mockMvc.perform(post("/api/employee/agents/{agentId}/tasks", agentId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "triggerType": "MANUAL",
                      "contextType": "IM_GROUP",
                      "contextId": 7301,
                      "inputPayloadJson": "{\\"query\\":\\"群内Agent问答\\"}"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
            .andReturn();

        long taskId = data(taskResult).get("taskId").asLong();
        assertEquals("SUCCEEDED", taskStatus(taskId));

        mockMvc.perform(get("/api/employee/agents")
                .cookie(otherEmployeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(post("/api/employee/agents/{agentId}/tasks", agentId)
                .cookie(otherEmployeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "triggerType": "MANUAL",
                      "contextType": "IM_GROUP",
                      "contextId": 7301,
                      "inputPayloadJson": "{\\"query\\":\\"非群成员不应调用\\"}"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message").value("无权使用该Agent"));
    }

    private long createWaitingTask(Cookie adminSession,
                                   Cookie employeeSession,
                                   String agentCode,
                                   String toolCode,
                                   String toolName,
                                   String toolType,
                                   String requestPayloadJson) throws Exception {
        long toolId = createTool(adminSession, toolCode, toolName, toolType, "HIGH", false);
        long agentId = createAgent(adminSession, agentCode, "待确认助手", "AUTOMATION");
        long versionId = createVersion(adminSession, agentId, 1);
        bindTool(adminSession, versionId, toolId, "REQUIRE_CONFIRM");
        grantAcl(adminSession, agentId, "USER", EMPLOYEE_USER_ID, "use");
        publishVersion(adminSession, versionId);

        MvcResult taskResult = mockMvc.perform(post("/api/employee/agents/{agentId}/tasks", agentId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "triggerType": "MANUAL",
                      "contextType": "USER_QUERY",
                      "inputPayloadJson": "{\\"query\\":\\"执行待确认任务\\"}",
                      "toolCalls": [
                        {
                          "toolCode": "%s",
                          "requestPayloadJson": "%s"
                        }
                      ]
                    }
                    """.formatted(toolCode, escapeJsonString(requestPayloadJson))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("WAITING_HUMAN_CONFIRM"))
            .andReturn();
        return data(taskResult).get("taskId").asLong();
    }

    private long createPublishedAgentWithoutTool(Cookie adminSession, String agentCode, String agentName) throws Exception {
        long agentId = createAgent(adminSession, agentCode, agentName, "QA");
        long versionId = createVersion(adminSession, agentId, 1);
        publishVersion(adminSession, versionId);
        return agentId;
    }

    private long createTool(Cookie adminSession,
                            String toolCode,
                            String toolName,
                            String toolType,
                            String riskLevel,
                            boolean readOnlyFlag) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/agents/tools")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "toolCode": "%s",
                      "toolName": "%s",
                      "toolType": "%s",
                      "riskLevel": "%s",
                      "readOnlyFlag": %s,
                      "status": "ACTIVE"
                    }
                    """.formatted(toolCode, toolName, toolType, riskLevel, readOnlyFlag)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return data(result).get("toolId").asLong();
    }

    private long createAgent(Cookie adminSession, String agentCode, String agentName, String agentType) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/agents")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "agentCode": "%s",
                      "agentName": "%s",
                      "agentType": "%s",
                      "identityMode": "SYSTEM_IDENTITY"
                    }
                    """.formatted(agentCode, agentName, agentType)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return data(result).get("agentId").asLong();
    }

    private long createVersion(Cookie adminSession, long agentId, int versionNo) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/agents/{agentId}/versions", agentId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "versionNo": %d,
                      "modelConfigJson": "{\\"provider\\":\\"mock\\",\\"model\\":\\"agent-test\\"}",
                      "systemPrompt": "你是企业协同助手。",
                      "workflowConfigJson": "{\\"mode\\":\\"tool-first\\"}"
                    }
                    """.formatted(versionNo)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return data(result).get("versionId").asLong();
    }

    private void bindTool(Cookie adminSession, long versionId, long toolId, String approvalMode) throws Exception {
        mockMvc.perform(post("/api/admin/agents/versions/{versionId}/tools", versionId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "toolId": %d,
                      "approvalMode": "%s",
                      "argSchemaJson": "{\\"type\\":\\"object\\"}",
                      "status": "ACTIVE"
                    }
                    """.formatted(toolId, approvalMode)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    private void grantAcl(Cookie adminSession,
                          long agentId,
                          String subjectType,
                          long subjectId,
                          String permCode) throws Exception {
        mockMvc.perform(post("/api/admin/agents/{agentId}/acl", agentId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "subjectType": "%s",
                      "subjectId": %d,
                      "permCode": "%s",
                      "status": "ACTIVE"
                    }
                    """.formatted(subjectType, subjectId, permCode)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    private void publishVersion(Cookie adminSession, long versionId) throws Exception {
        mockMvc.perform(post("/api/admin/agents/versions/{versionId}/publish", versionId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    private void seedCustomGroup() {
        jdbcTemplate.update("""
            INSERT INTO im_conversation
                (id, conversation_type, title, last_message_seq, status, created_by, updated_by, is_deleted)
            VALUES
                (?, 'GROUP', 'Agent协作群', 0, 'ACTIVE', ?, ?, 0)
            """, GROUP_CONVERSATION_ID, EMPLOYEE_USER_ID, EMPLOYEE_USER_ID);
        jdbcTemplate.update("""
            INSERT INTO im_group_profile
                (id, conversation_id, group_type, owner_user_id, status, created_by, updated_by)
            VALUES
                (7302, ?, 'CUSTOM', ?, 'ACTIVE', ?, ?)
            """, GROUP_CONVERSATION_ID, EMPLOYEE_USER_ID, EMPLOYEE_USER_ID, EMPLOYEE_USER_ID);
        jdbcTemplate.update("""
            INSERT INTO im_conversation_member
                (id, conversation_id, member_type, member_id, member_role, join_source, last_read_seq, status)
            VALUES
                (7303, ?, 'USER', ?, 'OWNER', 'CREATE', 0, 'ACTIVE'),
                (7304, ?, 'USER', ?, 'MEMBER', 'CREATE', 0, 'ACTIVE')
            """, GROUP_CONVERSATION_ID, EMPLOYEE_USER_ID, GROUP_CONVERSATION_ID, MANAGER_USER_ID);
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

    private long toolCallCount(long taskId, String resultStatus) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM agt_tool_call_log
            WHERE task_id = ? AND result_status = ?
            """, Long.class, taskId, resultStatus);
        return count == null ? 0L : count;
    }

    private String toolResponsePayload(long taskId) {
        String payload = jdbcTemplate.queryForObject("""
            SELECT response_payload_json
            FROM agt_tool_call_log
            WHERE task_id = ?
            """, String.class, taskId);
        return payload == null ? "" : payload;
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

    private long auditCount(long taskId, String actionType) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM agt_audit_log
            WHERE task_id = ? AND action_type = ?
            """, Long.class, taskId, actionType);
        return count == null ? 0L : count;
    }

    private String escapeJsonString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
