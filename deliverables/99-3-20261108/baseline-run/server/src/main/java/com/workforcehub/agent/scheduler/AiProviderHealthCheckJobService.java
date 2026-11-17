package com.workforcehub.agent.scheduler;

import com.workforcehub.agent.service.AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * AI 基座连通性自检。
 */
// AI 基座自检：每半小时分别检查供应商连通性和具体模型可用性
@Service
public class AiProviderHealthCheckJobService {

    private static final Logger log = LoggerFactory.getLogger(AiProviderHealthCheckJobService.class);

    private final AgentService agentService;
    private final boolean enabled;

    public AiProviderHealthCheckJobService(AgentService agentService,
                                           @Value("${workforce.agent.provider-health-check.enabled:true}") boolean enabled) {
        this.agentService = agentService;
        this.enabled = enabled;
    }

    @Scheduled(
        fixedDelayString = "${workforce.agent.provider-health-check.fixed-delay-ms:1800000}",
        initialDelayString = "${workforce.agent.provider-health-check.initial-delay-ms:1800000}"
    )
    public void scheduledProviderHealthCheck() {
        if (!enabled) {
            return;
        }
        try {
            agentService.testAiProviders();
        } catch (RuntimeException e) {
            log.warn("AI供应商自检失败", e);
        }
        try {
            agentService.testLlmModels();
        } catch (RuntimeException e) {
            log.warn("AI模型自检失败", e);
        }
    }
}
