package com.workforcehub.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.agent.model.AgentModelClient;
import com.workforcehub.agent.model.AgentModelRequest;
import com.workforcehub.agent.model.AgentModelResponse;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * module-agent 第一批 Agent 治理、授权、任务和工具调用接口集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
@Import(ModuleAgentApiIntegrationTest.TestAgentModelConfiguration.class)
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
            .andExpect(jsonLong("$.data.agentId", agentId))
            .andExpect(jsonLong("$.data.publishedVersionId", versionId))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/employee/agents")
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonLong("$.data[0].agentId", agentId))
            .andExpect(jsonPath("$.data[0].agentCode").value("ORG_HELPER"))
            .andExpect(jsonLong("$.data[0].publishedVersionId", versionId));

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
            .andExpect(jsonLong("$.data.agentId", agentId))
            .andExpect(jsonLong("$.data.agentVersionId", versionId))
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
    @DisplayName("后台应能校验DAG并拒绝发布非法工作流版本")
    void adminShouldValidateWorkflowAndRejectInvalidWorkflowPublish() throws Exception {
        Cookie adminSession = loginAdmin();
        String invalidWorkflow = """
            {
              "nodes": [
                { "id": "start", "type": "START" },
                { "id": "load_user", "type": "TOOL", "config": { "toolCode": "ORG_GET_USER_SUMMARY" } },
                { "id": "end", "type": "END" }
              ],
              "edges": [
                { "from": "start", "to": "load_user" },
                { "from": "load_user", "to": "start" },
                { "from": "load_user", "to": "missing" }
              ]
            }
            """;

        mockMvc.perform(post("/api/admin/agents/workflows/validate")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "workflowConfigJson": "%s"
                    }
                    """.formatted(escapeJsonString(invalidWorkflow))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.valid").value(false))
            .andExpect(jsonPath("$.data.messages[0]").isString());

        long agentId = createAgent(adminSession, "INVALID_DAG_AGENT", "非法DAG助手", "QA");
        long versionId = createVersionWithWorkflow(adminSession, agentId, 1, invalidWorkflow);

        mockMvc.perform(post("/api/admin/agents/versions/{versionId}/publish", versionId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value(containsString("工作流配置无效")));
    }

    @Test
    @DisplayName("后台发布Agent前必须配置可用范围")
    void adminShouldRejectPublishWithoutAcl() throws Exception {
        Cookie adminSession = loginAdmin();
        long agentId = createAgent(adminSession, "NO_ACL_AGENT", "未授权范围助手", "QA");
        long versionId = createVersion(adminSession, agentId, 1);

        mockMvc.perform(post("/api/admin/agents/versions/{versionId}/publish", versionId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("请先配置AI助手可用范围"));
    }

    @Test
    @DisplayName("后台发布DAG Agent前必须完成一次当前版本流程测试")
    void adminShouldRejectDagPublishWithoutWorkflowTest() throws Exception {
        Cookie adminSession = loginAdmin();
        long agentId = createAgent(adminSession, "NO_TEST_AGENT", "未测试流程助手", "QA");
        long versionId = createVersionWithWorkflow(adminSession, agentId, 1, simpleWorkflow("{{query}}"));
        grantAcl(adminSession, agentId, "USER", EMPLOYEE_USER_ID, "use");

        mockMvc.perform(post("/api/admin/agents/versions/{versionId}/publish", versionId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("请先完成一次流程测试"));

        runWorkflowTest(adminSession, versionId, simpleWorkflow("{{query}}"));

        mockMvc.perform(post("/api/admin/agents/versions/{versionId}/publish", versionId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("草稿DAG修改后应重新流程测试才可发布")
    void adminShouldRejectDagPublishAfterWorkflowChangedSinceTest() throws Exception {
        Cookie adminSession = loginAdmin();
        long agentId = createAgent(adminSession, "STALE_TEST_AGENT", "测试失效助手", "QA");
        long versionId = createVersionWithWorkflow(adminSession, agentId, 1, simpleWorkflow("{{query}}"));
        grantAcl(adminSession, agentId, "USER", EMPLOYEE_USER_ID, "use");
        runWorkflowTest(adminSession, versionId, simpleWorkflow("{{query}}"));

        mockMvc.perform(put("/api/admin/agents/versions/{versionId}", versionId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "modelConfigJson": "{\\"provider\\":\\"mock\\",\\"model\\":\\"agent-test\\"}",
                      "systemPrompt": "你是企业协同助手。",
                      "workflowConfigJson": "%s"
                    }
                    """.formatted(escapeJsonString(simpleWorkflow("已更新 {{query}}")))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/admin/agents/versions/{versionId}/publish", versionId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("请先完成一次流程测试"));
    }

    @Test
    @DisplayName("员工触发DAG任务时应按版本绑定执行工具并在后台任务详情返回执行轨迹")
    void employeeDagTaskShouldExecuteBoundToolAndExposeTrace() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long toolId = createTool(adminSession, "ORG_GET_USER_SUMMARY", "组织用户摘要", "SPI", "READONLY", true);
        long agentId = createAgent(adminSession, "DAG_ORG_AGENT", "DAG组织助手", "QA");
        long versionId = createVersionWithWorkflow(adminSession, agentId, 1, """
            {
              "nodes": [
                { "id": "start", "type": "START" },
                {
                  "id": "load_user",
                  "type": "TOOL",
                  "config": {
                    "toolCode": "ORG_GET_USER_SUMMARY",
                    "args": { "userId": "{{input.userId}}" }
                  },
                  "outputKey": "user"
                },
                { "id": "end", "type": "END", "config": { "reply": "已查询 {{user.result.name}}" } }
              ],
              "edges": [
                { "from": "start", "to": "load_user" },
                { "from": "load_user", "to": "end" }
              ]
            }
            """);
        bindTool(adminSession, versionId, toolId, "AUTO");
        grantAcl(adminSession, agentId, "USER", EMPLOYEE_USER_ID, "use");
        runWorkflowTest(adminSession, versionId, """
            {
              "nodes": [
                { "id": "start", "type": "START" },
                {
                  "id": "load_user",
                  "type": "TOOL",
                  "config": {
                    "toolCode": "ORG_GET_USER_SUMMARY",
                    "args": { "userId": "{{input.userId}}" }
                  },
                  "outputKey": "user"
                },
                { "id": "end", "type": "END", "config": { "reply": "已查询 {{user.result.name}}" } }
              ],
              "edges": [
                { "from": "start", "to": "load_user" },
                { "from": "load_user", "to": "end" }
              ]
            }
            """);
        publishVersion(adminSession, versionId);

        MvcResult taskResult = mockMvc.perform(post("/api/employee/agents/{agentId}/tasks", agentId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "triggerType": "MANUAL",
                      "contextType": "USER_QUERY",
                      "contextId": 2003,
                      "inputPayloadJson": "{\\"userId\\":2003}"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data.toolCallCount").value(1))
            .andReturn();

        long taskId = data(taskResult).get("taskId").asLong();
        assertEquals("SUCCEEDED", taskStatus(taskId));
        assertEquals(1L, toolCallCount(taskId, "SUCCESS"));
        assertTrue(toolResponsePayload(taskId).contains("张三"));

        mockMvc.perform(get("/api/admin/agent-tasks/{taskId}", taskId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.executionTraceJson").value(containsString("load_user")))
            .andExpect(jsonPath("$.data.executionTraceJson").value(containsString("SUCCEEDED")));
    }

    @Test
    @DisplayName("员工DAG数据查询应拒绝越权用户信息")
    void employeeDagDataSourceShouldRejectUnauthorizedTargetUser() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        String workflowConfigJson = """
            {
              "nodes": [
                { "id": "start", "type": "START" },
                {
                  "id": "load_user",
                  "type": "TOOL",
                  "config": {
                    "dataSources": ["employeeInfo"],
                    "targetUserId": "{{input.userId}}"
                  },
                  "outputKey": "userData"
                },
                { "id": "end", "type": "END", "config": { "reply": "已查询 {{userData.employeeInfo.realName}}" } }
              ],
              "edges": [
                { "from": "start", "to": "load_user" },
                { "from": "load_user", "to": "end" }
              ]
            }
            """;
        long agentId = createAgent(adminSession, "DAG_USER_DATA_AUTH_AGENT", "员工数据鉴权助手", "QA");
        long versionId = createVersionWithWorkflow(adminSession, agentId, 1, workflowConfigJson);
        grantAcl(adminSession, agentId, "USER", EMPLOYEE_USER_ID, "use");
        runWorkflowTest(adminSession, versionId, workflowConfigJson, "{\"userId\":2003,\"query\":\"测试\"}");
        publishVersion(adminSession, versionId);

        mockMvc.perform(post("/api/employee/agents/{agentId}/tasks", agentId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "triggerType": "MANUAL",
                      "contextType": "USER_QUERY",
                      "contextId": 2004,
                      "inputPayloadJson": "{\\"userId\\":2004,\\"query\\":\\"查王五\\"}"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.errorMessage").value("无权查询该员工信息"));
    }

    @Test
    @DisplayName("DAG提示词节点应能内嵌工具调用")
    void promptWorkflowNodeShouldExecuteEmbeddedToolCalls() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long toolId = createTool(adminSession, "ORG_GET_USER_SUMMARY", "组织用户摘要", "SPI", "READONLY", true);
        long agentId = createAgent(adminSession, "DAG_INTENT_TOOL_AGENT", "DAG节点工具助手", "QA");
        long versionId = createVersionWithWorkflow(adminSession, agentId, 1, """
            {
              "nodes": [
                { "id": "start", "type": "START" },
                {
                  "id": "recognize",
                  "type": "INTENT",
                  "config": {
                    "prompt": "请识别用户意图，用户姓名：{{user.result.name}}",
                    "toolCalls": [
                      {
                        "toolCode": "ORG_GET_USER_SUMMARY",
                        "args": { "userId": "{{input.userId}}" },
                        "outputKey": "user"
                      }
                    ]
                  },
                  "outputKey": "intent"
                },
                { "id": "end", "type": "END", "config": { "reply": "{{intent}}" } }
              ],
              "edges": [
                { "from": "start", "to": "recognize" },
                { "from": "recognize", "to": "end" }
              ]
            }
            """);
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
                      "contextId": 2003,
                      "inputPayloadJson": "{\\"userId\\":2003}"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data.toolCallCount").value(1))
            .andReturn();

        long taskId = data(taskResult).get("taskId").asLong();
        assertEquals(1L, toolCallCount(taskId, "SUCCESS"));
        assertTrue(toolResponsePayload(taskId).contains("张三"));
    }

    @Test
    @DisplayName("DAG输出格式为数组时应只保留数组项配置字段")
    void workflowRunShouldKeepOnlyConfiguredArrayItemFields() throws Exception {
        Cookie adminSession = loginAdmin();
        long agentId = createAgent(adminSession, "DAG_ARRAY_FORMAT_AGENT", "数组格式助手", "QA");
        String workflowConfigJson = """
            {
              "nodes": [
                { "id": "start", "type": "START" },
                {
                  "id": "answer",
                  "type": "LLM",
                  "config": { "prompt": "FORMAT_ARRAY_TEST" },
                  "outputKey": "answer",
                  "outputFormat": [
                    {
                      "key": "leaveAccounts",
                      "type": "array",
                      "description": "假期账户",
                      "itemFields": [
                        { "key": "leaveName", "type": "string", "description": "假期名称" },
                        { "key": "availableHours", "type": "number", "description": "可用小时" },
                        { "key": "frozenHours", "type": "number", "description": "冻结小时" }
                      ]
                    }
                  ]
                },
                { "id": "end", "type": "END", "config": { "reply": "{{answer.leaveAccounts}}" } }
              ],
              "edges": [
                { "from": "start", "to": "answer" },
                { "from": "answer", "to": "end" }
              ]
            }
            """;
        long versionId = createVersionWithWorkflow(adminSession, agentId, 1, workflowConfigJson);

        MvcResult result = mockMvc.perform(post("/api/admin/agents/workflow-run")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "versionId": %d,
                      "inputJson": "{\\"query\\":\\"测试\\"}",
                      "modelConfigJson": "{\\"provider\\":\\"mock\\",\\"model\\":\\"agent-test\\"}",
                      "systemPrompt": "你是企业协同助手。",
                      "workflowConfigJson": "%s"
                    }
                    """.formatted(versionId, escapeJsonString(workflowConfigJson))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.success").value(true))
            .andReturn();

        JsonNode output = objectMapper.readTree(data(result).path("steps").get(0).path("output").asText());
        JsonNode item = output.path("leaveAccounts").get(0);
        assertTrue(item.path("leaveName").asText().length() > 0);
        assertEquals(12, item.path("availableHours").asInt());
        assertEquals(4, item.path("frozenHours").asInt());
        assertFalse(item.has("accountId"));
        assertFalse(item.has("userId"));
        assertFalse(output.has("extra"));
    }

    @Test
    @DisplayName("DAG数据查询节点输出格式为数组时应只保留数组项配置字段")
    void dataSourceWorkflowNodeShouldKeepOnlyConfiguredArrayItemFields() throws Exception {
        Cookie adminSession = loginAdmin();
        long agentId = createAgent(adminSession, "DAG_DATA_ARRAY_FORMAT_AGENT", "数据数组格式助手", "QA");
        long leaveTypeId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        long accountId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        jdbcTemplate.update("""
            INSERT INTO lv_type
                (id, leave_code, leave_name, balance_mode, status)
            VALUES
                (?, ?, '测试年假', 'DEDUCT', 'ACTIVE')
            """, leaveTypeId, "AGENT_ARRAY_" + leaveTypeId);
        jdbcTemplate.update("""
            INSERT INTO lv_account
                (id, user_id, leave_type_id, available_minutes, frozen_minutes, version, status)
            VALUES
                (?, ?, ?, 720, 240, 0, 'ACTIVE')
            """, accountId, EMPLOYEE_USER_ID, leaveTypeId);
        String workflowConfigJson = """
            {
              "nodes": [
                { "id": "start", "type": "START" },
                {
                  "id": "load_user",
                  "type": "TOOL",
                  "config": {
                    "dataSources": ["leaveBalance"],
                    "targetUserId": "{{input.userId}}"
                  },
                  "outputKey": "userData",
                  "outputFormat": [
                    {
                      "key": "leaveBalance",
                      "type": "array",
                      "description": "假期账户",
                      "itemFields": [
                        { "key": "leaveName", "type": "string", "description": "假期名称" },
                        { "key": "availableMinutes", "type": "number", "description": "可用分钟" },
                        { "key": "frozenMinutes", "type": "number", "description": "冻结分钟" }
                      ]
                    }
                  ]
                },
                { "id": "end", "type": "END", "config": { "reply": "{{userData.leaveBalance}}" } }
              ],
              "edges": [
                { "from": "start", "to": "load_user" },
                { "from": "load_user", "to": "end" }
              ]
            }
            """;
        long versionId = createVersionWithWorkflow(adminSession, agentId, 1, workflowConfigJson);

        MvcResult result = mockMvc.perform(post("/api/admin/agents/workflow-run")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "versionId": %d,
                      "inputJson": "{\\"currentUserId\\":2003,\\"userId\\":2003}",
                      "modelConfigJson": "{\\"provider\\":\\"mock\\",\\"model\\":\\"agent-test\\"}",
                      "systemPrompt": "你是企业协同助手。",
                      "workflowConfigJson": "%s"
                    }
                    """.formatted(versionId, escapeJsonString(workflowConfigJson))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.success").value(true))
            .andReturn();

        JsonNode output = objectMapper.readTree(data(result).path("steps").get(0).path("output").asText());
        JsonNode item = output.path("leaveBalance").get(0);
        assertTrue(item.path("leaveName").asText().length() > 0);
        assertTrue(item.has("availableMinutes"));
        assertTrue(item.has("frozenMinutes"));
        assertFalse(item.has("accountId"));
        assertFalse(item.has("userId"));
        assertFalse(item.has("leaveTypeId"));
    }

    @Test
    @DisplayName("DAG应能返回当前部门真实人数卡片字段")
    void dataSourceWorkflowNodeShouldReturnDepartmentHeadcountFields() throws Exception {
        Cookie adminSession = loginAdmin();
        long agentId = createAgent(adminSession, "DAG_DEPT_HEADCOUNT_AGENT", "部门人数助手", "QA");
        String workflowConfigJson = """
            {
              "nodes": [
                { "id": "start", "type": "START" },
                {
                  "id": "load_dept",
                  "type": "TOOL",
                  "config": {
                    "dataSources": ["departmentHeadcountFields"],
                    "targetUserId": "{{input.userId}}"
                  },
                  "outputKey": "oaData",
                  "outputFormat": [
                    {
                      "key": "departmentHeadcountFields",
                      "type": "array",
                      "description": "部门人数字段",
                      "itemFields": [
                        { "key": "label", "type": "string", "description": "标签" },
                        { "key": "value", "type": "string", "description": "值" },
                        { "key": "tone", "type": "string", "description": "颜色" }
                      ]
                    }
                  ]
                },
                {
                  "id": "end",
                  "type": "END",
                  "config": {
                    "replyJson": {
                      "cardType": "agent_result",
                      "category": "OA",
                      "status": "SUCCESS",
                      "title": "{{input.queryTitle}}",
                      "answer": "查询完成",
                      "data": {
                        "fields": "{{oaData}}",
                        "records": null,
                        "flow": null
                      }
                    }
                  }
                }
              ],
              "edges": [
                { "from": "start", "to": "load_dept" },
                { "from": "load_dept", "to": "end" }
              ]
            }
            """;
        long versionId = createVersionWithWorkflow(adminSession, agentId, 1, workflowConfigJson);

        MvcResult result = mockMvc.perform(post("/api/admin/agents/workflow-run")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "versionId": %d,
                      "inputJson": "{\\"currentUserId\\":2003,\\"userId\\":2003,\\"query\\":\\"我们部门人数是多少？\\"}",
                      "modelConfigJson": "{\\"provider\\":\\"mock\\",\\"model\\":\\"agent-test\\"}",
                      "systemPrompt": "你是企业协同助手。",
                      "workflowConfigJson": "%s"
                    }
                    """.formatted(versionId, escapeJsonString(workflowConfigJson))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.success").value(true))
            .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        JsonNode reply = objectMapper.readTree(response.path("data").path("finalReply").asText());
        JsonNode fields = reply.path("data").path("fields");
        assertEquals("部门", fields.get(0).path("label").asText());
        assertTrue(fields.get(0).path("value").asText().length() > 0);
        assertEquals("部门人数", fields.get(1).path("label").asText());
        assertTrue(fields.get(1).path("value").asText().endsWith(" 人"));
        assertFalse(response.path("data").path("finalReply").asText().contains("假期名称"));
    }

    @Test
    @DisplayName("DAG结束节点应能返回JSON结构")
    void workflowRunShouldReturnConfiguredReplyJson() throws Exception {
        Cookie adminSession = loginAdmin();
        long agentId = createAgent(adminSession, "DAG_REPLY_JSON_AGENT", "JSON回复助手", "QA");
        long leaveTypeId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        long accountId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        jdbcTemplate.update("""
            INSERT INTO lv_type
                (id, leave_code, leave_name, balance_mode, status)
            VALUES
                (?, ?, '测试年假', 'DEDUCT', 'ACTIVE')
            """, leaveTypeId, "AGENT_JSON_" + leaveTypeId);
        jdbcTemplate.update("""
            INSERT INTO lv_account
                (id, user_id, leave_type_id, available_minutes, frozen_minutes, version, status)
            VALUES
                (?, ?, ?, 720, 240, 0, 'ACTIVE')
            """, accountId, EMPLOYEE_USER_ID, leaveTypeId);
        String workflowConfigJson = """
            {
              "nodes": [
                { "id": "start", "type": "START" },
                {
                  "id": "load_user",
                  "type": "TOOL",
                  "config": {
                    "dataSources": ["employeeInfo", "leaveBalance", "orgChart"],
                    "targetUserId": "{{input.userId}}"
                  },
                  "outputKey": "userData",
                  "outputFormat": [
                    { "key": "employeeInfo", "type": "object", "description": "员工信息" },
                    {
                      "key": "leaveBalance",
                      "type": "array",
                      "description": "假期账户",
                      "itemFields": [
                        { "key": "leaveName", "type": "string", "description": "假期名称" },
                        { "key": "availableMinutes", "type": "number", "description": "可用分钟" },
                        { "key": "frozenMinutes", "type": "number", "description": "冻结分钟" }
                      ]
                    },
                    { "key": "orgChart", "type": "object", "description": "组织关系" }
                  ]
                },
                {
                  "id": "end",
                  "type": "END",
                  "config": {
                    "replyJson": {
                      "employee": {
                        "name": "{{userData.employeeInfo.realName}}",
                        "department": "{{userData.employeeInfo.primaryDeptName}}",
                        "position": "{{userData.employeeInfo.positionName}}",
                        "manager": "{{userData.employeeInfo.managerName}}"
                      },
                      "leaveAccounts": "{{userData.leaveBalance}}",
                      "organization": {
                        "managerName": "{{userData.orgChart.manager.realName}}"
                      }
                    }
                  }
                }
              ],
              "edges": [
                { "from": "start", "to": "load_user" },
                { "from": "load_user", "to": "end" }
              ]
            }
            """;
        long versionId = createVersionWithWorkflow(adminSession, agentId, 1, workflowConfigJson);

        MvcResult result = mockMvc.perform(post("/api/admin/agents/workflow-run")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "versionId": %d,
                      "inputJson": "{\\"currentUserId\\":2003,\\"userId\\":2003}",
                      "modelConfigJson": "{\\"provider\\":\\"mock\\",\\"model\\":\\"agent-test\\"}",
                      "systemPrompt": "你是企业协同助手。",
                      "workflowConfigJson": "%s"
                    }
                    """.formatted(versionId, escapeJsonString(workflowConfigJson))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.success").value(true))
            .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        JsonNode reply = objectMapper.readTree(response.path("data").path("finalReply").asText());
        assertEquals("张三", reply.path("employee").path("name").asText());
        assertTrue(reply.path("employee").path("department").asText().length() > 0);
        JsonNode item = reply.path("leaveAccounts").get(0);
        assertTrue(item.path("leaveName").asText().length() > 0);
        assertTrue(item.has("availableMinutes"));
        assertTrue(item.has("frozenMinutes"));
        assertFalse(item.has("accountId"));
        assertFalse(item.has("userId"));
        assertFalse(item.has("leaveTypeId"));
        assertEquals("李四", reply.path("organization").path("managerName").asText());
    }

    @Test
    @DisplayName("后台应能更新草稿版本的DAG配置且不能更新已发布版本")
    void adminShouldUpdateDraftVersionWorkflowAndRejectPublishedVersionUpdate() throws Exception {
        Cookie adminSession = loginAdmin();
        long agentId = createAgent(adminSession, "DAG_EDIT_AGENT", "DAG编辑助手", "QA");
        long versionId = createVersion(adminSession, agentId, 1);
        String workflow = """
            {
              "nodes": [
                { "id": "start", "type": "START" },
                { "id": "answer", "type": "LLM", "config": { "prompt": "{{input.query}}" }, "outputKey": "answer" },
                { "id": "end", "type": "END", "config": { "reply": "{{answer}}" } }
              ],
              "edges": [
                { "from": "start", "to": "answer" },
                { "from": "answer", "to": "end" }
              ]
            }
            """;

        mockMvc.perform(put("/api/admin/agents/versions/{versionId}", versionId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "modelConfigJson": "{\\"provider\\":\\"mock\\",\\"model\\":\\"agent-test\\"}",
                      "systemPrompt": "更新后的系统提示",
                      "workflowConfigJson": "%s"
                    }
                    """.formatted(escapeJsonString(workflow))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data.versionId", versionId))
            .andExpect(jsonPath("$.data.systemPrompt").value("更新后的系统提示"))
            .andExpect(jsonPath("$.data.workflowConfigJson").value(containsString("answer")));

        grantAcl(adminSession, agentId, "ADMIN_USER", 1001L, "manage");
        publishVersion(adminSession, versionId);

        mockMvc.perform(put("/api/admin/agents/versions/{versionId}", versionId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "systemPrompt": "发布后不能修改"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409))
            .andExpect(jsonPath("$.message").value("只有草稿版本可以修改"));
    }

    @Test
    @DisplayName("后台应能按关键字查询Agent列表、详情并停用/启用已发布Agent")
    void adminShouldListGetDisableAndEnablePublishedAgent() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long agentId = createPublishedAgentWithoutTool(adminSession, "ADMIN_MANAGED_AGENT", "后台治理助手");
        grantAcl(adminSession, agentId, "USER", EMPLOYEE_USER_ID, "use");
        createVersion(adminSession, agentId, 2, "6.0.1");

        mockMvc.perform(get("/api/admin/agents")
                .cookie(adminSession)
                .param("pageNo", "1")
                .param("pageSize", "20")
                .param("keyword", "治理"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.list", hasSize(1)))
            .andExpect(jsonLong("$.data.list[0].agentId", agentId))
            .andExpect(jsonPath("$.data.list[0].agentCode").value("ADMIN_MANAGED_AGENT"))
            .andExpect(jsonPath("$.data.list[0].publishedVersionNo").value(1))
            .andExpect(jsonPath("$.data.list[0].status").value("ACTIVE"));

        mockMvc.perform(get("/api/admin/agents/{agentId}/versions", agentId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].versionNo").value(2))
            .andExpect(jsonPath("$.data[0].versionLabel").value("6.0.1"))
            .andExpect(jsonPath("$.data[0].publishStatus").value("DRAFT"));

        mockMvc.perform(get("/api/admin/agents/{agentId}", agentId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data.agentId", agentId))
            .andExpect(jsonPath("$.data.agentName").value("后台治理助手"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/api/admin/agents/{agentId}/disable", agentId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data.agentId", agentId))
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
            .andExpect(jsonPath("$.message").value("AI助手未发布或未启用"));

        mockMvc.perform(post("/api/admin/agents/{agentId}/enable", agentId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data.agentId", agentId))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/employee/agents")
                .cookie(employeeSession)
                .param("keyword", "治理"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonLong("$.data[0].agentId", agentId));
    }

    @Test
    @DisplayName("员工应能按关键字搜索可添加到IM的已授权Agent")
    void employeeShouldSearchUsableAgentsForImAdd() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long hrAgentId = createPublishedAgentWithoutTool(adminSession, "HR_FLOW_AGENT", "HR流程助手");
        long fileAgentId = createPublishedAgentWithoutTool(adminSession, "FILE_AGENT", "文件助手");
        grantAcl(adminSession, hrAgentId, "USER", EMPLOYEE_USER_ID, "use");
        grantAcl(adminSession, fileAgentId, "USER", EMPLOYEE_USER_ID, "use");

        mockMvc.perform(get("/api/employee/agents")
                .cookie(employeeSession)
                .param("keyword", "HR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonLong("$.data[0].agentId", hrAgentId))
            .andExpect(jsonPath("$.data[0].agentCode").value("HR_FLOW_AGENT"));
    }

    @Test
    @DisplayName("角色 ACL 应区分普通员工和领导")
    void roleAclShouldAuthorizeEmployeeAndLeader() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        Cookie managerSession = loginEmployee("lisi.manager");
        long employeeAgentId = createPublishedAgentWithoutTool(adminSession, "ROLE_EMPLOYEE_AGENT", "员工角色助手");
        long leaderAgentId = createPublishedAgentWithoutTool(adminSession, "ROLE_LEADER_AGENT", "领导角色助手");
        grantAcl(adminSession, employeeAgentId, "ROLE", 1L, "use");
        grantAcl(adminSession, leaderAgentId, "ROLE", 2L, "use");

        mockMvc.perform(get("/api/employee/agents")
                .cookie(employeeSession)
                .param("keyword", "ROLE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[*].agentCode", containsInAnyOrder("ROLE_EMPLOYEE_AGENT")));

        mockMvc.perform(post("/api/employee/agents/{agentId}/tasks", leaderAgentId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "triggerType": "MANUAL",
                      "contextType": "USER_QUERY",
                      "inputPayloadJson": "{\\"query\\":\\"普通员工不可调用领导Agent\\"}"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message").value("无权使用该AI助手"));

        mockMvc.perform(get("/api/employee/agents")
                .cookie(managerSession)
                .param("keyword", "ROLE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[*].agentCode", containsInAnyOrder("ROLE_EMPLOYEE_AGENT", "ROLE_LEADER_AGENT")));
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
            .andExpect(jsonLong("$.data.list[0].taskId", taskId))
            .andExpect(jsonLong("$.data.list[0].agentId", agentId))
            .andExpect(jsonPath("$.data.list[0].agentCode").value("TASK_QUERY_AGENT"))
            .andExpect(jsonPath("$.data.list[0].agentName").value("任务查询助手"))
            .andExpect(jsonPath("$.data.list[0].agentVersionId").isString())
            .andExpect(jsonPath("$.data.list[0].triggerType").value("MANUAL"))
            .andExpect(jsonPath("$.data.list[0].contextType").value("USER_QUERY"))
            .andExpect(jsonPath("$.data.list[0].contextId").value(2003))
            .andExpect(jsonPath("$.data.list[0].triggeredByType").value("USER"))
            .andExpect(jsonLong("$.data.list[0].triggeredById", EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data.list[0].status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data.list[0].toolCallCount").value(0));

        mockMvc.perform(get("/api/admin/agent-tasks/{taskId}", taskId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data.taskId", taskId))
            .andExpect(jsonPath("$.data.agentCode").value("TASK_QUERY_AGENT"))
            .andExpect(jsonLong("$.data.triggeredById", EMPLOYEE_USER_ID))
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
            .andExpect(jsonPath("$.message").value("AI任务状态不支持"));
    }

    @Test
    @DisplayName("后台应能查看Agent任务工具调用明细和审计轨迹")
    void adminShouldGetAgentTaskToolCallsAndAudits() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long taskId = createWaitingTask(
            adminSession,
            employeeSession,
            "TASK_TRACE_AGENT",
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
            .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/admin/agent-tasks/{taskId}/tool-calls", taskId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonLong("$.data[0].taskId", taskId))
            .andExpect(jsonPath("$.data[0].toolCode").value("ORG_GET_USER_SUMMARY"))
            .andExpect(jsonPath("$.data[0].toolName").value("组织用户摘要"))
            .andExpect(jsonPath("$.data[0].toolType").value("SPI"))
            .andExpect(jsonPath("$.data[0].riskLevel").value("HIGH"))
            .andExpect(jsonPath("$.data[0].readOnlyFlag").value(false))
            .andExpect(jsonPath("$.data[0].approvalStatus").value("APPROVED"))
            .andExpect(jsonPath("$.data[0].resultStatus").value("SUCCESS"))
            .andExpect(jsonPath("$.data[0].requestPayloadJson").value(containsString("\"userId\"")))
            .andExpect(jsonPath("$.data[0].requestPayloadJson").value(containsString("2003")))
            .andExpect(jsonPath("$.data[0].responsePayloadJson").isString())
            .andExpect(jsonPath("$.data[0].calledAt").isString());

        mockMvc.perform(get("/api/admin/agent-tasks/{taskId}/audits", taskId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.length()").value(5))
            .andExpect(jsonLong("$.data[0].taskId", taskId))
            .andExpect(jsonPath("$.data[0].actionType").value("TASK_CREATED"))
            .andExpect(jsonPath("$.data[0].riskFlag").value(false))
            .andExpect(jsonPath("$.data[1].actionType").value("TASK_WAITING_HUMAN_CONFIRM"))
            .andExpect(jsonPath("$.data[1].riskFlag").value(true))
            .andExpect(jsonPath("$.data[2].actionType").value("TASK_HUMAN_CONFIRMED"))
            .andExpect(jsonPath("$.data[3].actionType").value("TASK_RUNNING"))
            .andExpect(jsonPath("$.data[4].actionType").value("TASK_SUCCEEDED"))
            .andExpect(jsonPath("$.data[4].occurredAt").isString());
    }

    @Test
    @DisplayName("后台应能查看Agent任务运行指标和失败聚合")
    void adminShouldGetAgentTaskMetricsAndFailureSummary() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long successAgentId = createPublishedAgentWithoutTool(adminSession, "METRICS_SUCCESS_AGENT", "指标成功助手");
        grantAcl(adminSession, successAgentId, "USER", EMPLOYEE_USER_ID, "use");

        mockMvc.perform(post("/api/employee/agents/{agentId}/tasks", successAgentId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "triggerType": "MANUAL",
                      "contextType": "USER_QUERY",
                      "inputPayloadJson": "{\\"query\\":\\"成功任务\\"}"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));

        long failedToolId = createTool(adminSession, "METRICS_UNKNOWN_SPI_TOOL", "指标未知SPI工具", "SPI", "READONLY", true);
        long failedAgentId = createAgent(adminSession, "METRICS_FAILED_AGENT", "指标失败助手", "QA");
        long failedVersionId = createVersion(adminSession, failedAgentId, 1);
        bindTool(adminSession, failedVersionId, failedToolId, "AUTO");
        grantAcl(adminSession, failedAgentId, "USER", EMPLOYEE_USER_ID, "use");
        publishVersion(adminSession, failedVersionId);
        mockMvc.perform(post("/api/employee/agents/{agentId}/tasks", failedAgentId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "triggerType": "MANUAL",
                      "contextType": "USER_QUERY",
                      "inputPayloadJson": "{\\"query\\":\\"失败任务\\"}",
                      "toolCalls": [
                        {
                          "toolCode": "METRICS_UNKNOWN_SPI_TOOL",
                          "requestPayloadJson": "{\\"userId\\":2003}"
                        }
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("FAILED"));

        createWaitingTask(
            adminSession,
            employeeSession,
            "METRICS_WAITING_AGENT",
            "METRICS_CONNECTOR_WRITE_BACK",
            "指标连接器写入",
            "CONNECTOR",
            "{\"action\":\"write\"}"
        );

        mockMvc.perform(get("/api/admin/agent-tasks/metrics")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.totalTaskCount").value(3))
            .andExpect(jsonPath("$.data.succeededTaskCount").value(1))
            .andExpect(jsonPath("$.data.failedTaskCount").value(1))
            .andExpect(jsonPath("$.data.waitingHumanConfirmTaskCount").value(1))
            .andExpect(jsonPath("$.data.cancelledTaskCount").value(0))
            .andExpect(jsonPath("$.data.rejectedTaskCount").value(0))
            .andExpect(jsonPath("$.data.runningTaskCount").value(0))
            .andExpect(jsonPath("$.data.pendingTaskCount").value(0))
            .andExpect(jsonPath("$.data.totalToolCallCount").value(2))
            .andExpect(jsonPath("$.data.failedToolCallCount").value(1))
            .andExpect(jsonPath("$.data.averageFinishedDurationMillis").isNumber())
            .andExpect(jsonPath("$.data.lastTaskCreatedAt").isString());

        mockMvc.perform(get("/api/admin/agent-tasks/failure-summary")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].errorMessage").value("暂不支持该SPI工具执行"))
            .andExpect(jsonPath("$.data[0].taskCount").value(1))
            .andExpect(jsonPath("$.data[0].toolCallCount").value(1))
            .andExpect(jsonPath("$.data[0].firstOccurredAt").isString())
            .andExpect(jsonPath("$.data[0].lastOccurredAt").isString());
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
            .andExpect(jsonPath("$.message").value("无权使用该AI助手"));
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
            .andExpect(jsonPath("$.message").value("AI助手未授权调用该工具"));

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
            .andExpect(jsonPath("$.message").value("AI任务没有可继续执行的待确认工具"));

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
            .andExpect(jsonPath("$.message").value("无权操作该AI任务"));

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
            .andExpect(jsonLong("$.data[0].agentId", agentId));

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
            .andExpect(jsonPath("$.message").value("无权使用该AI助手"));
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
        grantAcl(adminSession, agentId, "ADMIN_USER", 1001L, "manage");
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
        return createVersion(adminSession, agentId, versionNo, null);
    }

    private long createVersion(Cookie adminSession, long agentId, int versionNo, String versionLabel) throws Exception {
        String labelLine = versionLabel == null ? "" : """
                      "versionLabel": "%s",
            """.formatted(escapeJsonString(versionLabel));
        MvcResult result = mockMvc.perform(post("/api/admin/agents/{agentId}/versions", agentId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "versionNo": %d,
            %s
                      "modelConfigJson": "{\\"provider\\":\\"mock\\",\\"model\\":\\"agent-test\\"}",
                      "systemPrompt": "你是企业协同助手。",
                      "workflowConfigJson": "{\\"mode\\":\\"tool-first\\"}"
                    }
                    """.formatted(versionNo, labelLine)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return data(result).get("versionId").asLong();
    }

    private long createVersionWithWorkflow(Cookie adminSession, long agentId, int versionNo, String workflowConfigJson)
        throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/agents/{agentId}/versions", agentId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "versionNo": %d,
                      "modelConfigJson": "{\\"provider\\":\\"mock\\",\\"model\\":\\"agent-test\\"}",
                      "systemPrompt": "你是企业协同助手。",
                      "workflowConfigJson": "%s"
                    }
                    """.formatted(versionNo, escapeJsonString(workflowConfigJson))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return data(result).get("versionId").asLong();
    }

    private void runWorkflowTest(Cookie adminSession, long versionId, String workflowConfigJson) throws Exception {
        runWorkflowTest(adminSession, versionId, workflowConfigJson, "{\"query\":\"测试\"}");
    }

    private void runWorkflowTest(Cookie adminSession, long versionId, String workflowConfigJson, String inputJson) throws Exception {
        mockMvc.perform(post("/api/admin/agents/workflow-run")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "versionId": %d,
                      "inputJson": %s,
                      "modelConfigJson": "{\\"provider\\":\\"mock\\",\\"model\\":\\"agent-test\\"}",
                      "systemPrompt": "你是企业协同助手。",
                      "workflowConfigJson": "%s"
                    }
                    """.formatted(versionId, objectMapper.writeValueAsString(inputJson), escapeJsonString(workflowConfigJson))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.success").value(true));
    }

    private String simpleWorkflow(String prompt) {
        return """
            {
              "nodes": [
                { "id": "start", "type": "START" },
                { "id": "answer", "type": "LLM", "config": { "prompt": "%s" }, "outputKey": "answer" },
                { "id": "end", "type": "END", "config": { "reply": "{{answer}}" } }
              ],
              "edges": [
                { "from": "start", "to": "answer" },
                { "from": "answer", "to": "end" }
              ]
            }
            """.formatted(prompt);
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
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    @TestConfiguration
    static class TestAgentModelConfiguration {

        @Bean
        @Primary
        AgentModelClient testAgentModelClient() {
            return new AgentModelClient() {
                @Override
                public AgentModelResponse generateReply(AgentModelRequest request) {
                    if (request.userMessage().contains("FORMAT_ARRAY_TEST")) {
                        return new AgentModelResponse("""
                            {"leaveAccounts":[{"leaveName":"演示年假","availableHours":"12","frozenHours":4,"accountId":999,"userId":22002}],"extra":"x"}
                            """);
                    }
                    return new AgentModelResponse(request.userMessage());
                }
            };
        }
    }
}
