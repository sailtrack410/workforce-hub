package com.workforcehub.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    @DisplayName("后端不应继续依赖 MyBatis-Plus")
    void backendShouldNotUseMyBatisPlus() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        assertFalse(pom.contains("mybatis-plus"), "应使用 MyBatis starter，不能继续引入 MyBatis-Plus");
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            List<Path> offenders = files
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> read(path).contains("com.baomidou"))
                .toList();
            assertTrue(offenders.isEmpty(), "源码不应继续引用 com.baomidou: " + offenders);
        }
    }

    @Test
    @DisplayName("IM Outbox 后台治理不应继续拆分多个 service")
    void imOutboxAdminShouldStayInSingleService() throws Exception {
        try (Stream<Path> files = Files.list(Path.of("src/main/java/com/workforcehub/im/service"))) {
            long serviceCount = files
                .filter(path -> path.getFileName().toString().matches("ImOutboxAdmin.*Service\\.java"))
                .count();
            assertEquals(1L, serviceCount, "IM Outbox 后台治理应收口到一个 service");
        }
    }

    @Test
    @DisplayName("文件上传归属和配额校验不应重复定义")
    void fileUploadValidationShouldNotBeDuplicated() throws Exception {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/com/workforcehub/file/service"))) {
            String source = files
                .filter(path -> path.toString().endsWith(".java"))
                .map(ApplicationConfigContractTest::read)
                .collect(Collectors.joining("\n"));
            assertEquals(1L, count(source, "void validateEmployeeOwner("), "员工上传归属校验只能定义一次");
            assertEquals(1L, count(source, "void validateAdminOwner("), "后台上传归属校验只能定义一次");
            assertEquals(1L, count(source, "void ensureEmployeeQuota("), "员工上传配额校验只能定义一次");
            assertEquals(1L, count(source, "void increaseEmployeeUsage("), "员工上传用量记账只能定义一次");
        }
    }

    @Test
    @DisplayName("后端重复小工具函数应集中")
    void backendUtilityHelpersShouldBeShared() throws Exception {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            String source = files
                .filter(path -> path.toString().endsWith(".java"))
                .map(ApplicationConfigContractTest::read)
                .collect(Collectors.joining("\n"));
            assertEquals(0L, count(source, "private String safeSummary("), "safeSummary 应集中到公共工具");
            assertEquals(0L, count(source, "private String normalizeJson("), "normalizeJson 应集中到公共工具");
            assertEquals(0L, count(source, "private String writeJson("), "writeJson 应集中到公共工具");
            assertEquals(0L, count(source, "private String blankToNull("), "blankToNull 应集中到公共工具");
            assertEquals(0L, count(source, "private String trimToNull("), "trimToNull 应集中到公共工具");
            assertEquals(0L, count(source, "private void audit(String operatorType"), "文件审计写入应集中");
        }
    }

    private static long count(String source, String needle) {
        return (source.length() - source.replace(needle, "").length()) / needle.length();
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
