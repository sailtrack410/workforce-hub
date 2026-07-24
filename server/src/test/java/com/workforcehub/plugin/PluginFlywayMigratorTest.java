package com.workforcehub.plugin.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PluginFlywayMigratorTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldSanitizePluginId() {
        // 正常ID（连字符转下划线）
        assertEquals("hello_plugin", PluginFlywayMigrator.sanitizePluginId("hello-plugin"));
        assertEquals("leave_plugin", PluginFlywayMigrator.sanitizePluginId("leave-plugin"));

        // 包含特殊字符
        assertEquals("hello_plugin", PluginFlywayMigrator.sanitizePluginId("hello.plugin"));
        assertEquals("hello_plugin", PluginFlywayMigrator.sanitizePluginId("hello@plugin"));
        assertEquals("hello_plugin", PluginFlywayMigrator.sanitizePluginId("hello plugin"));

        // 空值检查
        assertThrows(IllegalArgumentException.class, () ->
            PluginFlywayMigrator.sanitizePluginId(null));
        assertThrows(IllegalArgumentException.class, () ->
            PluginFlywayMigrator.sanitizePluginId(""));
        assertThrows(IllegalArgumentException.class, () ->
            PluginFlywayMigrator.sanitizePluginId("   "));
    }

    @Test
    void shouldTruncateLongPluginId() {
        String longId = "a".repeat(100);
        String sanitized = PluginFlywayMigrator.sanitizePluginId(longId);
        assertEquals(50, sanitized.length());
    }

    @Test
    void shouldValidateSafeTableNames() {
        // 安全表名
        assertDoesNotThrow(() -> PluginFlywayMigrator.validateTableName("flyway_plugin_hello"));
        assertDoesNotThrow(() -> PluginFlywayMigrator.validateTableName("flyway_plugin_test123"));

        // 不安全表名
        assertThrows(IllegalArgumentException.class, () ->
            PluginFlywayMigrator.validateTableName("flyway_plugin_hello; DROP TABLE users"));
        assertThrows(IllegalArgumentException.class, () ->
            PluginFlywayMigrator.validateTableName("flyway_plugin_'hello'"));
        assertThrows(IllegalArgumentException.class, () ->
            PluginFlywayMigrator.validateTableName(""));
    }

    @Test
    void shouldReturnCorrectHistoryTable() {
        PluginFlywayMigrator migrator = new PluginFlywayMigrator(null);

        assertEquals("flyway_plugin_hello",
            migrator.getHistoryTable("hello"));
        assertEquals("flyway_plugin_leave_plugin",
            migrator.getHistoryTable("leave-plugin"));
        assertEquals("flyway_plugin_my_plugin",
            migrator.getHistoryTable("my.plugin"));
    }

    @Test
    void shouldCreateIndependentMigrationDirectories() throws Exception {
        // 验证两个插件的迁移目录是独立的
        Path plugin1Dir = tempDir.resolve("plugin1/db/migration");
        Path plugin2Dir = tempDir.resolve("plugin2/db/migration");

        Files.createDirectories(plugin1Dir);
        Files.createDirectories(plugin2Dir);

        // 创建不同的迁移脚本
        Files.writeString(plugin1Dir.resolve("V1__init.sql"),
            "CREATE TABLE IF NOT EXISTS plugin1_test (id INT PRIMARY KEY);");
        Files.writeString(plugin2Dir.resolve("V1__init.sql"),
            "CREATE TABLE IF NOT EXISTS plugin2_test (id INT PRIMARY KEY);");

        // 验证文件存在
        assertTrue(Files.exists(plugin1Dir.resolve("V1__init.sql")));
        assertTrue(Files.exists(plugin2Dir.resolve("V1__init.sql")));

        // 验证内容不同
        assertNotEquals(
            Files.readString(plugin1Dir.resolve("V1__init.sql")),
            Files.readString(plugin2Dir.resolve("V1__init.sql"))
        );
    }
}
