package com.workforcehub.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.support.WorkflowFlowableIntegrationTestSupport;
import com.workforcehub.support.WorkflowFlowableTestConfiguration;
import jakarta.servlet.http.Cookie;
import org.flowable.engine.RepositoryService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * module-workflow 后台流程模板治理接口集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
@Import(WorkflowFlowableTestConfiguration.class)
class ModuleWorkflowAdminApiIntegrationTest extends WorkflowFlowableIntegrationTestSupport {

    private static final long ADMIN_USER_ID = 8001L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RepositoryService repositoryService;

    @Test
    @DisplayName("后台应能创建流程类型、模板、草稿版本并发布为当前有效版本")
    void adminShouldCreateAndPublishWorkflowTemplateVersion() throws Exception {
        Cookie adminSession = loginAdmin();
        assertNotNull(repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey("leave_manager_approval")
            .latestVersion()
            .singleResult(), "测试环境应自动部署请假审批 BPMN 定义");

        long processTypeId = createProcessType(adminSession, "LEAVE", "请假流程", "LEAVE");

        mockMvc.perform(get("/api/admin/workflow/process-types")
                .cookie(adminSession)
                .param("processCode", "LEAVE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].processTypeId").value(processTypeId))
            .andExpect(jsonPath("$.data[0].processCode").value("LEAVE"))
            .andExpect(jsonPath("$.data[0].processName").value("请假流程"))
            .andExpect(jsonPath("$.data[0].bizType").value("LEAVE"))
            .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));

        long templateId = createTemplate(adminSession, processTypeId, "LEAVE_MANAGER_APPROVAL", "请假直属上级审批");
        long versionId = createTemplateVersion(adminSession, templateId, 1, "ANNUAL_LEAVE", 480);

        mockMvc.perform(post("/api/admin/workflow/template-versions/{versionId}/publish", versionId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "flowableDefinitionKey": "leave_manager_approval"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.versionId").value(versionId))
            .andExpect(jsonPath("$.data.templateId").value(templateId))
            .andExpect(jsonPath("$.data.versionNo").value(1))
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.flowableDefinitionKey").value("leave_manager_approval"))
            .andExpect(jsonPath("$.data.flowableDefinitionId").isString())
            .andExpect(jsonPath("$.data.effectiveFrom").isString());

        mockMvc.perform(get("/api/admin/workflow/templates")
                .cookie(adminSession)
                .param("processCode", "LEAVE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].templateId").value(templateId))
            .andExpect(jsonPath("$.data[0].processCode").value("LEAVE"))
            .andExpect(jsonPath("$.data[0].templateCode").value("LEAVE_MANAGER_APPROVAL"))
            .andExpect(jsonPath("$.data[0].currentVersion.versionId").value(versionId))
            .andExpect(jsonPath("$.data[0].currentVersion.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/admin/workflow/templates/{templateId}/versions", templateId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].versionId").value(versionId))
            .andExpect(jsonPath("$.data[0].status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data[0].ruleSnapshotJson").value("{\"leaveCode\":\"ANNUAL_LEAVE\",\"maxDurationMinutes\":480}"));

        assertEquals("PUBLISHED", versionStatus(versionId));
        assertEquals(ADMIN_USER_ID, publishedBy(versionId));
        assertEquals(1, currentPublishedVersionCount(templateId));
        assertEquals(versionId, currentPublishedTemplateVersionId("LEAVE"));
    }

    @Test
    @DisplayName("发布不存在的Flowable定义应返回业务错误且保持草稿")
    void publishingUnknownFlowableDefinitionShouldFail() throws Exception {
        Cookie adminSession = loginAdmin();
        long processTypeId = createProcessType(adminSession, "LEAVE", "请假流程", "LEAVE");
        long templateId = createTemplate(adminSession, processTypeId, "LEAVE_MANAGER_APPROVAL", "请假直属上级审批");
        long versionId = createTemplateVersion(adminSession, templateId, 1, "ANNUAL_LEAVE", 480);

        mockMvc.perform(post("/api/admin/workflow/template-versions/{versionId}/publish", versionId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "flowableDefinitionKey": "missing_definition_key"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.message").value("Flowable流程定义不存在"))
            .andExpect(jsonPath("$.data").doesNotExist());

        assertEquals("DRAFT", versionStatus(versionId));
        assertEquals(0, currentPublishedVersionCount(templateId));
    }

    @Test
    @DisplayName("同一模板发布新版本后旧发布版本应自动失效")
    void publishingNewVersionShouldRetirePreviousPublishedVersion() throws Exception {
        Cookie adminSession = loginAdmin();
        long processTypeId = createProcessType(adminSession, "LEAVE", "请假流程", "LEAVE");
        long templateId = createTemplate(adminSession, processTypeId, "LEAVE_MANAGER_APPROVAL", "请假直属上级审批");
        long versionOneId = createTemplateVersion(adminSession, templateId, 1, "ANNUAL_LEAVE", 480);
        publishVersion(adminSession, versionOneId, 1);

        long versionTwoId = createTemplateVersion(adminSession, templateId, 2, "ANNUAL_LEAVE", 960);
        publishVersion(adminSession, versionTwoId, 2);

        assertEquals("ARCHIVED", versionStatus(versionOneId));
        assertTrue(hasEffectiveTo(versionOneId), "旧版本应写入 effective_to，避免出现多个当前有效发布版本");
        assertEquals("PUBLISHED", versionStatus(versionTwoId));
        assertEquals(1, currentPublishedVersionCount(templateId));
        assertEquals(versionTwoId, currentPublishedTemplateVersionId("LEAVE"));
    }

    private long createProcessType(Cookie adminSession, String processCode, String processName, String bizType)
        throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/workflow/process-types")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "processCode": "%s",
                      "processName": "%s",
                      "bizType": "%s",
                      "status": "ACTIVE"
                    }
                    """.formatted(processCode, processName, bizType)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.processTypeId").isNumber())
            .andExpect(jsonPath("$.data.processCode").value(processCode))
            .andReturn();
        return data(result).get("processTypeId").asLong();
    }

    private long createTemplate(Cookie adminSession, long processTypeId, String templateCode, String templateName)
        throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/workflow/templates")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "processTypeId": %d,
                      "templateCode": "%s",
                      "templateName": "%s",
                      "status": "ACTIVE"
                    }
                    """.formatted(processTypeId, templateCode, templateName)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.templateId").isNumber())
            .andExpect(jsonPath("$.data.templateCode").value(templateCode))
            .andReturn();
        return data(result).get("templateId").asLong();
    }

    private long createTemplateVersion(Cookie adminSession,
                                       long templateId,
                                       int versionNo,
                                       String leaveCode,
                                       int maxDurationMinutes)
        throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/workflow/templates/{templateId}/versions", templateId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "versionNo": %d,
                      "formSchemaJson": "{\\"formKey\\":\\"leave-request\\"}",
                      "ruleSnapshotJson": "{\\"leaveCode\\":\\"%s\\",\\"maxDurationMinutes\\":%d}"
                    }
                    """.formatted(versionNo, leaveCode, maxDurationMinutes)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.versionId").isNumber())
            .andExpect(jsonPath("$.data.templateId").value(templateId))
            .andExpect(jsonPath("$.data.versionNo").value(versionNo))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andReturn();
        return data(result).get("versionId").asLong();
    }

    private void publishVersion(Cookie adminSession, long versionId, int versionNo) throws Exception {
        mockMvc.perform(post("/api/admin/workflow/template-versions/{versionId}/publish", versionId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "flowableDefinitionKey": "leave_manager_approval"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.versionNo").value(versionNo))
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
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
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(cookie().exists("SESSION"))
            .andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private String versionStatus(long versionId) {
        return jdbcTemplate.queryForObject("""
            SELECT status
            FROM wf_template_version
            WHERE id = ?
            """, String.class, versionId);
    }

    private Long publishedBy(long versionId) {
        return jdbcTemplate.queryForObject("""
            SELECT published_by
            FROM wf_template_version
            WHERE id = ?
            """, Long.class, versionId);
    }

    private boolean hasEffectiveTo(long versionId) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM wf_template_version
            WHERE id = ? AND effective_to IS NOT NULL
            """, Integer.class, versionId);
        return count != null && count > 0;
    }

    private int currentPublishedVersionCount(long templateId) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM wf_template_version
            WHERE template_id = ?
              AND status = 'PUBLISHED'
              AND effective_from <= NOW()
              AND (effective_to IS NULL OR effective_to > NOW())
            """, Integer.class, templateId);
        return count == null ? 0 : count;
    }

    private long currentPublishedTemplateVersionId(String processCode) {
        return jdbcTemplate.queryForObject("""
            SELECT wtv.id
            FROM wf_process_type wpt
            JOIN wf_template wt ON wt.process_type_id = wpt.id
            JOIN wf_template_version wtv ON wtv.template_id = wt.id
            WHERE wpt.process_code = ?
              AND wtv.status = 'PUBLISHED'
              AND wtv.effective_from <= NOW()
              AND (wtv.effective_to IS NULL OR wtv.effective_to > NOW())
            ORDER BY wtv.version_no DESC, wtv.id DESC
            LIMIT 1
            """, Long.class, processCode);
    }
}
