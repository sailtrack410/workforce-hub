package com.workforcehub.agent;

import com.workforcehub.agent.repository.AgentTaskTimeoutRepository;
import com.workforcehub.support.LocalIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * module-agent 人工确认超时扫描仓储边界测试。
 */
@SpringBootTest
@ActiveProfiles("api")
class ModuleAgentTaskTimeoutRepositoryBoundaryTest extends LocalIntegrationTestSupport {

    @Autowired(required = false)
    private AgentTaskTimeoutRepository timeoutRepository;

    @Test
    @DisplayName("人工确认超时扫描应使用独立仓储承载任务锁和批次记录SQL")
    void timeoutScanShouldHaveDedicatedRepositoryBoundary() {
        assertNotNull(timeoutRepository, "AgentTaskTimeoutRepository 应作为独立 Spring Bean 存在");
    }
}
