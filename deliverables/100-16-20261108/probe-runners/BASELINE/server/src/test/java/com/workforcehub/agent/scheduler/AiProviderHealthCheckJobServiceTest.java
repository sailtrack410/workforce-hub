package com.workforcehub.agent.scheduler;

import com.workforcehub.agent.service.AgentService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class AiProviderHealthCheckJobServiceTest {

    @Test
    void scheduledHealthCheckShouldRunEveryThirtyMinutesByDefault() throws NoSuchMethodException {
        Scheduled scheduled = AiProviderHealthCheckJobService.class
            .getMethod("scheduledProviderHealthCheck")
            .getAnnotation(Scheduled.class);

        assertEquals("${workforce.agent.provider-health-check.fixed-delay-ms:1800000}", scheduled.fixedDelayString());
        assertEquals("${workforce.agent.provider-health-check.initial-delay-ms:1800000}", scheduled.initialDelayString());
    }

    @Test
    void scheduledHealthCheckShouldRunProviderAndModelChecks() {
        AgentService agentService = mock(AgentService.class);
        AiProviderHealthCheckJobService jobService = new AiProviderHealthCheckJobService(agentService, true);

        jobService.scheduledProviderHealthCheck();

        verify(agentService).testAiProviders();
        verify(agentService).testLlmModels();
    }

    @Test
    void disabledScheduledHealthCheckShouldSkipBothChecks() {
        AgentService agentService = mock(AgentService.class);
        AiProviderHealthCheckJobService jobService = new AiProviderHealthCheckJobService(agentService, false);

        jobService.scheduledProviderHealthCheck();

        verify(agentService, never()).testAiProviders();
        verify(agentService, never()).testLlmModels();
    }
}
