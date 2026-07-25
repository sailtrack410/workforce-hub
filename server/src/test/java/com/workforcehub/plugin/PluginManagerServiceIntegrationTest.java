package com.workforcehub.plugin;

import com.workforcehub.plugin.repository.PluginRow;
import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.runtime.PluginFlywayMigrator;
import com.workforcehub.plugin.runtime.PluginManagerService;
import com.workforcehub.plugin.runtime.WorkforcePluginManager;
import com.workforcehub.support.LocalIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "workforce.plugin.root=target/test-plugins")
@ActiveProfiles("api")
class PluginManagerServiceIntegrationTest extends LocalIntegrationTestSupport {

    private static final String SUCCESS_PLUGIN_ID = "migration-plugin-success";
    private static final String FAILURE_PLUGIN_ID = "migration-plugin-failure";
    private static final String PERSISTENCE_FAILURE_PLUGIN_ID = "migration-plugin-persistence-failure";

    @TempDir
    Path tempDir;

    @Autowired
    private PluginManagerService pluginManagerService;

    @Autowired
    private WorkforcePluginManager pluginManager;

    @Autowired
    private PluginFlywayMigrator flywayMigrator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetPluginState() {
        unloadIfPresent(SUCCESS_PLUGIN_ID);
        unloadIfPresent(FAILURE_PLUGIN_ID);
        unloadIfPresent(PERSISTENCE_FAILURE_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin WHERE plugin_id IN (?, ?, ?)",
            SUCCESS_PLUGIN_ID,
            FAILURE_PLUGIN_ID,
            PERSISTENCE_FAILURE_PLUGIN_ID
        );
        dropTable("plugin_migration_success");
        dropTable(flywayMigrator.getHistoryTable(SUCCESS_PLUGIN_ID));
        dropTable(flywayMigrator.getHistoryTable(FAILURE_PLUGIN_ID));
        dropTable(flywayMigrator.getHistoryTable(PERSISTENCE_FAILURE_PLUGIN_ID));
    }

    @AfterEach
    void cleanupPluginState() {
        resetPluginState();
    }

    @Test
    void shouldInstallMigrateAndPersistLifecycle() throws Exception {
        Path jarPath = buildPluginJar(
            SUCCESS_PLUGIN_ID,
            "CREATE TABLE plugin_migration_success (id INT PRIMARY KEY);"
        );

        PluginRow installed = pluginManagerService.install(jarPath, metadata("Migration Success"));

        assertEquals(SUCCESS_PLUGIN_ID, installed.getPluginId());
        assertEquals(PluginStatus.INSTALLED, pluginManagerService.getPlugin(SUCCESS_PLUGIN_ID).getStatus());
        assertEquals(1, tableExists("plugin_migration_success"));
        assertEquals(1, tableExists(flywayMigrator.getHistoryTable(SUCCESS_PLUGIN_ID)));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + flywayMigrator.getHistoryTable(SUCCESS_PLUGIN_ID)
                + " WHERE version = '1' AND type = 'SQL' AND success = 1",
            Integer.class
        ));

        pluginManagerService.enable(SUCCESS_PLUGIN_ID);
        assertEquals(PluginStatus.ACTIVE, pluginManagerService.getPlugin(SUCCESS_PLUGIN_ID).getStatus());

        pluginManagerService.disable(SUCCESS_PLUGIN_ID);
        assertEquals(PluginStatus.STOPPED, pluginManagerService.getPlugin(SUCCESS_PLUGIN_ID).getStatus());

        pluginManagerService.uninstall(SUCCESS_PLUGIN_ID);
        assertEquals(PluginStatus.UNINSTALLED, pluginManagerService.getPlugin(SUCCESS_PLUGIN_ID).getStatus());
        assertNull(pluginManager.getPlugin(SUCCESS_PLUGIN_ID));
        assertFalse(hasMigrationTempDirectory(SUCCESS_PLUGIN_ID));
    }

    @Test
    void shouldUnloadPluginWhenMigrationFails() throws Exception {
        Path jarPath = buildPluginJar(FAILURE_PLUGIN_ID, "THIS IS NOT VALID SQL;");

        assertThrows(
            PluginFlywayMigrator.PluginMigrationException.class,
            () -> pluginManagerService.install(jarPath, metadata("Migration Failure"))
        );

        assertNull(pluginManager.getPlugin(FAILURE_PLUGIN_ID));
        assertFalse(pluginManagerService.listPlugins().stream()
            .anyMatch(plugin -> FAILURE_PLUGIN_ID.equals(plugin.getPluginId())));
        assertFalse(hasMigrationTempDirectory(FAILURE_PLUGIN_ID));
    }

    @Test
    void shouldUnloadPluginWhenPersistenceFails() throws Exception {
        jdbcTemplate.update("""
            INSERT INTO sys_plugin
                (plugin_id, name, version, sdk_version, java_version, type, status, jar_path)
            VALUES (?, 'Existing Plugin', '0.1.0', '1.0.0', '17', 'FUNCTIONAL', 'INSTALLED', 'existing.jar')
            """, PERSISTENCE_FAILURE_PLUGIN_ID);
        Path jarPath = buildPluginJar(PERSISTENCE_FAILURE_PLUGIN_ID, "SELECT 1;");

        assertThrows(
            DataIntegrityViolationException.class,
            () -> pluginManagerService.install(jarPath, metadata("Persistence Failure"))
        );

        assertNull(pluginManager.getPlugin(PERSISTENCE_FAILURE_PLUGIN_ID));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin WHERE plugin_id = ?",
            Integer.class,
            PERSISTENCE_FAILURE_PLUGIN_ID
        ));
        assertFalse(hasMigrationTempDirectory(PERSISTENCE_FAILURE_PLUGIN_ID));
    }

    private PluginRow metadata(String name) {
        PluginRow row = new PluginRow();
        row.setName(name);
        return row;
    }

    private Path buildPluginJar(String pluginId, String migrationSql) throws IOException {
        Path source = Path.of("..", "plugins", "hello-plugin", "src", "main", "java",
            "com", "workforcehub", "plugin", "hello", "HelloPlugin.java").toAbsolutePath().normalize();
        Path classes = tempDir.resolve(pluginId + "-classes");
        Files.createDirectories(classes);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "需要 JDK");
        int result = compiler.run(
            null, null, null,
            "-classpath", System.getProperty("java.class.path"),
            "-d", classes.toString(),
            source.toString()
        );
        assertEquals(0, result, "编译失败");

        Path properties = classes.resolve("plugin.properties");
        Files.writeString(properties, """
            plugin.id=%s
            plugin.class=com.workforcehub.plugin.hello.HelloPlugin
            plugin.version=0.1.0
            plugin.provider=Workforce Hub
            plugin.description=Plugin migration integration test
            """.formatted(pluginId));

        Path jar = tempDir.resolve(pluginId + ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            try (var files = Files.walk(classes)) {
                for (Path file : files.filter(Files::isRegularFile).sorted(Comparator.naturalOrder()).toList()) {
                    String entryName = classes.relativize(file).toString().replace('\\', '/');
                    output.putNextEntry(new JarEntry(entryName));
                    Files.copy(file, output);
                    output.closeEntry();
                }
            }
            output.putNextEntry(new JarEntry("db/migration/V1__init.sql"));
            output.write(migrationSql.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return jar;
    }

    private int tableExists(String tableName) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
            """, Integer.class, tableName);
    }

    private void dropTable(String tableName) {
        jdbcTemplate.execute("DROP TABLE IF EXISTS `" + tableName + "`");
    }

    private void unloadIfPresent(String pluginId) {
        if (pluginManager.getPlugin(pluginId) != null) {
            pluginManager.stopPlugin(pluginId);
            pluginManager.unloadPlugin(pluginId);
        }
    }

    private boolean hasMigrationTempDirectory(String pluginId) throws IOException {
        String prefix = "wf_plugin_migration_" + flywayMigrator.getHistoryTable(pluginId)
            .substring("flyway_plugin_".length()) + "_";
        try (var paths = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return paths.anyMatch(path -> path.getFileName().toString().startsWith(prefix));
        }
    }
}
