package com.workforcehub.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.agent.model.AgentModelRequest;
import com.workforcehub.agent.model.AgentModelResponse;
import com.workforcehub.agent.model.OpenAiCompatibleAgentModelClient;
import com.workforcehub.agent.repository.AgentRepository;
import com.workforcehub.agent.repository.AgentRow;
import com.workforcehub.ragflow.repository.RagflowGlobalSettingRow;
import com.workforcehub.ragflow.repository.RagflowRepository;
import com.workforcehub.workflow.repository.WorkflowLeaveDetailRow;
import com.workforcehub.workflow.repository.WorkflowRepository;
import com.workforcehub.workflow.repository.WorkflowRequestRow;
import com.workforcehub.workflow.service.WorkflowAgentResultCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowAgentPreReviewTriggerServiceTest {

    private final AgentRepository agentRepository = mock(AgentRepository.class);
    private final WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
    private final RagflowRepository ragflowRepository = mock(RagflowRepository.class);
    private final OpenAiCompatibleAgentModelClient agentModelClient = mock(OpenAiCompatibleAgentModelClient.class);
    private final WorkflowAgentResultCallback callback = mock(WorkflowAgentResultCallback.class);
    private final ObjectProvider<WorkflowAgentResultCallback> callbackProvider = mock(ObjectProvider.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private WorkflowAgentPreReviewTriggerService service;

    @BeforeEach
    void setUp() {
        when(callbackProvider.getIfAvailable()).thenReturn(callback);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        when(agentRepository.findActivePreReviewAgent()).thenReturn(new AgentRow(
            1001L, "oa-pre-review", "OA预审", null, "PRE_REVIEW",
            "SYSTEM", 2001L, 1, "v1", "ACTIVE",
            LocalDateTime.now(), LocalDateTime.now()
        ));
        when(ragflowRepository.getGlobalSetting()).thenReturn(new RagflowGlobalSettingRow(
            1L, "https://ai.local/v1", true, "vision-chat",
            null, false, null, null,
            false, null, false, null,
            800, 100, 5, BigDecimal.ZERO,
            LocalDateTime.now(), LocalDateTime.now()
        ));
        when(ragflowRepository.getRawOcrApiKey()).thenReturn("test-key");
        when(workflowRepository.getRequestByIdOrThrow(3001L)).thenReturn(new WorkflowRequestRow(
            3001L, "WF202607010001", "LEAVE", "PENDING_REVIEW", "张三的请假申请",
            2003L, null, null
        ));
        when(workflowRepository.getLeaveDetailByRequestId(3001L)).thenReturn(new WorkflowLeaveDetailRow(
            9001L,
            LocalDateTime.of(2026, 7, 2, 9, 0),
            LocalDateTime.of(2026, 7, 2, 18, 0),
            480,
            "家中有事"
        ));
        service = new WorkflowAgentPreReviewTriggerService(
            agentRepository,
            workflowRepository,
            ragflowRepository,
            agentModelClient,
            callbackProvider,
            transactionManager,
            objectMapper
        );
    }

    @Test
    void shouldCallRagflowModelAndApproveWorkflow() throws Exception {
        when(agentModelClient.generateReply(any(AgentModelRequest.class))).thenReturn(new AgentModelResponse("""
            {"decision":"APPROVED","conclusion":"建议进入直属上级审批","reason":"信息完整","suggestion":"核对排班","riskLevel":"低","checks":[{"name":"时长","result":"正常"}]}
            """));

        Long taskId = service.triggerPreReview(3001L, "LEAVE", "LEAVE", 4001L, 2003L);

        assertNotNull(taskId);
        ArgumentCaptor<AgentModelRequest> modelRequest = ArgumentCaptor.forClass(AgentModelRequest.class);
        verify(agentModelClient).generateReply(modelRequest.capture());
        JsonNode config = objectMapper.readTree(modelRequest.getValue().modelConfigJson());
        assertEquals("https://ai.local/v1", config.path("baseUrl").asText());
        assertEquals("vision-chat", config.path("model").asText());
        ArgumentCaptor<String> decisionJson = ArgumentCaptor.forClass(String.class);
        verify(callback).onPreReviewApproved(eq(3001L), eq(taskId), decisionJson.capture());
        JsonNode decision = objectMapper.readTree(decisionJson.getValue());
        assertEquals("APPROVED", decision.path("decision").asText());
        assertEquals("知识库模型预审", decision.path("reviewMode").asText());
        verify(agentRepository).finishTask(eq(taskId), eq("SUCCEEDED"), eq(null), eq(2003L));
    }

    @Test
    void shouldTurnToHumanWhenModelCallFails() throws Exception {
        when(agentModelClient.generateReply(any(AgentModelRequest.class))).thenThrow(new RuntimeException("模型不可用"));

        Long taskId = service.triggerPreReview(3001L, "LEAVE", "LEAVE", 4001L, 2003L);

        ArgumentCaptor<String> decisionJson = ArgumentCaptor.forClass(String.class);
        verify(callback).onPreReviewToHuman(eq(3001L), eq(taskId), decisionJson.capture());
        JsonNode decision = objectMapper.readTree(decisionJson.getValue());
        assertEquals("TO_HUMAN", decision.path("decision").asText());
        assertEquals("AI预审未完成，转人工复核", decision.path("conclusion").asText());
        verify(agentRepository).finishTask(eq(taskId), eq("FAILED"), eq("模型不可用"), eq(2003L));
    }
}
