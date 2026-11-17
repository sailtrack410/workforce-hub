package com.workforcehub.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.agent.repository.AgentToolBindingRow;
import com.workforcehub.agent.service.AgentToolExecutionService;
import com.workforcehub.attendance.service.AttendanceService;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.leave.service.LeaveService;
import com.workforcehub.org.service.OrgDirectoryService;
import com.workforcehub.ragflow.service.KnowledgeAccessTestFixture;
import com.workforcehub.ragflow.service.RagflowKnowledgeSearchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AgentToolKnowledgeAuthorizationTest {
    private static final AgentToolBindingRow BINDING = new AgentToolBindingRow(
        1L, 2L, 3L, "KNOWLEDGE_SEARCH_CHUNKS", "知识检索", "BUILTIN", "LOW", true,
        "AUTO_APPROVE", "ACTIVE", null, null);
    private static final String INPUT = """
        {"spaceId":1,"query":"制度","topK":5,"currentUserId":8,"operatorUserId":8}
        """;
    private final ObjectMapper mapper = new ObjectMapper();
    private KnowledgeAccessTestFixture f;
    private AgentToolExecutionService tool;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        f = new KnowledgeAccessTestFixture();
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("knowledgeSearchService", f.search);
        ObjectProvider<LeaveService> leave = beans.getBeanProvider(LeaveService.class);
        ObjectProvider<AttendanceService> attendance = beans.getBeanProvider(AttendanceService.class);
        ObjectProvider<RagflowKnowledgeSearchService> knowledge = beans.getBeanProvider(RagflowKnowledgeSearchService.class);
        tool = new AgentToolExecutionService(mapper, mock(OrgDirectoryService.class), leave, attendance, knowledge);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void toolPassesTrustedOperatorToActualSearchWithoutNeedingAnAsyncThreadPrincipal() throws Exception {
        f.grant("USER", 7, "read");
        var result = mapper.readTree(tool.execute(BINDING, INPUT, 7L));
        assertEquals("SUCCESS", result.path("status").asText());
        assertEquals("授权知识正文", result.path("result").get(0).asText());
    }

    @Test
    void payloadIdentityDoesNotReplaceTrustedOperatorOrGrantAgentUserKnowledgeAccess() {
        f.grant("USER", 8, "read");
        BizException denied = assertThrows(BizException.class, () -> tool.execute(BINDING, INPUT, 7L));
        assertEquals(403, denied.getHttpStatus().value());
        verifyNoInteractions(f.resolver, f.embedding, f.vectors);
    }

    @Test
    void missingOperatorCannotTurnMissingOrEmployeeContextIntoAnAdminBypass() {
        assertThrows(BizException.class, () -> tool.execute(BINDING, INPUT, null));
        f.employee(8);
        f.grant("USER", 8, "manage");
        assertThrows(BizException.class, () -> tool.execute(BINDING, INPUT, null));
        f.admin(List.of(), List.of("ADMIN_AGENT_MANAGE"));
        assertThrows(BizException.class, () -> tool.execute(BINDING, INPUT, null));
        verifyNoInteractions(f.resolver, f.embedding, f.vectors);
    }

    @Test
    void explicitAdminPreviewOnlyWorksForKnowledgeManagementPermission() throws Exception {
        f.admin(List.of(), List.of("ADMIN_RAGFLOW_MANAGE"));
        assertEquals("授权知识正文", mapper.readTree(tool.execute(BINDING, INPUT, null)).path("result").get(0).asText());
    }
}
