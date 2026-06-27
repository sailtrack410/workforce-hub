package com.workforcehub.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationConfigContractTest {

    @Test
    @DisplayName("后端启动配置应导入仓库根目录 .env")
    void applicationConfigShouldImportRepoRootDotEnv() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties properties = factory.getObject();

        String configImport = properties == null ? "" : properties.stringPropertyNames().stream()
            .filter(name -> name.equals("spring.config.import") || name.startsWith("spring.config.import["))
            .map(properties::getProperty)
            .collect(Collectors.joining(","));
        assertTrue(configImport.contains("optional:file:../.env[.properties]"),
            "server 目录启动时应自动读取仓库根目录 .env");
        assertTrue(configImport.contains("optional:file:.env[.properties]"),
            "仓库根目录启动或临时 server/.env 场景也应被支持");
    }

    @Test
    @DisplayName("后端不应保留跨模块 spi 包")
    void backendShouldNotKeepSpiPackage() {
        assertFalse(Files.exists(Path.of("src/main/java/com/workforcehub/spi")),
            "跨模块 spi 包已确认为过度设计，应直接注入具体服务");
    }
}
