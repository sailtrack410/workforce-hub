package com.workforcehub.ragflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.agent.repository.AgentRepository;
import com.workforcehub.auth.security.AdminUserDetailsService;
import com.workforcehub.auth.security.EmployeeUserDetailsService;
import com.workforcehub.auth.security.WfhUserDetails;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.ragflow.dto.RagflowSearchTestRequest;
import com.workforcehub.ragflow.repository.RagflowRepository;
import com.workforcehub.ragflow.repository.RagflowSearchHitRow;
import com.workforcehub.ragflow.repository.RagflowVectorRepository;
import com.workforcehub.ragflow.service.KnowledgeAccessTestFixture;
import com.workforcehub.ragflow.service.RagflowAdminService;
import com.workforcehub.ragflow.service.RagflowConfigResolver;
import com.workforcehub.ragflow.service.RagflowEmbeddingClient;
import com.workforcehub.ragflow.service.RagflowKnowledgeSearchService;
import com.workforcehub.support.LocalIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "workforce.ragflow.embedding.validate-on-create=false")
@AutoConfigureMockMvc
@ActiveProfiles("api")
class RagflowKnowledgeAccessIntegrationTest extends LocalIntegrationTestSupport {
    private static final long SPACE_ID = 94_016L;
    private static final long DOCUMENT_ID = 94_017L;
    private static final long DEPARTMENT_ID = 94_018L;
    private static final long POSITION_ID = 94_019L;
    private static final long KNOWLEDGE_ADMIN_ID = 94_020L;
    private static final long AGENT_ADMIN_ID = 94_021L;
    private static final long AGENT_ID = 94_030L;
    private static final long VERSION_ID = 94_031L;
    private static final long TOOL_ID = 94_032L;
    private static final long EMPLOYEE_ID = 2003L;
    private static final String SECRET = "ACL_INTEGRATION_SECRET";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private RagflowRepository repository;
    @Autowired private RagflowKnowledgeSearchService search;
    @Autowired private RagflowAdminService adminService;
    @Autowired private AgentRepository agents;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EmployeeUserDetailsService employees;
    @Autowired private AdminUserDetailsService admins;
    @MockBean private RagflowEmbeddingClient embedding;
    @MockBean private RagflowVectorRepository vectors;
    @MockBean private RagflowConfigResolver resolver;

    @BeforeEach
    void seedKnowledgeAccess() {
        SecurityContextHolder.clearContext();
        jdbc.update("""
            INSERT INTO org_department (id, tree_path, dept_code, dept_name)
            VALUES (?, '/94018/', 'ACL_INTEGRATION_DEPT', '知识权限测试部门')
            """, DEPARTMENT_ID);
        jdbc.update("INSERT INTO org_position (id, position_code, position_name) VALUES (?, 'ACL_INTEGRATION_POSITION', '知识权限测试岗位')", POSITION_ID);
        jdbc.update("UPDATE org_user SET primary_dept_id=?, position_id=?, status='ACTIVE', employment_status='ACTIVE' WHERE id=?", DEPARTMENT_ID, POSITION_ID, EMPLOYEE_ID);
        jdbc.update("UPDATE auth_user_account SET account_status='ACTIVE', is_deleted=0 WHERE user_id=?", EMPLOYEE_ID);
        jdbc.update("""
            INSERT INTO kb_space (id, space_name, space_code, visibility, owner_type, owner_id, created_by)
            VALUES (?, '知识权限集成测试', 'ACL_MYSQL_INTEGRATION', 'PRIVATE', 'ADMIN', ?, ?)
            """, SPACE_ID, EMPLOYEE_ID, EMPLOYEE_ID);
        jdbc.update("INSERT INTO kb_document (id, space_id, source_type, title) VALUES (?, ?, 'FILE', '权限测试正文')", DOCUMENT_ID, SPACE_ID);
        seedAdmin(KNOWLEDGE_ADMIN_ID, "acl.knowledge.admin", "ACL_KNOWLEDGE_TEST", "ADMIN_RAGFLOW_MANAGE");
        seedAdmin(AGENT_ADMIN_ID, "acl.agent.admin", "ACL_AGENT_TEST", "ADMIN_AGENT_MANAGE");
        KnowledgeAccessTestFixture.stubRetrievalConfig(resolver);
        when(embedding.embedOne(any(), anyLong(), anyInt())).thenReturn(List.of(0.1, 0.2));
        when(vectors.search(eq(SPACE_ID), anyList(), anyInt(), any())).thenReturn(List.of(
            new RagflowSearchHitRow(94_099L, DOCUMENT_ID, null, 1, SECRET, 1, BigDecimal.ONE)));
    }

    @AfterEach
    void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mysqlPrivateOwnerAndActualSearchBeanRespectTypedEmployeeOwnership() {
        assertFalse(repository.canReadSpace(SPACE_ID, EMPLOYEE_ID), "管理员ID数字相同不能充当员工所有者");
        jdbc.update("UPDATE kb_space SET owner_type='USER', owner_id=? WHERE id=?", EMPLOYEE_ID, SPACE_ID);
        assertTrue(repository.canReadSpace(SPACE_ID, EMPLOYEE_ID));
        assertFalse(repository.canReadSpace(SPACE_ID, 2004L));
        SecurityContextHolder.getContext().setAuthentication(employeeAuthentication());
        var hits = search.searchRows(SPACE_ID, "制度", 5);
        assertEquals(SECRET, hits.get(0).contentText());
        assertEquals("权限测试正文", hits.get(0).documentTitle());
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "DEPARTMENT", "POSITION"})
    void mysqlReadAclUsesActualEmployeeAndOrganizationMembership(String subjectType) {
        long subjectId = switch (subjectType) {
            case "DEPARTMENT" -> DEPARTMENT_ID;
            case "POSITION" -> POSITION_ID;
            default -> EMPLOYEE_ID;
        };
        grant(subjectType, subjectId);
        assertTrue(repository.canReadSpace(SPACE_ID, EMPLOYEE_ID));
        assertFalse(repository.canReadSpace(SPACE_ID, 2004L));
        assertEquals(List.of(SECRET), search.searchChunksForEmployee(EMPLOYEE_ID, SPACE_ID, "制度", 5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACL_REVOKED", "ACCOUNT_DISABLED", "SPACE_DISABLED"})
    void mysqlRevocationPreventsAnyConfigurationEmbeddingOrVectorAccess(String transition) {
        grant("USER", EMPLOYEE_ID);
        assertEquals(List.of(SECRET), search.searchChunksForEmployee(EMPLOYEE_ID, SPACE_ID, "制度", 5));
        switch (transition) {
            case "ACL_REVOKED" -> jdbc.update("DELETE FROM kb_space_acl WHERE space_id=?", SPACE_ID);
            case "ACCOUNT_DISABLED" -> jdbc.update("UPDATE auth_user_account SET account_status='INACTIVE' WHERE user_id=?", EMPLOYEE_ID);
            case "SPACE_DISABLED" -> jdbc.update("UPDATE kb_space SET status='INACTIVE' WHERE id=?", SPACE_ID);
            default -> throw new IllegalArgumentException(transition);
        }
        clearInvocations(resolver, embedding, vectors);
        assertFalse(repository.canReadSpace(SPACE_ID, EMPLOYEE_ID));
        assertEquals(403, assertThrows(BizException.class,
            () -> search.searchRowsForEmployee(EMPLOYEE_ID, SPACE_ID, "制度", 5)).getHttpStatus().value());
        verifyNoInteractions(resolver, embedding, vectors);
    }

    @Test
    void adminSearchTestRequiresKnowledgePermissionBeforeResolvingConfiguration() throws Exception {
        WfhUserDetails agentAdmin = (WfhUserDetails) admins.loadUserByUsername("acl.agent.admin");
        var auth = authenticationFor(agentAdmin);
        mvc.perform(post("/api/admin/ragflow/spaces/{spaceId}/search-test", SPACE_ID)
                .with(authentication(auth)).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"制度\"}"))
            .andExpect(status().isForbidden());
        // 同时验证服务边界，不能仅依赖HTTP过滤器提前拒绝。
        SecurityContextHolder.getContext().setAuthentication(auth);
        RagflowSearchTestRequest request = new RagflowSearchTestRequest();
        request.setQuery("制度");
        assertEquals(403, assertThrows(BizException.class,
            () -> adminService.searchTest(SPACE_ID, request)).getHttpStatus().value());
        verifyNoInteractions(resolver, embedding, vectors);
        SecurityContextHolder.clearContext();
        WfhUserDetails knowledgeAdmin = (WfhUserDetails) admins.loadUserByUsername("acl.knowledge.admin");
        mvc.perform(post("/api/admin/ragflow/spaces/{spaceId}/search-test", SPACE_ID)
                .with(authentication(authenticationFor(knowledgeAdmin))).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"制度\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.hits[0].contentText").value(SECRET));
        verify(embedding).embedOne("制度", 9L, 2);
        verify(vectors).search(eq(SPACE_ID), anyList(), eq(5), any());
    }

    @Test
    void actualEmployeeAgentTaskFailsWithoutKnowledgeAclAndNeverStoresSecret() throws Exception {
        jdbc.update("INSERT INTO agt_agent (id, agent_code, agent_name, agent_type, published_version_id, status) VALUES (?, 'ACL_KNOWLEDGE_AGENT', '知识权限测试助手', 'QA', ?, 'ACTIVE')", AGENT_ID, VERSION_ID);
        jdbc.update("INSERT INTO agt_agent_version (id, agent_id, version_no, workflow_config_json, publish_status) VALUES (?, ?, 1, '{}', 'PUBLISHED')", VERSION_ID, AGENT_ID);
        jdbc.update("INSERT INTO agt_tool (id, tool_code, tool_name, tool_type, risk_level, read_only_flag) VALUES (?, 'KNOWLEDGE_SEARCH_CHUNKS', '知识检索', 'SPI', 'READONLY', 1)", TOOL_ID);
        jdbc.update("INSERT INTO agt_agent_tool (id, agent_version_id, tool_id, permission_mode, approval_mode, status) VALUES (94033, ?, ?, 'ALLOWED', 'AUTO', 'ACTIVE')", VERSION_ID, TOOL_ID);
        jdbc.update("INSERT INTO agt_agent_acl (id, agent_id, subject_type, subject_id, perm_code) VALUES (94034, ?, 'USER', ?, 'use')", AGENT_ID, EMPLOYEE_ID);
        assertTrue(agents.canUseAgent(AGENT_ID, EMPLOYEE_ID));
        assertFalse(repository.canReadSpace(SPACE_ID, EMPLOYEE_ID));
        String toolPayload = mapper.writeValueAsString(Map.of("spaceId", SPACE_ID, "query", "制度", "topK", 5, "operatorUserId", 2004));
        String body = mapper.writeValueAsString(Map.of("triggerType", "MANUAL", "contextType", "USER_QUERY",
            "inputPayloadJson", "{}", "toolCalls", List.of(Map.of("toolCode", "KNOWLEDGE_SEARCH_CHUNKS", "requestPayloadJson", toolPayload))));
        var result = mvc.perform(post("/api/employee/agents/{agentId}/tasks", AGENT_ID)
                .with(authentication(employeeAuthentication())).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andReturn();
        long taskId = mapper.readTree(result.getResponse().getContentAsString()).path("data").path("taskId").asLong();
        assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM agt_task WHERE id=?", String.class, taskId));
        assertEquals(EMPLOYEE_ID, jdbc.queryForObject("SELECT triggered_by_id FROM agt_task WHERE id=?", Long.class, taskId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM agt_tool_call_log WHERE task_id=? AND result_status='FAILED'", Integer.class, taskId));
        String payload = jdbc.queryForObject("SELECT CAST(response_payload_json AS CHAR) FROM agt_tool_call_log WHERE task_id=?", String.class, taskId);
        assertFalse(payload.contains(SECRET));
        assertTrue(payload.contains("FAILED"));
        verifyNoInteractions(resolver, embedding, vectors);
    }

    private void grant(String type, long subjectId) {
        jdbc.update("INSERT INTO kb_space_acl (id, space_id, subject_type, subject_id, perm_code) VALUES (94022, ?, ?, ?, 'read')", SPACE_ID, type, subjectId);
    }

    private void seedAdmin(long id, String username, String roleCode, String permission) {
        jdbc.update("INSERT INTO admin_user (id, username, password_hash, display_name) VALUES (?, ?, 'fixture-unused', '知识权限测试管理员')", id, username);
        jdbc.update("INSERT INTO admin_role (id, role_code, role_name) VALUES (?, ?, '知识权限测试角色')", id, roleCode);
        jdbc.update("INSERT INTO admin_user_role (id, admin_user_id, role_id) VALUES (?, ?, ?)", id, id, id);
        jdbc.update("INSERT INTO admin_role_permission (id, role_id, permission_id) SELECT ?, ?, id FROM admin_permission WHERE perm_code=?", id, id, permission);
    }

    private UsernamePasswordAuthenticationToken employeeAuthentication() {
        return authenticationFor((WfhUserDetails) employees.loadUserByUsername("zhangsan.employee"));
    }

    private UsernamePasswordAuthenticationToken authenticationFor(WfhUserDetails principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
