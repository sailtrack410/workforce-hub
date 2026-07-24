package com.workforcehub.plugin.runtime;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.regex.Pattern;

/**
 * 插件 Flyway 迁移器，每个插件使用独立的 history 表。
 * 从插件 JAR 内部读取迁移脚本，不把 JAR 当普通目录。
 */
@Component
public final class PluginFlywayMigrator {

    private static final Pattern SAFE_ID = Pattern.compile("^[a-zA-Z0-9_]{1,50}$");
    private static final String MIGRATION_PATH = "db/migration";

    private final DataSource dataSource;

    public PluginFlywayMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 执行插件数据库迁移。
     * 从 JAR 内部的 db/migration/ 目录读取迁移脚本。
     * 如果 JAR 内没有 db/migration 目录，跳过迁移（不是错误）。
     *
     * @param pluginId 插件ID
     * @param pluginJarPath 插件JAR文件路径
     */
    public void migrate(String pluginId, Path pluginJarPath) {
        String safeId = sanitizePluginId(pluginId);
        String historyTable = "flyway_plugin_" + safeId;
        validateTableName(historyTable);

        // 验证 JAR 文件存在
        if (!Files.exists(pluginJarPath) || !Files.isRegularFile(pluginJarPath)) {
            throw new PluginMigrationException("插件 JAR 不存在: " + pluginJarPath, null);
        }

        // JAR 内没有 db/migration 目录，跳过迁移
        if (!hasMigrationDirectory(pluginJarPath)) {
            return;
        }

        // 使用 JAR URL 作为 Flyway 位置
        String location = "filesystem:" + pluginJarPath.toAbsolutePath().getParent()
            + "/";  // Flyway 会扫描 JAR 同级目录，需要解压或使用其他方式

        // 实际方案：解压 JAR 中的迁移脚本到临时目录
        Path tempMigrationDir = extractMigrationScripts(pluginId, pluginJarPath);

        FluentConfiguration config = Flyway.configure()
            .dataSource(dataSource)
            .locations("filesystem:" + tempMigrationDir.toAbsolutePath())
            .table(historyTable)
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .validateOnMigrate(true)
            .outOfOrder(false);

        try {
            Flyway flyway = config.load();
            flyway.migrate();
        } catch (Exception e) {
            throw new PluginMigrationException(
                "插件 [%s] 数据库迁移失败: %s".formatted(pluginId, e.getMessage()), e);
        }
    }

    /**
     * 检查 JAR 内是否存在 db/migration 目录。
     */
    private boolean hasMigrationDirectory(Path jarPath) {
        try (FileSystem fs = FileSystems.newFileSystem(jarPath, Collections.emptyMap())) {
            Path migrationPath = fs.getPath(MIGRATION_PATH);
            return Files.exists(migrationPath) && Files.isDirectory(migrationPath);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 从 JAR 中解压迁移脚本到临时目录。
     */
    private Path extractMigrationScripts(String pluginId, Path jarPath) {
        try {
            Path tempDir = Files.createTempDirectory("wf_plugin_migration_" + pluginId);
            tempDir.toFile().deleteOnExit();

            try (FileSystem fs = FileSystems.newFileSystem(jarPath, Collections.emptyMap())) {
                Path migrationPath = fs.getPath(MIGRATION_PATH);
                if (Files.exists(migrationPath)) {
                    Files.walk(migrationPath)
                        .filter(Files::isRegularFile)
                        .forEach(source -> {
                            try {
                                Path target = tempDir.resolve(migrationPath.relativize(source).toString());
                                Files.createDirectories(target.getParent());
                                Files.copy(source, target);
                            } catch (IOException e) {
                                throw new RuntimeException("解压迁移脚本失败: " + source, e);
                            }
                        });
                }
            }

            return tempDir;
        } catch (IOException e) {
            throw new PluginMigrationException("解压迁移脚本失败: " + pluginId, e);
        }
    }

    /**
     * 获取插件的 history 表名。
     */
    public String getHistoryTable(String pluginId) {
        String safeId = sanitizePluginId(pluginId);
        String historyTable = "flyway_plugin_" + safeId;
        validateTableName(historyTable);
        return historyTable;
    }

    /**
     * 清理插件ID中的非法字符，只保留字母、数字、下划线。
     */
    static String sanitizePluginId(String pluginId) {
        if (pluginId == null || pluginId.isBlank()) {
            throw new IllegalArgumentException("pluginId 不能为空");
        }
        String sanitized = pluginId.replaceAll("[^a-zA-Z0-9_]", "_");
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }
        return sanitized;
    }

    /**
     * 校验表名安全性，防止 SQL 注入。
     */
    static void validateTableName(String tableName) {
        if (!SAFE_ID.matcher(tableName).matches()) {
            throw new IllegalArgumentException("非法表名: " + tableName);
        }
    }

    /**
     * 静态方法：获取 history 表名（测试用）。
     */
    static String getHistoryTableStatic(String pluginId) {
        String safeId = sanitizePluginId(pluginId);
        String historyTable = "flyway_plugin_" + safeId;
        validateTableName(historyTable);
        return historyTable;
    }

    /**
     * 静态方法：检查 JAR 内是否存在 db/migration 目录（测试用）。
     */
    static boolean hasMigrationDirectoryStatic(Path jarPath) {
        try (FileSystem fs = FileSystems.newFileSystem(jarPath, Collections.emptyMap())) {
            Path migrationPath = fs.getPath(MIGRATION_PATH);
            return Files.exists(migrationPath) && Files.isDirectory(migrationPath);
        } catch (IOException e) {
            return false;
        }
    }

    public static class PluginMigrationException extends RuntimeException {
        public PluginMigrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
