package com.workforcehub.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.agent.repository.AgentRepository;
import com.workforcehub.agent.repository.AgentRow;
import com.workforcehub.agent.repository.AgentTaskTimeoutRepository;
import com.workforcehub.agent.repository.AiProviderRow;
import com.workforcehub.agent.repository.KnowledgeSpaceHealthRow;
import com.workforcehub.agent.repository.LlmModelRow;
import com.workforcehub.agent.dto.ModelTestResponse;
import com.workforcehub.agent.dto.ProviderModelOptionResponse;
import com.workforcehub.agent.dto.TestLlmModelRequest;
import com.workforcehub.agent.dto.UpdateLlmModelRequest;
import com.workforcehub.agent.workflow.WorkflowExecutor;
import com.workforcehub.agent.workflow.WorkflowParser;
import com.workforcehub.agent.workflow.WorkflowValidator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentServiceAiProviderHealthCheckTest {

    private final AgentRepository agentRepository = mock(AgentRepository.class);
    private final AiModelHealthCheckService healthCheckService = mock(AiModelHealthCheckService.class);
    private final AgentService agentService = new AgentService(
        agentRepository,
        mock(AgentTaskTimeoutRepository.class),
        new ObjectMapper(),
        mock(AgentToolExecutionService.class),
        mock(AgentTaskExecutionService.class),
        mock(WorkflowParser.class),
        mock(WorkflowValidator.class),
        mock(WorkflowExecutor.class),
        healthCheckService
    );

    @Test
    void providerHealthCheckShouldUseProviderProbeWithoutModel() {
        AiProviderRow provider = provider(1001L, "siliconflow", "硅基流动", true);
        when(agentRepository.listAiProviders()).thenReturn(List.of(provider));
        when(healthCheckService.testProvider(provider)).thenReturn(new ModelTestResponse(true, "供应商连通", null, 25));

        agentService.testAiProviders();

        verify(healthCheckService).testProvider(provider);
        verify(agentRepository, never()).getFirstActiveTextModelByProvider(any());
        verify(agentRepository).updateAiProviderTestResult(eq(1001L), eq(true), eq("供应商连通"), eq(0L));
    }

    @Test
    void healthCheckShouldSkipDisabledProviders() {
        AiProviderRow provider = provider(1002L, "openai", "OpenAI", false);
        when(agentRepository.listAiProviders()).thenReturn(List.of(provider));

        agentService.testAiProviders();

        verify(agentRepository, never()).getFirstActiveTextModelByProvider(any());
        verify(healthCheckService, never()).testProvider(any());
    }

    @Test
    void providerModelDiscoveryShouldUseSavedProviderConfig() {
        AiProviderRow provider = provider(1003L, "siliconflow", "硅基流动", true);
        List<ProviderModelOptionResponse> discovered = List.of(new ProviderModelOptionResponse(
            "Qwen/Qwen2.5-7B-Instruct",
            "Qwen/Qwen2.5-7B-Instruct",
            "TEXT"
        ));
        when(agentRepository.getAiProvider(1003L)).thenReturn(provider);
        when(healthCheckService.listProviderModels(provider)).thenReturn(discovered);

        List<ProviderModelOptionResponse> response = agentService.listProviderModels(1003L);

        assertEquals(discovered, response);
        verify(healthCheckService).listProviderModels(provider);
    }

    @Test
    void savedModelHealthCheckShouldPersistSpecificModelResult() {
        LlmModelRow model = model(2001L, "siliconflow", "EMBEDDING", "BAAI/bge-m3", "https://api.siliconflow.cn/v1", "model-key", "ACTIVE");
        when(agentRepository.getLlmModel(2001L)).thenReturn(model);
        when(healthCheckService.testModel("siliconflow", "EMBEDDING", "BAAI/bge-m3", "https://api.siliconflow.cn/v1", "model-key", false))
            .thenReturn(new ModelTestResponse(false, "模型不可用", null, 31));

        ModelTestResponse response = agentService.testModelConnection(2001L);

        assertFalse(response.success());
        assertEquals("模型不可用", response.message());
        verify(agentRepository).updateLlmModelTestResult(eq(2001L), eq(false), eq("模型不可用"), eq(0L));
        verify(agentRepository, never()).updateAiProviderTestResult(any(), any(), any(), any());
    }

    @Test
    void scheduledModelHealthCheckShouldOnlyTestActiveModels() {
        LlmModelRow activeModel = model(2001L, "openai", "TEXT", "gpt-4o-mini", "https://api.openai.com/v1", "model-key", "ACTIVE");
        LlmModelRow inactiveModel = model(2002L, "openai", "TEXT", "disabled-model", "https://api.openai.com/v1", "model-key", "INACTIVE");
        when(agentRepository.listLlmModels()).thenReturn(List.of(activeModel, inactiveModel));
        when(healthCheckService.testModel("openai", "TEXT", "gpt-4o-mini", "https://api.openai.com/v1", "model-key", false))
            .thenReturn(new ModelTestResponse(true, "模型可用", "hi", 42));

        agentService.testLlmModels();

        verify(healthCheckService).testModel("openai", "TEXT", "gpt-4o-mini", "https://api.openai.com/v1", "model-key", false);
        verify(healthCheckService, never()).testModel("openai", "TEXT", "disabled-model", "https://api.openai.com/v1", "model-key", false);
        verify(agentRepository).updateLlmModelTestResult(eq(2001L), eq(true), eq("模型可用"), eq(0L));
        verify(agentRepository, never()).updateLlmModelTestResult(eq(2002L), any(), any(), any());
    }

    @Test
    void draftModelHealthCheckShouldFallbackToProviderConnectionConfig() {
        AiProviderRow provider = provider(1003L, "siliconflow", "硅基流动", true);
        when(agentRepository.getAiProviderByType("siliconflow")).thenReturn(provider);
        when(healthCheckService.testModel("siliconflow", "TEXT", "Qwen/Qwen2.5-7B-Instruct", "https://example.test/v1", "test-key", null))
            .thenReturn(new ModelTestResponse(true, "模型可用", "hi", 19));

        ModelTestResponse response = agentService.testModelConnection(new TestLlmModelRequest(
            "siliconflow",
            "TEXT",
            "Qwen/Qwen2.5-7B-Instruct",
            null,
            null
        ));

        assertTrue(response.success());
        verify(healthCheckService).testModel("siliconflow", "TEXT", "Qwen/Qwen2.5-7B-Instruct", "https://example.test/v1", "test-key", null);
    }

    @Test
    void savedModelHealthCheckShouldPassVisionFlag() {
        LlmModelRow model = model(2004L, "siliconflow", "TEXT", "Qwen/Qwen3-VL-8B-Instruct", "https://api.siliconflow.cn/v1", "model-key", "ACTIVE", true);
        when(agentRepository.getLlmModel(2004L)).thenReturn(model);
        when(healthCheckService.testModel("siliconflow", "TEXT", "Qwen/Qwen3-VL-8B-Instruct", "https://api.siliconflow.cn/v1", "model-key", true))
            .thenReturn(new ModelTestResponse(true, "模型可用", null, 31));

        ModelTestResponse response = agentService.testModelConnection(2004L);

        assertTrue(response.success());
        verify(healthCheckService).testModel("siliconflow", "TEXT", "Qwen/Qwen3-VL-8B-Instruct", "https://api.siliconflow.cn/v1", "model-key", true);
    }

    @Test
    void pageAgentsShouldFlagPublishedKnowledgeSpaceIssues() {
        String workflowConfigJson = """
            {"nodes":[{"id":"knowledge","type":"KNOWLEDGE","config":{"toolCalls":[{"toolCode":"KNOWLEDGE_SEARCH_CHUNKS","args":{"spaceId":"9001","query":"{{start.query}}","topK":5}}]}}]}
            """;
        AgentRow row = new AgentRow(
            1001L, "POLICY_HELPER", "制度助手", null, "QA", "SYSTEM_IDENTITY",
            2001L, 1, "v1", workflowConfigJson, "ACTIVE", LocalDateTime.now(), LocalDateTime.now()
        );
        when(agentRepository.countAgents(null)).thenReturn(1L);
        when(agentRepository.pageAgents(null, 0L, 20L)).thenReturn(List.of(row));
        when(agentRepository.listKnowledgeSpacesByIds(eq(List.of(9001L)))).thenReturn(List.<KnowledgeSpaceHealthRow>of());

        var response = agentService.pageAdminAgents(1, 20);

        assertEquals(1, response.getList().get(0).getKnowledgeIssueCount());
        assertTrue(response.getList().get(0).getKnowledgeIssueMessage().contains("知识库不可用"));
    }

    @Test
    void updateModelShouldKeepRawModelOverrideInsteadOfCopyingProviderSecret() {
        LlmModelRow effectiveModel = model(2003L, "siliconflow", "TEXT", "Qwen/Qwen2.5-7B-Instruct", "https://example.test/v1", "provider-key", "ACTIVE");
        LlmModelRow rawModel = model(2003L, "siliconflow", "TEXT", "Qwen/Qwen2.5-7B-Instruct", "https://custom.test/v1", "model-key", "ACTIVE");
        when(agentRepository.getLlmModel(2003L)).thenReturn(effectiveModel);
        when(agentRepository.getRawLlmModel(2003L)).thenReturn(rawModel);

        agentService.updateLlmModel(2003L, new UpdateLlmModelRequest(
            "siliconflow",
            "TEXT",
            "Qwen/Qwen2.5-7B-Instruct",
            "Qwen 2.5",
            null,
            null,
            false,
            true,
            false,
            false,
            false,
            false,
            "ACTIVE"
        ));

        verify(agentRepository).updateLlmModel(
            eq(2003L),
            eq("siliconflow"),
            eq("TEXT"),
            eq("Qwen/Qwen2.5-7B-Instruct"),
            eq("Qwen 2.5"),
            eq("https://custom.test/v1"),
            eq("model-key"),
            eq(false),
            eq(true),
            eq(false),
            eq(false),
            eq(false),
            eq(false),
            eq("ACTIVE"),
            eq(0L)
        );
    }

    private AiProviderRow provider(Long id, String providerType, String displayName, boolean enabled) {
        return new AiProviderRow(
            id,
            providerType,
            displayName,
            "https://example.test/v1",
            "test-key",
            enabled,
            null,
            null,
            null,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }

    private LlmModelRow model(Long id, String provider, String modelType, String modelName, String endpoint, String apiKey, String status) {
        return model(id, provider, modelType, modelName, endpoint, apiKey, status, false);
    }

    private LlmModelRow model(Long id, String provider, String modelType, String modelName, String endpoint, String apiKey, String status, Boolean visionFlag) {
        return new LlmModelRow(
            id,
            provider,
            modelType,
            modelName,
            modelName,
            endpoint,
            apiKey,
            false,
            false,
            visionFlag,
            false,
            false,
            false,
            status,
            null,
            null,
            null,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }
}
