package com.workforcehub.support;

import org.flowable.spring.boot.ProcessEngineConfigurationConfigurer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * module-workflow Flowable 集成测试专用配置。
 */
@TestConfiguration
public class WorkflowFlowableTestConfiguration {

    @Bean
    ProcessEngineConfigurationConfigurer workflowProcessEngineConfigurer() {
        return configuration -> {
            configuration.setDatabaseCatalog(testDatabaseName());
            configuration.setDisableIdmEngine(true);
            configuration.setDisableEventRegistry(true);
            configuration.setEnableConfiguratorServiceLoader(false);
        };
    }

    private static String testDatabaseName() {
        String value = System.getenv("TEST_DB_NAME");
        return value == null || value.isBlank() ? "workforce_hub_test" : value;
    }
}
