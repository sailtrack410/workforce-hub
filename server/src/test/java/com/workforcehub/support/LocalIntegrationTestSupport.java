package com.workforcehub.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * 本地集成测试支撑基类
 * - 统一注入本地 MySQL / Redis 连接参数
 * - 在测试开始前装载 module-org 冒烟种子数据
 */
public abstract class LocalIntegrationTestSupport {

    private static final String MYSQL_URL_TEMPLATE =
        "jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=UTF-8"
            + "&connectionCollation=utf8mb4_0900_ai_ci&serverTimezone=Asia/Shanghai"
            + "&useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true";
    private static final String FLOWABLE_TEST_EXCLUDES = String.join(",",
        "org.flowable.spring.boot.actuate.info.FlowableInfoAutoConfiguration",
        "org.flowable.spring.boot.EndpointAutoConfiguration",
        "org.flowable.spring.boot.RestApiAutoConfiguration",
        "org.flowable.spring.boot.app.AppEngineServicesAutoConfiguration",
        "org.flowable.spring.boot.app.AppEngineAutoConfiguration",
        "org.flowable.spring.boot.ProcessEngineServicesAutoConfiguration",
        "org.flowable.spring.boot.ProcessEngineAutoConfiguration",
        "org.flowable.spring.boot.FlowableJpaAutoConfiguration",
        "org.flowable.spring.boot.dmn.DmnEngineAutoConfiguration",
        "org.flowable.spring.boot.dmn.DmnEngineServicesAutoConfiguration",
        "org.flowable.spring.boot.idm.IdmEngineAutoConfiguration",
        "org.flowable.spring.boot.idm.IdmEngineServicesAutoConfiguration",
        "org.flowable.spring.boot.eventregistry.EventRegistryAutoConfiguration",
        "org.flowable.spring.boot.eventregistry.EventRegistryServicesAutoConfiguration",
        "org.flowable.spring.boot.cmmn.CmmnEngineAutoConfiguration",
        "org.flowable.spring.boot.cmmn.CmmnEngineServicesAutoConfiguration",
        "org.flowable.spring.boot.ldap.FlowableLdapAutoConfiguration",
        "org.flowable.spring.boot.FlowableSecurityAutoConfiguration"
    );

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> String.format(
            MYSQL_URL_TEMPLATE,
            envOrFallback("TEST_DB_HOST", "DB_HOST", "localhost"),
            envOrFallback("TEST_DB_PORT", "DB_PORT", "3306"),
            env("TEST_DB_NAME", "workforce_hub_test")
        ));
        registry.add("spring.datasource.username", () -> envOrFallback("TEST_DB_USER", "DB_USER", "root"));
        registry.add("spring.datasource.password", () -> envOrFallback("TEST_DB_PASSWORD", "DB_PASSWORD", "REDACTED_DB_PASSWORD"));
        registry.add("spring.data.redis.host", () -> env("REDIS_HOST", "localhost"));
        registry.add("spring.data.redis.port", () -> env("REDIS_PORT", "6379"));
        registry.add("spring.data.redis.password", () -> env("REDIS_PASSWORD", ""));
        registry.add("spring.session.redis.namespace", () -> "workforce:test:session");
        registry.add("spring.autoconfigure.exclude", LocalIntegrationTestSupport::resolveAutoConfigurationExcludes);
        registry.add("spring.mail.host", () -> env("MAIL_HOST", "localhost"));
        registry.add("spring.mail.port", () -> env("MAIL_PORT", "1025"));
        registry.add("management.health.mail.enabled", () -> "false");
        registry.add("logging.level.com.workforcehub", () -> "info");
    }

    @BeforeEach
    void seedDatabase() {
        resetRedisState();
        resetBusinessTables();
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding("UTF-8");
        populator.addScript(resolveSeedScript());
        DatabasePopulatorUtils.execute(populator, dataSource);
    }

    private void resetRedisState() {
        deleteKeys("workforce:test:session*");
        deleteKeys("auth:login:fail:*");
        deleteKeys("auth:email:*");
    }

    private void resetBusinessTables() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            jdbcTemplate.execute("DELETE FROM sys_download_ticket");
            jdbcTemplate.execute("DELETE FROM sys_storage_usage");
            jdbcTemplate.execute("DELETE FROM sys_storage_quota_policy");
            jdbcTemplate.execute("DELETE FROM sys_upload_ticket");
            jdbcTemplate.execute("DELETE FROM sys_attachment_ref");
            jdbcTemplate.execute("DELETE FROM sys_file_object");
            jdbcTemplate.execute("DELETE FROM hr_employee_event");
            jdbcTemplate.execute("DELETE FROM auth_user_account");
            jdbcTemplate.execute("DELETE FROM org_department_leader");
            jdbcTemplate.execute("DELETE FROM org_user");
            jdbcTemplate.execute("DELETE FROM org_position");
            jdbcTemplate.execute("DELETE FROM org_department");
            jdbcTemplate.execute("DELETE FROM admin_user");
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    private static Resource resolveSeedScript() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path modulePath = cwd.resolve("scripts/sql/seed_module_org_smoke_data.sql");
        if (Files.exists(modulePath)) {
            return new FileSystemResource(modulePath);
        }

        Path repoPath = cwd.resolve("server/scripts/sql/seed_module_org_smoke_data.sql");
        if (Files.exists(repoPath)) {
            return new FileSystemResource(repoPath);
        }

        throw new IllegalStateException("未找到 module-org 冒烟种子脚本");
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String envOrFallback(String primaryKey, String fallbackKey, String defaultValue) {
        String primary = System.getenv(primaryKey);
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return env(fallbackKey, defaultValue);
    }

    private static String resolveAutoConfigurationExcludes() {
        String existing = env("SPRING_AUTOCONFIGURE_EXCLUDE", "");
        if (existing.isBlank()) {
            return FLOWABLE_TEST_EXCLUDES;
        }
        return existing + "," + FLOWABLE_TEST_EXCLUDES;
    }

    private void deleteKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
