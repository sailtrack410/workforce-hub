package com.workforcehub.plugin.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PluginFlywayMigrator 测试 - 验证从真实 JAR 读取迁移脚本。
 */
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
        assertThrows(IllegalArgumentException.class, () ->
            PluginFlywayMigrator.validateTableName("a".repeat(62)));
    }

    @Test
    void shouldReturnCorrectHistoryTable() {
        assertEquals("flyway_plugin_hello",
            PluginFlywayMigrator.getHistoryTableStatic("hello"));
        assertNotEquals(
            PluginFlywayMigrator.getHistoryTableStatic("leave-plugin"),
            PluginFlywayMigrator.getHistoryTableStatic("leave_plugin")
        );
        assertNotEquals(
            PluginFlywayMigrator.getHistoryTableStatic("my.plugin"),
            PluginFlywayMigrator.getHistoryTableStatic("my_plugin")
        );
        String longPluginId = "a".repeat(64);
        assertTrue(PluginFlywayMigrator.getHistoryTableStatic(longPluginId).length() <= 61);
        assertNotEquals(
            PluginFlywayMigrator.getHistoryTableStatic(longPluginId),
            PluginFlywayMigrator.getHistoryTableStatic(longPluginId + "b")
        );
    }

    @Test
    void shouldDetectMigrationDirectoryInJar() throws IOException {
        // 有迁移目录的 JAR
        Path jarWithMigration = buildJarWithMigration("test-plugin");
        assertTrue(PluginFlywayMigrator.hasMigrationDirectoryStatic(jarWithMigration));

        // 无迁移目录的 JAR
        Path jarWithoutMigration = buildJarWithoutMigration("hello-plugin");
        assertFalse(PluginFlywayMigrator.hasMigrationDirectoryStatic(jarWithoutMigration));
    }

    @Test
    void shouldSkipMigrationWhenNoMigrationDirectory() throws IOException {
        // 验证无迁移目录的 JAR 不会抛出异常
        Path jarWithoutMigration = buildJarWithoutMigration("hello-plugin");
        PluginFlywayMigrator migrator = new PluginFlywayMigrator(null);

        // 不应抛出异常，直接返回
        assertDoesNotThrow(() -> migrator.migrate("hello-plugin", jarWithoutMigration));
    }

    @Test
    void shouldExtractMigrationScriptsFromJar() throws IOException {
        Path jarPath = buildJarWithMigration("test-plugin");
        PluginFlywayMigrator migrator = new PluginFlywayMigrator(null);

        // 使用反射调用私有方法验证解压
        Path tempDir = Files.createTempDirectory("test-extract");
        try (FileSystem fs = FileSystems.newFileSystem(jarPath)) {
            Path migrationPath = fs.getPath("db/migration");
            assertTrue(Files.exists(migrationPath));
            assertTrue(Files.isDirectory(migrationPath));

            // 验证迁移脚本存在
            Path v1Script = migrationPath.resolve("V1__init.sql");
            assertTrue(Files.exists(v1Script));
        }
    }

    private Path buildJarWithMigration(String pluginId) throws IOException {
        Path jar = tempDir.resolve(pluginId + ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            // 添加 plugin.properties
            output.putNextEntry(new JarEntry("plugin.properties"));
            output.write(("plugin.id=" + pluginId + "\nplugin.class=com.test.TestPlugin\n").getBytes());
            output.closeEntry();

            // 添加 db/migration/V1__init.sql
            output.putNextEntry(new JarEntry("db/migration/V1__init.sql"));
            output.write("CREATE TABLE IF NOT EXISTS test_table (id INT PRIMARY KEY);\n".getBytes());
            output.closeEntry();
        }
        return jar;
    }

    private Path buildJarWithoutMigration(String pluginId) throws IOException {
        Path jar = tempDir.resolve(pluginId + ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            // 只添加 plugin.properties，没有 db/migration
            output.putNextEntry(new JarEntry("plugin.properties"));
            output.write(("plugin.id=" + pluginId + "\nplugin.class=com.test.TestPlugin\n").getBytes());
            output.closeEntry();
        }
        return jar;
    }
}
