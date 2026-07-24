package com.workforcehub.plugin.runtime;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * 插件 Flyway 迁移器，每个插件使用独立的 history 表。
 */
public final class PluginFlywayMigrator {

    private static final Pattern SAFE_ID = Pattern.compile("^[a-zA-Z0-9_]{1,50}$");
    private final DataSource dataSource;

    public PluginFlywayMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 执行插件数据库迁移。
     *
     * @param pluginId 插件ID
     * @param pluginJarPath 插件JAR路径
     * @throws PluginMigrationException 迁移失败时抛出
     */
    public void migrate(String pluginId, Path pluginJarPath) {
        String safeId = sanitizePluginId(pluginId);
        String historyTable = "flyway_plugin_" + safeId;
        validateTableName(historyTable);

        Path migrationDir = pluginJarPath.resolve("db/migration");
        String location = "filesystem:" + migrationDir.toAbsolutePath();

        FluentConfiguration config = Flyway.configure()
            .dataSource(dataSource)
            .locations(location)
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
     * 清理插件的迁移历史（卸载时可选调用）。
     * 注意：不删除业务数据表，只清理 history 记录。
     */
    public void cleanHistory(String pluginId) {
        String safeId = sanitizePluginId(pluginId);
        String historyTable = "flyway_plugin_" + safeId;
        validateTableName(historyTable);

        try {
            Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .table(historyTable)
                .load();
            flyway.clean();
        } catch (Exception e) {
            throw new PluginMigrationException(
                "插件 [%s] 迁移历史清理失败: %s".formatted(pluginId, e.getMessage()), e);
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
        // 只保留字母、数字、下划线，最大50字符
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

    public static class PluginMigrationException extends RuntimeException {
        public PluginMigrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
