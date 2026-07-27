package com.workforcehub.plugin;

import com.workforcehub.plugin.repository.PluginRow;
import com.workforcehub.plugin.repository.PluginRepository;
import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.runtime.PluginFlywayMigrator;
import com.workforcehub.plugin.runtime.PluginLifecycleGuard;
import com.workforcehub.plugin.runtime.PluginManagerService;
import com.workforcehub.plugin.runtime.PluginSignatureVerifier;
import com.workforcehub.plugin.runtime.PluginVersionService;
import com.workforcehub.plugin.runtime.WorkforcePluginManager;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import com.workforcehub.support.LocalIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.pf4j.PluginClassLoader;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "workforce.plugin.root=target/test-plugins")
@ActiveProfiles("api")
class PluginManagerServiceIntegrationTest extends LocalIntegrationTestSupport {

    private static final String SUCCESS_PLUGIN_ID = "migration-plugin-success";
    private static final String FAILURE_PLUGIN_ID = "migration-plugin-failure";
    private static final String DEPENDENCY_FAILURE_PLUGIN_ID = "dependency-resolution-failure";
    private static final String UNRESOLVED_DEPENDENT_PLUGIN_ID = "existing-unresolved-dependent";
    private static final String PERSISTENCE_FAILURE_PLUGIN_ID = "migration-plugin-persistence-failure";
    private static final String TAMPERED_PLUGIN_ID = "migration-plugin-tampered";
    private static final String HELLO_PLUGIN_ID = "hello-plugin";
    private static final String HELLO_PLUGIN_FRONTEND_SRI =
        "sha384-c527zMMh7jwa0gTNl9fI1Mg5xEqGXyEFnTFCcjr7fokaoyPHzkIY7kC9f13c4zUQ";
    private static final String PERSISTENCE_GUARD_TABLE = "plugin_migration_persistence_guard";
    private static final String TEST_SIGNER_ID = "plugin-integration-test";
    private static final KeyPair SIGNING_KEY = createSigningKey();

    @DynamicPropertySource
    static void configureTrustedSigner(DynamicPropertyRegistry registry) {
        registry.add(
            "workforce.plugin.trusted-signers",
            () -> TEST_SIGNER_ID + "="
                + Base64.getEncoder().encodeToString(SIGNING_KEY.getPublic().getEncoded())
        );
    }

    @TempDir
    Path tempDir;

    @Autowired
    private PluginManagerService pluginManagerService;

    @SpyBean
    private PluginRepository pluginRepository;

    @SpyBean
    private WorkforcePluginManager pluginManager;

    @Autowired
    private PluginFlywayMigrator flywayMigrator;

    @Autowired
    private PluginLifecycleGuard lifecycleGuard;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetPluginState() {
        unloadIfPresent(SUCCESS_PLUGIN_ID);
        unloadIfPresent(FAILURE_PLUGIN_ID);
        unloadIfPresent(DEPENDENCY_FAILURE_PLUGIN_ID);
        unloadIfPresent(UNRESOLVED_DEPENDENT_PLUGIN_ID);
        unloadIfPresent(PERSISTENCE_FAILURE_PLUGIN_ID);
        unloadIfPresent(TAMPERED_PLUGIN_ID);
        unloadIfPresent(HELLO_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin WHERE plugin_id IN (?, ?, ?, ?, ?, ?)",
            SUCCESS_PLUGIN_ID,
            FAILURE_PLUGIN_ID,
            DEPENDENCY_FAILURE_PLUGIN_ID,
            PERSISTENCE_FAILURE_PLUGIN_ID,
            TAMPERED_PLUGIN_ID,
            HELLO_PLUGIN_ID
        );
        dropTable("plugin_migration_success");
        dropTable(PERSISTENCE_GUARD_TABLE);
        dropTable(flywayMigrator.getHistoryTable(SUCCESS_PLUGIN_ID));
        dropTable(flywayMigrator.getHistoryTable(FAILURE_PLUGIN_ID));
        dropTable(flywayMigrator.getHistoryTable(DEPENDENCY_FAILURE_PLUGIN_ID));
        dropTable(flywayMigrator.getHistoryTable(PERSISTENCE_FAILURE_PLUGIN_ID));
        dropTable(flywayMigrator.getHistoryTable(TAMPERED_PLUGIN_ID));
        clearTestSnapshots();
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

        PluginRow installed = pluginManagerService.install(jarPath, metadata("Migration Success", jarPath));

        Path snapshotPath = Path.of(installed.getJarPath());
        PluginRow persisted = pluginManagerService.getPlugin(SUCCESS_PLUGIN_ID);
        assertEquals(SUCCESS_PLUGIN_ID, installed.getPluginId());
        assertEquals(TEST_SIGNER_ID, installed.getSignerId());
        assertEquals(sha256(jarPath), installed.getJarSha256());
        assertEquals("FUNCTIONAL", persisted.getType());
        assertEquals("Workforce Hub", persisted.getProvider());
        assertNull(persisted.getFrontendEntry());
        assertNull(persisted.getFrontendSha384());
        assertNotEquals(jarPath.toAbsolutePath(), snapshotPath);
        assertTrue(snapshotPath.startsWith(
            pluginManager.getPluginsRoot().toAbsolutePath().normalize().resolve(".snapshots")
        ));
        assertEquals(installed.getJarSha256(), sha256(snapshotPath));
        assertEquals(snapshotPath.toString(), persisted.getJarPath());
        Files.delete(jarPath);
        assertFalse(Files.exists(jarPath));
        assertTrue(Files.isRegularFile(snapshotPath));
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
        assertTrue(Files.isRegularFile(snapshotPath));
        assertFalse(hasMigrationTempDirectory(SUCCESS_PLUGIN_ID));
    }

    @Test
    void shouldSurviveOneHundredSerialEnableDisableCycles() throws Exception {
        Path jarPath = buildRealHelloPluginJar();
        pluginManagerService.install(jarPath, metadata("Hello Plugin", jarPath));

        PluginWrapper wrapper = pluginManager.getPlugin(HELLO_PLUGIN_ID);
        assertNotNull(wrapper);
        ClassLoader classLoader = pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID);
        assertNotNull(classLoader);
        PluginClassLoader pluginClassLoader = assertInstanceOf(PluginClassLoader.class, classLoader);

        for (int cycle = 1; cycle <= 100; cycle++) {
            String round = "第 " + cycle + " 轮";
            pluginManagerService.enable(HELLO_PLUGIN_ID);

            assertEquals(PluginStatus.ACTIVE,
                pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus(), round);
            assertEquals(PluginState.STARTED,
                pluginManager.getPlugin(HELLO_PLUGIN_ID).getPluginState(), round);
            assertSame(wrapper, pluginManager.getPlugin(HELLO_PLUGIN_ID), round);
            assertSame(classLoader, pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID), round);
            var handlers = pluginManager.getExtensions(PluginActionHandler.class, HELLO_PLUGIN_ID);
            assertEquals(1, handlers.size(), round);
            assertEquals("hello.greet", handlers.get(0).getDescriptor().actionCode(), round);
            assertTrue(lifecycleGuard.tryAcquire(HELLO_PLUGIN_ID), round);
            lifecycleGuard.release(HELLO_PLUGIN_ID);
            assertEquals(0, lifecycleGuard.getActiveCount(HELLO_PLUGIN_ID), round);

            pluginManagerService.disable(HELLO_PLUGIN_ID);

            assertEquals(PluginStatus.STOPPED,
                pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus(), round);
            assertEquals(PluginState.STOPPED,
                pluginManager.getPlugin(HELLO_PLUGIN_ID).getPluginState(), round);
            assertSame(wrapper, pluginManager.getPlugin(HELLO_PLUGIN_ID), round);
            assertSame(classLoader, pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID), round);
            assertTrue(pluginManager.getExtensions(PluginActionHandler.class, HELLO_PLUGIN_ID).isEmpty(), round);
            assertTrue(pluginManagerService.findActionHandler(HELLO_PLUGIN_ID, "hello.greet").isEmpty(), round);
            assertFalse(lifecycleGuard.tryAcquire(HELLO_PLUGIN_ID), round);
            assertEquals(0, lifecycleGuard.getActiveCount(HELLO_PLUGIN_ID), round);
            assertEquals(1L,
                pluginManager.getPlugins().stream()
                    .filter(item -> HELLO_PLUGIN_ID.equals(item.getPluginId()))
                    .count(),
                round);
        }

        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin WHERE plugin_id = ?",
            Integer.class,
            HELLO_PLUGIN_ID
        ));

        pluginManagerService.uninstall(HELLO_PLUGIN_ID);

        assertEquals(PluginStatus.UNINSTALLED,
            pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus());
        assertNull(pluginManager.getPlugin(HELLO_PLUGIN_ID));
        assertNull(pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID));
        assertTrue(pluginClassLoader.isClosed());
        assertTrue(pluginManager.getExtensions(PluginActionHandler.class).stream()
            .noneMatch(handler -> {
                PluginWrapper owner = pluginManager.whichPlugin(handler.getClass());
                return owner != null && HELLO_PLUGIN_ID.equals(owner.getPluginId());
            }));
        assertFalse(lifecycleGuard.tryAcquire(HELLO_PLUGIN_ID));
        assertEquals(0, lifecycleGuard.getActiveCount(HELLO_PLUGIN_ID));
    }

    @Test
    void shouldUnloadPluginWhenMigrationFails() throws Exception {
        Path jarPath = buildPluginJar(FAILURE_PLUGIN_ID, "THIS IS NOT VALID SQL;");

        assertThrows(
            PluginFlywayMigrator.PluginMigrationException.class,
            () -> pluginManagerService.install(jarPath, metadata("Migration Failure", jarPath))
        );

        assertNull(pluginManager.getPlugin(FAILURE_PLUGIN_ID));
        assertFalse(pluginManagerService.listPlugins().stream()
            .anyMatch(plugin -> FAILURE_PLUGIN_ID.equals(plugin.getPluginId())));
        assertFalse(hasMigrationTempDirectory(FAILURE_PLUGIN_ID));
    }

    @Test
    void shouldUnloadPluginWhenDependencyResolutionFailsBeforeLoadReturns() throws Exception {
        Path jarPath = buildPluginJar(
            DEPENDENCY_FAILURE_PLUGIN_ID,
            "SELECT 1;",
            "missing-required-plugin"
        );

        RuntimeException thrown = assertThrows(
            RuntimeException.class,
            () -> pluginManagerService.install(jarPath, metadata("Dependency Failure", jarPath))
        );

        assertFalse(thrown instanceof PluginVersionService.PluginVersionException);
        assertNotNull(thrown.getMessage());
        assertTrue(thrown.getMessage().contains("missing-required-plugin"));
        assertNull(pluginManager.getPlugin(DEPENDENCY_FAILURE_PLUGIN_ID));
        assertNull(pluginManager.getPluginClassLoader(DEPENDENCY_FAILURE_PLUGIN_ID));
        assertFalse(pluginManagerService.listPlugins().stream()
            .anyMatch(plugin -> DEPENDENCY_FAILURE_PLUGIN_ID.equals(plugin.getPluginId())));
        assertSnapshotDirectoryEmpty();
    }

    @Test
    void shouldUnloadPluginWhenRestoreDependencyResolutionFailsBeforeLoadReturns() throws Exception {
        Path jarPath = buildPluginJar(
            DEPENDENCY_FAILURE_PLUGIN_ID,
            "SELECT 1;",
            "missing-required-plugin"
        );
        jdbcTemplate.update("""
            INSERT INTO sys_plugin
                (plugin_id, name, version, sdk_version, min_host_version, max_host_version,
                 java_version, type, status, jar_path, jar_sha256, signature, signer_id,
                 provider, installed_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """,
            DEPENDENCY_FAILURE_PLUGIN_ID,
            "Dependency Restore Failure",
            "0.1.0",
            "0.1.0",
            "0.1.0",
            "0.2.0",
            "17",
            "FUNCTIONAL",
            PluginStatus.INSTALLED.name(),
            jarPath.toAbsolutePath().toString(),
            sha256(jarPath),
            sign(jarPath),
            TEST_SIGNER_ID,
            "Workforce Hub"
        );

        pluginManagerService.restorePlugins();

        assertNull(pluginManager.getPlugin(DEPENDENCY_FAILURE_PLUGIN_ID));
        assertNull(pluginManager.getPluginClassLoader(DEPENDENCY_FAILURE_PLUGIN_ID));
        PluginRow failed = pluginManagerService.getPlugin(DEPENDENCY_FAILURE_PLUGIN_ID);
        assertEquals(PluginStatus.ERROR, failed.getStatus());
        assertNotEquals("插件清单与安装记录不一致", failed.getErrorMessage());
        assertTrue(failed.getErrorMessage().contains("missing-required-plugin"));
    }

    @Test
    void shouldNotUnloadExistingUnresolvedDependentWhenFailedPluginCleanupRuns() throws Exception {
        Path dependentJar = buildPluginJar(
            UNRESOLVED_DEPENDENT_PLUGIN_ID,
            "SELECT 1;",
            DEPENDENCY_FAILURE_PLUGIN_ID
        );
        assertThrows(RuntimeException.class, () -> pluginManager.loadPlugin(dependentJar));
        assertNotNull(pluginManager.getPlugin(UNRESOLVED_DEPENDENT_PLUGIN_ID));

        Path failedJar = buildPluginJar(
            DEPENDENCY_FAILURE_PLUGIN_ID,
            "SELECT 1;",
            "missing-required-plugin"
        );
        RuntimeException thrown = assertThrows(
            RuntimeException.class,
            () -> pluginManagerService.install(failedJar, metadata("Dependency Failure", failedJar))
        );

        assertFalse(thrown instanceof PluginVersionService.PluginVersionException);
        assertNotNull(thrown.getMessage());
        assertTrue(thrown.getMessage().contains("missing-required-plugin"));
        assertEquals(0, thrown.getSuppressed().length);
        assertNotNull(pluginManager.getPlugin(UNRESOLVED_DEPENDENT_PLUGIN_ID));
        assertNotNull(pluginManager.getPluginClassLoader(UNRESOLVED_DEPENDENT_PLUGIN_ID));
        assertNull(pluginManager.getPlugin(DEPENDENCY_FAILURE_PLUGIN_ID));
        assertNull(pluginManager.getPluginClassLoader(DEPENDENCY_FAILURE_PLUGIN_ID));
        assertSnapshotDirectoryEmpty();
    }

    @Test
    void shouldRejectExistingPluginBeforeMigration() throws Exception {
        jdbcTemplate.update("""
            INSERT INTO sys_plugin
                (plugin_id, name, version, sdk_version, java_version, type, status, jar_path)
            VALUES (?, 'Existing Plugin', '0.1.0', '1.0.0', '17', 'FUNCTIONAL', 'INSTALLED', 'existing.jar')
            """, PERSISTENCE_FAILURE_PLUGIN_ID);
        Path jarPath = buildPluginJar(
            PERSISTENCE_FAILURE_PLUGIN_ID,
            "CREATE TABLE " + PERSISTENCE_GUARD_TABLE + " (id INT PRIMARY KEY);"
        );

        assertThrows(
            IllegalStateException.class,
            () -> pluginManagerService.install(jarPath, metadata("Persistence Failure", jarPath))
        );

        assertNull(pluginManager.getPlugin(PERSISTENCE_FAILURE_PLUGIN_ID));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin WHERE plugin_id = ?",
            Integer.class,
            PERSISTENCE_FAILURE_PLUGIN_ID
        ));
        assertEquals(0, tableExists(PERSISTENCE_GUARD_TABLE));
        assertEquals(0, tableExists(flywayMigrator.getHistoryTable(PERSISTENCE_FAILURE_PLUGIN_ID)));
        assertFalse(hasMigrationTempDirectory(PERSISTENCE_FAILURE_PLUGIN_ID));
    }

    @Test
    void shouldUnloadAndDeleteSnapshotWhenPersistenceFailsAfterMigration() throws Exception {
        Path jarPath = buildPluginJar(
            PERSISTENCE_FAILURE_PLUGIN_ID,
            "CREATE TABLE " + PERSISTENCE_GUARD_TABLE + " (id INT PRIMARY KEY);"
        );
        doThrow(new DataAccessResourceFailureException("simulated insert failure"))
            .when(pluginRepository)
            .insert(argThat(row -> PERSISTENCE_FAILURE_PLUGIN_ID.equals(row.getPluginId())));

        assertThrows(
            DataAccessResourceFailureException.class,
            () -> pluginManagerService.install(jarPath, metadata("Persistence Failure", jarPath))
        );

        verify(pluginRepository).insert(argThat(
            row -> PERSISTENCE_FAILURE_PLUGIN_ID.equals(row.getPluginId())
        ));
        assertNull(pluginManager.getPlugin(PERSISTENCE_FAILURE_PLUGIN_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin WHERE plugin_id = ?",
            Integer.class,
            PERSISTENCE_FAILURE_PLUGIN_ID
        ));
        assertEquals(1, tableExists(PERSISTENCE_GUARD_TABLE));
        assertEquals(1, tableExists(flywayMigrator.getHistoryTable(PERSISTENCE_FAILURE_PLUGIN_ID)));
        assertFalse(hasMigrationTempDirectory(PERSISTENCE_FAILURE_PLUGIN_ID));
        assertSnapshotDirectoryEmpty();
    }

    @Test
    void shouldRejectTamperedJarBeforePf4jLoad() throws Exception {
        Path jarPath = buildPluginJar(TAMPERED_PLUGIN_ID, "SELECT 1;");
        PluginRow metadata = metadata("Tampered Plugin", jarPath);
        Files.writeString(jarPath, "tampered");

        assertThrows(
            PluginSignatureVerifier.PluginSignatureException.class,
            () -> pluginManagerService.install(jarPath, metadata)
        );

        assertNull(pluginManager.getPlugin(TAMPERED_PLUGIN_ID));
        assertFalse(pluginManagerService.listPlugins().stream()
            .anyMatch(plugin -> TAMPERED_PLUGIN_ID.equals(plugin.getPluginId())));
        assertEquals(0, tableExists(flywayMigrator.getHistoryTable(TAMPERED_PLUGIN_ID)));
        assertFalse(hasMigrationTempDirectory(TAMPERED_PLUGIN_ID));
    }

    @ParameterizedTest
    @EnumSource(ManifestDrift.class)
    void shouldFailClosedBeforePf4jLoadWhenRealUiManifestPersistenceDrifts(
            ManifestDrift drift) throws Exception {
        Path jarPath = buildRealHelloPluginJar();

        pluginManagerService.install(jarPath, metadata("Hello Plugin", jarPath));

        PluginRow persisted = pluginRepository.findById(HELLO_PLUGIN_ID).orElseThrow();
        assertEquals("UI", persisted.getType());
        assertEquals("Workforce Hub", persisted.getProvider());
        assertEquals("/plugins/hello-plugin/index.js", persisted.getFrontendEntry());
        assertEquals(HELLO_PLUGIN_FRONTEND_SRI, persisted.getFrontendSha384());
        assertEquals(
            "com.workforcehub.plugin.hello.HelloPlugin",
            pluginManager.getPlugin(HELLO_PLUGIN_ID).getPlugin().getClass().getName()
        );

        assertTrue(pluginManager.unloadPlugin(HELLO_PLUGIN_ID));
        assertNull(pluginManager.getPlugin(HELLO_PLUGIN_ID));
        clearInvocations(pluginManager);
        jdbcTemplate.update(
            "UPDATE sys_plugin SET " + drift.column + " = ? WHERE plugin_id = ?",
            drift.value,
            HELLO_PLUGIN_ID
        );

        pluginManagerService.restorePlugins();

        PluginRow failed = pluginRepository.findById(HELLO_PLUGIN_ID).orElseThrow();
        assertEquals(PluginStatus.ERROR, failed.getStatus());
        assertEquals("插件清单与安装记录不一致", failed.getErrorMessage());
        verify(pluginManager, never()).loadPlugin(any(Path.class));
        assertNull(pluginManager.getPlugin(HELLO_PLUGIN_ID));
        assertNull(pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID));
    }

    private PluginRow metadata(String name, Path jarPath) throws Exception {
        PluginRow row = new PluginRow();
        row.setName(name);
        row.setSignerId(TEST_SIGNER_ID);
        row.setSignature(sign(jarPath));
        return row;
    }

    private static KeyPair createSigningKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private byte[] sign(Path jarPath) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(SIGNING_KEY.getPrivate());
        signer.update(Files.readAllBytes(jarPath));
        return signer.sign();
    }

    private String sha256(Path jarPath) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jarPath))
        );
    }

    private Path buildPluginJar(String pluginId, String migrationSql) throws IOException {
        return buildPluginJar(pluginId, migrationSql, null);
    }

    private Path buildPluginJar(String pluginId, String migrationSql, String dependencies) throws IOException {
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
            plugin.dependencies=%s
            """.formatted(pluginId, dependencies == null ? "" : dependencies));

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
            output.putNextEntry(new JarEntry("plugin.json"));
            output.write("""
                {
                  "id":"%s",
                  "version":"0.1.0",
                  "sdkVersion":"0.1.0",
                  "minHostVersion":"0.1.0",
                  "maxHostVersion":"0.2.0",
                  "javaVersion":"17",
                  "type":"FUNCTIONAL",
                  "provider":"Workforce Hub"
                }
                """.formatted(pluginId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return jar;
    }

    private Path buildRealHelloPluginJar() throws IOException {
        Path pluginSource = Path.of("..", "plugins", "hello-plugin", "src", "main")
            .toAbsolutePath()
            .normalize();
        Path javaSource = pluginSource.resolve("java/com/workforcehub/plugin/hello");
        Path classes = tempDir.resolve("hello-plugin-real-classes");
        Files.createDirectories(classes);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "需要 JDK");
        int result = compiler.run(
            null, null, null,
            "-classpath", System.getProperty("java.class.path"),
            "-d", classes.toString(),
            javaSource.resolve("HelloPlugin.java").toString(),
            javaSource.resolve("HelloActionHandler.java").toString()
        );
        assertEquals(0, result, "编译失败");

        Path resources = pluginSource.resolve("resources");
        Files.copy(resources.resolve("plugin.properties"), classes.resolve("plugin.properties"));
        Files.copy(resources.resolve("plugin.json"), classes.resolve("plugin.json"));

        Path jar = tempDir.resolve("hello-plugin-real.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar));
                var files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String entryName = classes.relativize(file).toString().replace('\\', '/');
                output.putNextEntry(new JarEntry(entryName));
                Files.copy(file, output);
                output.closeEntry();
            }
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
        lifecycleGuard.remove(pluginId);
    }

    private boolean hasMigrationTempDirectory(String pluginId) throws IOException {
        String prefix = "wf_plugin_migration_" + flywayMigrator.getHistoryTable(pluginId)
            .substring("flyway_plugin_".length()) + "_";
        try (var paths = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return paths.anyMatch(path -> path.getFileName().toString().startsWith(prefix));
        }
    }

    private void clearTestSnapshots() {
        Path snapshotDirectory = pluginManager.getPluginsRoot()
            .toAbsolutePath()
            .normalize()
            .resolve(".snapshots");
        if (!Files.isDirectory(snapshotDirectory)) {
            return;
        }
        try (var snapshots = Files.list(snapshotDirectory)) {
            for (Path snapshot : snapshots.filter(Files::isRegularFile).toList()) {
                Files.deleteIfExists(snapshot);
            }
        } catch (IOException e) {
            throw new IllegalStateException("清理测试插件快照失败", e);
        }
    }

    private void assertSnapshotDirectoryEmpty() throws IOException {
        Path snapshotDirectory = pluginManager.getPluginsRoot()
            .toAbsolutePath()
            .normalize()
            .resolve(".snapshots");
        assertTrue(Files.isDirectory(snapshotDirectory));
        try (var snapshots = Files.list(snapshotDirectory)) {
            assertEquals(0, snapshots.filter(Files::isRegularFile).count());
        }
    }

    private enum ManifestDrift {
        TYPE("type", "FUNCTIONAL"),
        PROVIDER("provider", "Tampered Provider"),
        FRONTEND_ENTRY("frontend_entry", "/plugins/tampered/index.js"),
        FRONTEND_SHA384("frontend_sha384", "sha384-" + "B".repeat(64));

        private final String column;
        private final String value;

        ManifestDrift(String column, String value) {
            this.column = column;
            this.value = value;
        }
    }
}
