package com.workforcehub.support;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 本地集成测试支撑基类
 * - 统一注入本地 MySQL / Redis 连接参数
 * - 在测试开始前装载 module-org 冒烟种子数据
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class LocalIntegrationTestSupport {

    private static final String MYSQL_URL_TEMPLATE =
        "jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=UTF-8"
            + "&connectionCollation=utf8mb4_0900_ai_ci&serverTimezone=Asia/Shanghai"
            + "&useSSL=false&allowPublicKeyRetrieval=true";

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> String.format(
            MYSQL_URL_TEMPLATE,
            env("DB_HOST", "localhost"),
            env("DB_PORT", "3306"),
            env("DB_NAME", "workforce_hub")
        ));
        registry.add("spring.datasource.username", () -> env("DB_USER", "root"));
        registry.add("spring.datasource.password", () -> env("DB_PASSWORD", "REDACTED_DB_PASSWORD"));
        registry.add("spring.data.redis.host", () -> env("REDIS_HOST", "localhost"));
        registry.add("spring.data.redis.port", () -> env("REDIS_PORT", "6379"));
        registry.add("spring.data.redis.password", () -> env("REDIS_PASSWORD", ""));
        registry.add("spring.session.redis.namespace", () -> "workforce:test:session");
        registry.add("spring.mail.host", () -> env("MAIL_HOST", "localhost"));
        registry.add("spring.mail.port", () -> env("MAIL_PORT", "1025"));
        registry.add("management.health.mail.enabled", () -> "false");
        registry.add("flowable.database-schema-update", () -> env("FLOWABLE_DB_SCHEMA_UPDATE", "true"));
        registry.add("logging.level.com.workforcehub", () -> "info");
    }

    @BeforeAll
    void seedDatabase() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding("UTF-8");
        populator.addScript(resolveSeedScript());
        DatabasePopulatorUtils.execute(populator, dataSource);
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
}
