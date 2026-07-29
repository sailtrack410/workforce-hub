package com.workforcehub.plugin;

import com.jayway.jsonpath.JsonPath;
import com.workforcehub.auth.security.AdminUserDetails;
import com.workforcehub.plugin.repository.PluginRow;
import com.workforcehub.plugin.repository.PluginRepository;
import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.runtime.PluginFlywayMigrator;
import com.workforcehub.plugin.runtime.PluginLifecycleGuard;
import com.workforcehub.plugin.runtime.PluginManagerService;
import com.workforcehub.plugin.runtime.PluginPermissionService;
import com.workforcehub.plugin.runtime.PluginSignatureVerifier;
import com.workforcehub.plugin.runtime.PluginVersionService;
import com.workforcehub.plugin.runtime.WorkforcePluginManager;
import com.workforcehub.plugin.sdk.HostService;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest(properties = "workforce.plugin.root=target/test-plugins")
@AutoConfigureMockMvc
@ActiveProfiles("api")
class PluginManagerServiceIntegrationTest extends LocalIntegrationTestSupport {

    private static final String SUCCESS_PLUGIN_ID = "migration-plugin-success";
    private static final String FAILURE_PLUGIN_ID = "migration-plugin-failure";
    private static final String DEPENDENCY_FAILURE_PLUGIN_ID = "dependency-resolution-failure";
    private static final String UNRESOLVED_DEPENDENT_PLUGIN_ID = "existing-unresolved-dependent";
    private static final String PERSISTENCE_FAILURE_PLUGIN_ID = "migration-plugin-persistence-failure";
    private static final String TAMPERED_PLUGIN_ID = "migration-plugin-tampered";
    private static final String REINSTALL_PLUGIN_ID = "plugin-reinstall";
    private static final String HELLO_PLUGIN_ID = "hello-plugin";
    private static final String HELLO_PLUGIN_FRONTEND_SRI =
        "sha384-c527zMMh7jwa0gTNl9fI1Mg5xEqGXyEFnTFCcjr7fokaoyPHzkIY7kC9f13c4zUQ";
    private static final String PERSISTENCE_GUARD_TABLE = "plugin_migration_persistence_guard";
    private static final String REINSTALL_DATA_TABLE = "plugin_reinstall_data";
    private static final String REINSTALL_TRANSACTION_GUARD_TABLE =
        "plugin_reinstall_transaction_guard";
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
    private PluginSignatureVerifier signatureVerifier;

    @Autowired
    private PluginVersionService versionService;

    @Autowired
    private PluginPermissionService permissionService;

    @Autowired
    private List<HostService> hostServices;

    @Autowired
    private PluginLifecycleGuard lifecycleGuard;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetPluginState() {
        unloadIfPresent(SUCCESS_PLUGIN_ID);
        unloadIfPresent(FAILURE_PLUGIN_ID);
        unloadIfPresent(DEPENDENCY_FAILURE_PLUGIN_ID);
        unloadIfPresent(UNRESOLVED_DEPENDENT_PLUGIN_ID);
        unloadIfPresent(PERSISTENCE_FAILURE_PLUGIN_ID);
        unloadIfPresent(TAMPERED_PLUGIN_ID);
        unloadIfPresent(REINSTALL_PLUGIN_ID);
        unloadIfPresent(HELLO_PLUGIN_ID);
        dropTable(REINSTALL_TRANSACTION_GUARD_TABLE);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_action_idempotency WHERE plugin_id = ?", REINSTALL_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_operation_idempotency WHERE plugin_id = ?", REINSTALL_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_event_subscription WHERE plugin_id = ?", REINSTALL_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_dependency WHERE plugin_id = ? OR depends_on = ?",
            REINSTALL_PLUGIN_ID,
            REINSTALL_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_visibility WHERE plugin_id = ?", REINSTALL_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_action_permission WHERE plugin_id = ?", REINSTALL_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_capability WHERE plugin_id = ?", REINSTALL_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_config WHERE plugin_id = ?", REINSTALL_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_action_idempotency WHERE plugin_id = ?", HELLO_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_action_permission WHERE plugin_id = ?", HELLO_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_capability WHERE plugin_id = ?", HELLO_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_config WHERE plugin_id = ?", HELLO_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin WHERE plugin_id IN (?, ?, ?, ?, ?, ?)",
            SUCCESS_PLUGIN_ID,
            FAILURE_PLUGIN_ID,
            DEPENDENCY_FAILURE_PLUGIN_ID,
            PERSISTENCE_FAILURE_PLUGIN_ID,
            TAMPERED_PLUGIN_ID,
            HELLO_PLUGIN_ID
        );
        jdbcTemplate.update("DELETE FROM sys_plugin WHERE plugin_id = ?", REINSTALL_PLUGIN_ID);
        dropTable("plugin_migration_success");
        dropTable(PERSISTENCE_GUARD_TABLE);
        dropTable(REINSTALL_DATA_TABLE);
        dropTable(flywayMigrator.getHistoryTable(SUCCESS_PLUGIN_ID));
        dropTable(flywayMigrator.getHistoryTable(FAILURE_PLUGIN_ID));
        dropTable(flywayMigrator.getHistoryTable(DEPENDENCY_FAILURE_PLUGIN_ID));
        dropTable(flywayMigrator.getHistoryTable(PERSISTENCE_FAILURE_PLUGIN_ID));
        dropTable(flywayMigrator.getHistoryTable(TAMPERED_PLUGIN_ID));
        dropTable(flywayMigrator.getHistoryTable(REINSTALL_PLUGIN_ID));
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
    void shouldReplaceUninstalledSameIdAndPreserveDurableState() throws Exception {
        String firstMigration = """
            CREATE TABLE plugin_reinstall_data (
                id INT PRIMARY KEY,
                business_value VARCHAR(64) NOT NULL
            );
            """;
        Path firstJar = buildPluginJar(
            REINSTALL_PLUGIN_ID,
            "0.1.0",
            firstMigration,
            null,
            null
        );
        PluginRow firstInstall = pluginManagerService.install(
            firstJar,
            metadata("Reinstall Plugin v1", firstJar)
        );
        Path previousSnapshot = Path.of(firstInstall.getJarPath());
        PluginClassLoader previousClassLoader = assertInstanceOf(
            PluginClassLoader.class,
            pluginManager.getPluginClassLoader(REINSTALL_PLUGIN_ID)
        );

        jdbcTemplate.update(
            "INSERT INTO " + REINSTALL_DATA_TABLE + " (id, business_value) VALUES (1, 'preserved')");
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_config
                (plugin_id, config_key, config_value, value_type, default_value,
                 description, requires_restart, is_secret)
            VALUES (?, 'secret.key', 'encrypted-placeholder', 'STRING', 'default-value',
                    'preserved config', 1, 1)
            """, REINSTALL_PLUGIN_ID);
        jdbcTemplate.update(
            "INSERT INTO sys_plugin_capability (plugin_id, capability_code) VALUES (?, 'employee:read')",
            REINSTALL_PLUGIN_ID);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_visibility (plugin_id, scope_type, scope_value)
            VALUES (?, 'DEPT', 'dept-1')
            """, REINSTALL_PLUGIN_ID);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_action_permission (plugin_id, action_code, capability_code)
            VALUES (?, 'legacy.action', 'employee:read')
            """, REINSTALL_PLUGIN_ID);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_action_idempotency
                (plugin_id, action_code, caller_type, caller_id, idempotency_key,
                 request_hash, status, claim_token)
            VALUES (?, 'legacy.action', 'ADMIN', 1, 'legacy-action-key', ?, 'IN_PROGRESS',
                    '00000000-0000-0000-0000-000000000001')
            """, REINSTALL_PLUGIN_ID, "a".repeat(64));
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_operation_idempotency
                (plugin_id, operation_code, idempotency_key, request_hash)
            VALUES (?, 'event.subscribe', 'legacy-operation-key', ?)
            """, REINSTALL_PLUGIN_ID, "b".repeat(64));
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_event_subscription (plugin_id, event_type, action_code)
            VALUES (?, 'employee.updated', 'legacy.action')
            """, REINSTALL_PLUGIN_ID);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_dependency (plugin_id, depends_on, min_version)
            VALUES (?, 'legacy-dependency', '0.1.0')
            """, REINSTALL_PLUGIN_ID);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_dependency (plugin_id, depends_on, min_version)
            VALUES ('dependent-plugin', ?, '0.1.0')
            """, REINSTALL_PLUGIN_ID);

        pluginManagerService.uninstall(REINSTALL_PLUGIN_ID);

        assertTrue(previousClassLoader.isClosed());
        assertTrue(Files.isRegularFile(previousSnapshot));
        Path replacementJar = buildPluginJar(
            REINSTALL_PLUGIN_ID,
            "0.2.0",
            firstMigration,
            "ALTER TABLE plugin_reinstall_data ADD COLUMN version_marker VARCHAR(32);",
            null
        );

        PluginRow replacement = pluginManagerService.install(
            replacementJar,
            metadata("Reinstall Plugin v2", replacementJar)
        );

        Path replacementSnapshot = Path.of(replacement.getJarPath());
        PluginRow persisted = pluginManagerService.getPlugin(REINSTALL_PLUGIN_ID);
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin WHERE plugin_id = ?",
            Integer.class,
            REINSTALL_PLUGIN_ID
        ));
        assertEquals(PluginStatus.INSTALLED, persisted.getStatus());
        assertEquals("0.2.0", persisted.getVersion());
        assertEquals(TEST_SIGNER_ID, persisted.getSignerId());
        assertEquals(sha256(replacementJar), persisted.getJarSha256());
        assertEquals(replacementSnapshot.toString(), persisted.getJarPath());
        assertNull(persisted.getErrorMessage());
        assertFalse(Files.exists(previousSnapshot));
        assertTrue(Files.isRegularFile(replacementSnapshot));
        assertEquals(persisted.getJarSha256(), sha256(replacementSnapshot));
        assertNotSame(previousClassLoader, pluginManager.getPluginClassLoader(REINSTALL_PLUGIN_ID));
        assertEquals("0.2.0",
            pluginManager.getPlugin(REINSTALL_PLUGIN_ID).getDescriptor().getVersion());
        assertEquals(1L, pluginManager.getPlugins().stream()
            .filter(wrapper -> REINSTALL_PLUGIN_ID.equals(wrapper.getPluginId()))
            .count());
        assertFalse(lifecycleGuard.tryAcquire(REINSTALL_PLUGIN_ID));

        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + REINSTALL_DATA_TABLE
                + " WHERE id = 1 AND business_value = 'preserved'",
            Integer.class
        ));
        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = ?
              AND COLUMN_NAME = 'version_marker'
            """, Integer.class, REINSTALL_DATA_TABLE));
        assertEquals(2, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + flywayMigrator.getHistoryTable(REINSTALL_PLUGIN_ID)
                + " WHERE version IN ('1', '2') AND type = 'SQL' AND success = 1",
            Integer.class
        ));
        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_plugin_config
            WHERE plugin_id = ? AND config_key = 'secret.key'
              AND config_value = 'encrypted-placeholder' AND value_type = 'STRING'
              AND default_value = 'default-value' AND description = 'preserved config'
              AND requires_restart = 1 AND is_secret = 1
            """, Integer.class, REINSTALL_PLUGIN_ID));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_capability WHERE plugin_id = ?",
            Integer.class,
            REINSTALL_PLUGIN_ID
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_visibility WHERE plugin_id = ?",
            Integer.class,
            REINSTALL_PLUGIN_ID
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_action_permission WHERE plugin_id = ?",
            Integer.class,
            REINSTALL_PLUGIN_ID
        ));
        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_plugin_action_idempotency
            WHERE plugin_id = ? AND status = 'IN_PROGRESS'
              AND claim_token = '00000000-0000-0000-0000-000000000001'
            """, Integer.class, REINSTALL_PLUGIN_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_operation_idempotency WHERE plugin_id = ?",
            Integer.class,
            REINSTALL_PLUGIN_ID
        ));
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_event_subscription WHERE plugin_id = ?",
            Integer.class,
            REINSTALL_PLUGIN_ID
        ));
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_dependency WHERE plugin_id = ?",
            Integer.class,
            REINSTALL_PLUGIN_ID
        ));
        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_plugin_dependency
            WHERE plugin_id = 'dependent-plugin' AND depends_on = ?
            """, Integer.class, REINSTALL_PLUGIN_ID));
    }

    @Test
    void replaceUninstalledShouldRollbackRecordAndCleanupWhenRuntimeResetFails() {
        String previousJarPath = tempDir.resolve("previous-plugin.jar").toString();
        jdbcTemplate.update("""
            INSERT INTO sys_plugin
                (plugin_id, name, version, sdk_version, java_version, type,
                 status, jar_path, signer_id)
            VALUES (?, 'Reinstall Plugin v1', '0.1.0', '0.1.0', '17', 'FUNCTIONAL',
                    'UNINSTALLED', ?, ?)
            """, REINSTALL_PLUGIN_ID, previousJarPath, TEST_SIGNER_ID);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_operation_idempotency
                (plugin_id, operation_code, idempotency_key, request_hash)
            VALUES (?, 'event.subscribe', 'rollback-operation-key', ?)
            """, REINSTALL_PLUGIN_ID, "b".repeat(64));
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_event_subscription (plugin_id, event_type, action_code)
            VALUES (?, 'employee.updated', 'legacy.action')
            """, REINSTALL_PLUGIN_ID);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_dependency (plugin_id, depends_on, min_version)
            VALUES (?, 'legacy-dependency', '0.1.0')
            """, REINSTALL_PLUGIN_ID);
        jdbcTemplate.execute("""
            CREATE TABLE plugin_reinstall_transaction_guard (
                plugin_id VARCHAR(64) NOT NULL,
                depends_on VARCHAR(64) NOT NULL,
                PRIMARY KEY (plugin_id, depends_on),
                CONSTRAINT fk_plugin_reinstall_transaction_guard
                    FOREIGN KEY (plugin_id, depends_on)
                    REFERENCES sys_plugin_dependency (plugin_id, depends_on)
            ) ENGINE=InnoDB
            """);
        jdbcTemplate.update("""
            INSERT INTO plugin_reinstall_transaction_guard (plugin_id, depends_on)
            VALUES (?, 'legacy-dependency')
            """, REINSTALL_PLUGIN_ID);

        PluginRow replacement = new PluginRow();
        replacement.setPluginId(REINSTALL_PLUGIN_ID);
        replacement.setName("Reinstall Plugin v2");
        replacement.setVersion("0.2.0");
        replacement.setSdkVersion("0.1.0");
        replacement.setJavaVersion("17");
        replacement.setType("FUNCTIONAL");
        replacement.setStatus(PluginStatus.INSTALLED);
        replacement.setJarPath(tempDir.resolve("replacement-plugin.jar").toString());
        replacement.setJarSha256("c".repeat(64));
        replacement.setSignature(new byte[]{1, 2, 3});
        replacement.setSignerId(TEST_SIGNER_ID);
        replacement.setProvider("Workforce Hub");
        replacement.setInstalledAt(LocalDateTime.now());
        replacement.setUpdatedAt(LocalDateTime.now());

        try {
            assertThrows(
                DataAccessException.class,
                () -> pluginRepository.replaceUninstalled(
                    replacement,
                    previousJarPath,
                    TEST_SIGNER_ID
                )
            );
        } finally {
            dropTable(REINSTALL_TRANSACTION_GUARD_TABLE);
        }

        PluginRow persisted = pluginRepository.findById(REINSTALL_PLUGIN_ID).orElseThrow();
        assertEquals(PluginStatus.UNINSTALLED, persisted.getStatus());
        assertEquals("0.1.0", persisted.getVersion());
        assertEquals(previousJarPath, persisted.getJarPath());
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_operation_idempotency WHERE plugin_id = ?",
            Integer.class,
            REINSTALL_PLUGIN_ID
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_event_subscription WHERE plugin_id = ?",
            Integer.class,
            REINSTALL_PLUGIN_ID
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_dependency WHERE plugin_id = ?",
            Integer.class,
            REINSTALL_PLUGIN_ID
        ));
    }

    @Test
    void realPf4jPluginShouldReadBoundConfigAndUninstallShouldPreserveIt() throws Exception {
        Path jarPath = buildRealHelloPluginJar();
        pluginManagerService.install(jarPath, metadata("Hello Plugin", jarPath));
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_config
                (plugin_id, config_key, config_value, value_type)
            VALUES (?, 'shared.key', 'configured-value', 'STRING')
            """, HELLO_PLUGIN_ID);
        pluginManagerService.enable(HELLO_PLUGIN_ID);

        Object plugin = pluginManager.getPlugin(HELLO_PLUGIN_ID).getPlugin();
        assertEquals(
            "configured-value",
            plugin.getClass().getMethod("getConfigValue", String.class)
                .invoke(plugin, "shared.key")
        );
        assertNull(plugin.getClass().getMethod("getConfigValue", String.class)
            .invoke(plugin, "missing.key"));

        pluginManagerService.uninstall(HELLO_PLUGIN_ID);

        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_plugin_config
            WHERE plugin_id = ? AND config_key = 'shared.key'
            """, Integer.class, HELLO_PLUGIN_ID));
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
    void shouldRestoreActivePluginWithFreshManagerInSameJvm() throws Exception {
        Path jarPath = buildRealHelloPluginJar();
        PluginRow installed = pluginManagerService.install(jarPath, metadata("Hello Plugin", jarPath));
        Path snapshotPath = Path.of(installed.getJarPath());
        assertNotEquals(jarPath.toAbsolutePath(), snapshotPath);
        assertTrue(snapshotPath.startsWith(
            pluginManager.getPluginsRoot().toAbsolutePath().normalize().resolve(".snapshots")
        ));
        assertEquals(snapshotPath.toString(),
            pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getJarPath());
        Files.delete(jarPath);
        assertFalse(Files.exists(jarPath));
        assertTrue(Files.isRegularFile(snapshotPath));
        pluginManagerService.enable(HELLO_PLUGIN_ID);

        PluginWrapper oldWrapper = pluginManager.getPlugin(HELLO_PLUGIN_ID);
        assertNotNull(oldWrapper);
        PluginClassLoader oldClassLoader = assertInstanceOf(
            PluginClassLoader.class,
            pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID)
        );

        lifecycleGuard.beginDisable(HELLO_PLUGIN_ID);
        assertTrue(lifecycleGuard.awaitDrain(HELLO_PLUGIN_ID));
        assertEquals(PluginState.STOPPED, pluginManager.stopPlugin(HELLO_PLUGIN_ID));
        assertTrue(pluginManager.unloadPlugin(HELLO_PLUGIN_ID));
        lifecycleGuard.remove(HELLO_PLUGIN_ID);

        assertEquals(PluginStatus.ACTIVE,
            pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus());
        assertNull(pluginManager.getPlugin(HELLO_PLUGIN_ID));
        assertTrue(oldClassLoader.isClosed());
        assertFalse(lifecycleGuard.tryAcquire(HELLO_PLUGIN_ID));

        WorkforcePluginManager restartedManager = new WorkforcePluginManager(
            pluginManager.getPluginsRoot().toString(),
            hostServices,
            permissionService
        );
        PluginLifecycleGuard restartedGuard = new PluginLifecycleGuard();
        PluginManagerService restartedService = new PluginManagerService(
            restartedManager,
            pluginRepository,
            signatureVerifier,
            versionService,
            flywayMigrator,
            restartedGuard
        );

        try {
            restartedService.restorePlugins();

            PluginWrapper restartedWrapper = restartedManager.getPlugin(HELLO_PLUGIN_ID);
            assertNotNull(restartedWrapper);
            PluginClassLoader restartedClassLoader = assertInstanceOf(
                PluginClassLoader.class,
                restartedManager.getPluginClassLoader(HELLO_PLUGIN_ID)
            );
            assertEquals(PluginStatus.ACTIVE,
                restartedService.getPlugin(HELLO_PLUGIN_ID).getStatus());
            assertEquals(PluginState.STARTED, restartedWrapper.getPluginState());
            assertNotSame(oldWrapper, restartedWrapper);
            assertNotSame(oldClassLoader, restartedClassLoader);

            var handlers = restartedManager.getExtensions(PluginActionHandler.class, HELLO_PLUGIN_ID);
            assertEquals(1, handlers.size());
            assertEquals("hello.greet", handlers.get(0).getDescriptor().actionCode());
            assertTrue(restartedGuard.tryAcquire(HELLO_PLUGIN_ID));
            restartedGuard.release(HELLO_PLUGIN_ID);
            assertEquals(0, restartedGuard.getActiveCount(HELLO_PLUGIN_ID));

            restartedService.uninstall(HELLO_PLUGIN_ID);

            assertEquals(PluginStatus.UNINSTALLED,
                restartedService.getPlugin(HELLO_PLUGIN_ID).getStatus());
            assertNull(restartedManager.getPlugin(HELLO_PLUGIN_ID));
            assertTrue(restartedClassLoader.isClosed());
            assertFalse(restartedGuard.tryAcquire(HELLO_PLUGIN_ID));
        } finally {
            try {
                if (restartedManager.getPlugin(HELLO_PLUGIN_ID) != null) {
                    restartedGuard.beginDisable(HELLO_PLUGIN_ID);
                    restartedGuard.awaitDrain(HELLO_PLUGIN_ID);
                    try {
                        restartedManager.stopPlugin(HELLO_PLUGIN_ID);
                    } finally {
                        assertTrue(restartedManager.unloadPlugin(HELLO_PLUGIN_ID));
                    }
                }
            } finally {
                restartedGuard.remove(HELLO_PLUGIN_ID);
            }
        }
    }

    @Test
    void shouldRestoreActivePluginAcrossTwoIndependentJvmProcesses() throws Exception {
        Path jarPath = buildRealHelloPluginJar();
        PluginRow installed = pluginManagerService.install(jarPath, metadata("Hello Plugin", jarPath));
        Path snapshotPath = Path.of(installed.getJarPath());
        Files.delete(jarPath);
        assertFalse(Files.exists(jarPath));
        assertTrue(Files.isRegularFile(snapshotPath));
        pluginManagerService.enable(HELLO_PLUGIN_ID);

        PluginClassLoader parentClassLoader = assertInstanceOf(
            PluginClassLoader.class,
            pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID)
        );
        lifecycleGuard.beginDisable(HELLO_PLUGIN_ID);
        assertTrue(lifecycleGuard.awaitDrain(HELLO_PLUGIN_ID));
        assertEquals(PluginState.STOPPED, pluginManager.stopPlugin(HELLO_PLUGIN_ID));
        assertTrue(pluginManager.unloadPlugin(HELLO_PLUGIN_ID));
        lifecycleGuard.remove(HELLO_PLUGIN_ID);

        assertEquals(PluginStatus.ACTIVE,
            pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus());
        assertNull(pluginManager.getPlugin(HELLO_PLUGIN_ID));
        assertTrue(parentClassLoader.isClosed());
        assertFalse(lifecycleGuard.tryAcquire(HELLO_PLUGIN_ID));

        PluginRow activeBeforeRestart = pluginManagerService.getPlugin(HELLO_PLUGIN_ID);
        long parentPid = ProcessHandle.current().pid();
        long firstChildPid = runRestartProcessProbe("verify");
        assertNotEquals(parentPid, firstChildPid);
        PluginRow activeAfterFirstChild = pluginManagerService.getPlugin(HELLO_PLUGIN_ID);
        assertEquals(PluginStatus.ACTIVE, activeAfterFirstChild.getStatus());
        assertEquals(activeBeforeRestart.getUpdatedAt(), activeAfterFirstChild.getUpdatedAt());
        assertTrue(Files.isRegularFile(snapshotPath));

        long secondChildPid = runRestartProcessProbe("uninstall");
        assertNotEquals(parentPid, secondChildPid);
        assertNotEquals(firstChildPid, secondChildPid);
        assertEquals(PluginStatus.UNINSTALLED,
            pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus());
        assertTrue(Files.isRegularFile(snapshotPath));
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

    @Test
    void adminHttpShouldUninstallInstalledPluginWithoutStartingIt() throws Exception {
        Path jarPath = buildRealHelloPluginJar();
        pluginManagerService.install(jarPath, metadata("Hello Plugin", jarPath));

        PluginWrapper wrapper = pluginManager.getPlugin(HELLO_PLUGIN_ID);
        assertNotNull(wrapper);
        assertEquals(PluginState.RESOLVED, wrapper.getPluginState());
        PluginClassLoader classLoader = assertInstanceOf(
            PluginClassLoader.class,
            pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID)
        );
        clearInvocations(pluginManager);

        MvcResult uninstallResult = mockMvc.perform(
                delete("/api/admin/plugins/{pluginId}", HELLO_PLUGIN_ID)
                    .with(adminPluginManager())
                    .with(csrf().asHeader()))
            .andReturn();

        assertEquals(200, uninstallResult.getResponse().getStatus());
        assertEquals("UNINSTALLED", JsonPath.read(
            uninstallResult.getResponse().getContentAsString(), "$.data.status"));
        LocalDateTime responseUpdatedAt = LocalDateTime.parse(JsonPath.read(
            uninstallResult.getResponse().getContentAsString(), "$.data.updatedAt"));
        PluginRow persisted = pluginManagerService.getPlugin(HELLO_PLUGIN_ID);
        assertEquals(PluginStatus.UNINSTALLED, persisted.getStatus());
        assertEquals(persisted.getUpdatedAt(), responseUpdatedAt);
        verify(pluginManager, never()).stopPlugin(HELLO_PLUGIN_ID);
        assertNull(pluginManager.getPlugin(HELLO_PLUGIN_ID));
        assertNull(pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID));
        assertTrue(classLoader.isClosed());
        assertFalse(lifecycleGuard.tryAcquire(HELLO_PLUGIN_ID));
    }

    @Test
    void adminHttpShouldKeepPluginRecoverableWhenClassLoaderCloseFails() throws Exception {
        Path jarPath = buildRealHelloPluginJar();
        pluginManagerService.install(jarPath, metadata("Hello Plugin", jarPath));

        PluginClassLoader originalClassLoader = assertInstanceOf(
            PluginClassLoader.class,
            pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID)
        );
        PluginClassLoader failingClassLoader = mock(PluginClassLoader.class);
        doThrow(new IOException("close failed")).when(failingClassLoader).close();

        MvcResult failedResult;
        try {
            doReturn(failingClassLoader)
                .when(pluginManager)
                .getPluginClassLoader(HELLO_PLUGIN_ID);
            failedResult = mockMvc.perform(
                    delete("/api/admin/plugins/{pluginId}", HELLO_PLUGIN_ID)
                        .with(adminPluginManager())
                        .with(csrf().asHeader()))
                .andReturn();
        } finally {
            doCallRealMethod()
                .when(pluginManager)
                .getPluginClassLoader(HELLO_PLUGIN_ID);
        }

        assertEquals(500, failedResult.getResponse().getStatus());
        assertEquals(PluginStatus.ERROR, pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus());
        assertNotNull(pluginManager.getPlugin(HELLO_PLUGIN_ID));
        assertSame(originalClassLoader, pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID));
        assertFalse(originalClassLoader.isClosed());

        MvcResult retryResult = mockMvc.perform(
                delete("/api/admin/plugins/{pluginId}", HELLO_PLUGIN_ID)
                    .with(adminPluginManager())
                    .with(csrf().asHeader()))
            .andReturn();

        assertEquals(200, retryResult.getResponse().getStatus());
        assertEquals(PluginStatus.UNINSTALLED, pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus());
        assertNull(pluginManager.getPlugin(HELLO_PLUGIN_ID));
        assertNull(pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID));
        assertTrue(originalClassLoader.isClosed());
    }

    @Test
    void adminHttpLifecycleShouldDrainAndUnloadRealPlugin() throws Exception {
        Path jarPath = buildRealHelloPluginJar();
        pluginManagerService.install(jarPath, metadata("Hello Plugin", jarPath));
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_capability (plugin_id, capability_code)
            VALUES (?, 'HELLO_GREET')
            """, HELLO_PLUGIN_ID);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_action_permission (plugin_id, action_code, capability_code)
            VALUES (?, 'hello.greet', 'HELLO_GREET')
            """, HELLO_PLUGIN_ID);

        MvcResult enableResult = mockMvc.perform(
                put("/api/admin/plugins/{pluginId}/enable", HELLO_PLUGIN_ID)
                    .with(adminPluginManager())
                    .with(csrf().asHeader()))
            .andReturn();

        assertEquals(200, enableResult.getResponse().getStatus());
        assertEquals("ACTIVE", JsonPath.read(
            enableResult.getResponse().getContentAsString(), "$.data.status"));
        assertEquals(PluginStatus.ACTIVE, pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus());
        PluginWrapper wrapper = pluginManager.getPlugin(HELLO_PLUGIN_ID);
        assertNotNull(wrapper);
        assertEquals(PluginState.STARTED, wrapper.getPluginState());
        PluginClassLoader classLoader = assertInstanceOf(
            PluginClassLoader.class,
            pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID)
        );
        assertFalse(classLoader.isClosed());
        assertEquals(1, pluginManager.getExtensions(PluginActionHandler.class, HELLO_PLUGIN_ID).size());

        // 模拟已经越过网关 acquire 的在途请求，后续新请求仍通过真实 MockMvc 网关验证拒绝。
        assertTrue(lifecycleGuard.tryAcquire(HELLO_PLUGIN_ID));
        CompletableFuture<MvcResult> uninstallFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return mockMvc.perform(delete("/api/admin/plugins/{pluginId}", HELLO_PLUGIN_ID)
                        .with(adminPluginManager())
                        .with(csrf().asHeader()))
                    .andReturn();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        PluginStatus statusWhileDraining;
        boolean uninstallCompletedWhileHeld;
        MvcResult rejectedResult;
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus() != PluginStatus.DISABLING
                    && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(10);
            }
            statusWhileDraining = pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus();
            uninstallCompletedWhileHeld = uninstallFuture.isDone();
            rejectedResult = mockMvc.perform(post(
                    "/api/plugin-gateway/{pluginId}/actions/{actionCode}",
                    HELLO_PLUGIN_ID,
                    "hello.greet")
                    .with(adminPluginManager())
                    .with(csrf().asHeader())
                    .header(HttpHeaders.ORIGIN, "http://localhost")
                    .header("Idempotency-Key", "lifecycle-drain-key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"test\"}"))
                .andReturn();
        } finally {
            lifecycleGuard.release(HELLO_PLUGIN_ID);
        }

        MvcResult uninstallResult = uninstallFuture.get(10, TimeUnit.SECONDS);
        assertEquals(PluginStatus.DISABLING, statusWhileDraining);
        assertFalse(uninstallCompletedWhileHeld);
        assertEquals(409, rejectedResult.getResponse().getStatus());
        assertEquals(4009, ((Number) JsonPath.read(
            rejectedResult.getResponse().getContentAsString(), "$.code")).intValue());
        assertEquals(200, uninstallResult.getResponse().getStatus());
        assertEquals("UNINSTALLED", JsonPath.read(
            uninstallResult.getResponse().getContentAsString(), "$.data.status"));
        assertEquals(PluginStatus.UNINSTALLED, pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus());
        assertNull(pluginManager.getPlugin(HELLO_PLUGIN_ID));
        assertNull(pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID));
        assertTrue(classLoader.isClosed());
        assertTrue(pluginManager.getExtensions(PluginActionHandler.class).stream()
            .noneMatch(handler -> {
                PluginWrapper owner = pluginManager.whichPlugin(handler.getClass());
                return owner != null && HELLO_PLUGIN_ID.equals(owner.getPluginId());
            }));
        assertFalse(lifecycleGuard.tryAcquire(HELLO_PLUGIN_ID));
        assertEquals(0, lifecycleGuard.getActiveCount(HELLO_PLUGIN_ID));
        assertEquals(
            List.of("PLUGIN_ENABLED:SUCCESS", "PLUGIN_UNINSTALLED:SUCCESS"),
            jdbcTemplate.query("""
                SELECT action_type, result
                FROM sys_audit_log
                WHERE target_type = 'PLUGIN'
                  AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.pluginId')) = ?
                ORDER BY occurred_at, id
                """, (rs, rowNum) -> rs.getString("action_type") + ":" + rs.getString("result"),
                HELLO_PLUGIN_ID)
        );
    }

    private RequestPostProcessor adminPluginManager() {
        AdminUserDetails principal = new AdminUserDetails(
            8_001L,
            "admin.demo",
            "unused",
            "插件生命周期管理员",
            "ACTIVE",
            List.of(),
            List.of("ADMIN_PLUGIN_MANAGE")
        );
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            principal.getAuthorities()
        ));
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
        return buildPluginJar(pluginId, "0.1.0", migrationSql, null, dependencies);
    }

    private Path buildPluginJar(
            String pluginId,
            String version,
            String migrationSql,
            String secondMigrationSql,
            String dependencies) throws IOException {
        Path source = Path.of("..", "plugins", "hello-plugin", "src", "main", "java",
            "com", "workforcehub", "plugin", "hello", "HelloPlugin.java").toAbsolutePath().normalize();
        Path classes = tempDir.resolve(pluginId + "-" + version + "-classes");
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
            plugin.version=%s
            plugin.provider=Workforce Hub
            plugin.description=Plugin migration integration test
            plugin.dependencies=%s
            """.formatted(pluginId, version, dependencies == null ? "" : dependencies));

        Path jar = tempDir.resolve(pluginId + "-" + version + ".jar");
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
            if (secondMigrationSql != null) {
                output.putNextEntry(new JarEntry("db/migration/V2__replace.sql"));
                output.write(secondMigrationSql.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.closeEntry();
            }
            output.putNextEntry(new JarEntry("plugin.json"));
            output.write("""
                {
                  "id":"%s",
                  "version":"%s",
                  "sdkVersion":"0.1.0",
                  "minHostVersion":"0.1.0",
                  "maxHostVersion":"0.2.0",
                  "javaVersion":"17",
                  "type":"FUNCTIONAL",
                  "provider":"Workforce Hub"
                }
                """.formatted(pluginId, version).getBytes(java.nio.charset.StandardCharsets.UTF_8));
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

    private long runRestartProcessProbe(String mode) throws Exception {
        String configuredClasspath = System.getProperty(
            "surefire.test.class.path",
            System.getProperty("java.class.path")
        );
        Path workingDirectory = Files.createDirectory(tempDir.resolve("restart-process-" + mode));
        Path launchDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        String childClasspath = Arrays.stream(configuredClasspath.split(File.pathSeparator))
            .map(Path::of)
            .map(path -> path.isAbsolute() ? path : launchDirectory.resolve(path).normalize())
            .map(Path::toString)
            .collect(Collectors.joining(File.pathSeparator));
        ProcessBuilder processBuilder = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp",
            childClasspath,
            "com.workforcehub.plugin.PluginRestartProcessProbe",
            mode,
            "--spring.config.location=optional:classpath:/plugin-restart-process-none.properties",
            "--mybatis.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
        );
        processBuilder.directory(workingDirectory.toFile());
        processBuilder.redirectErrorStream(true);

        var childEnvironment = processBuilder.environment();
        childEnvironment.clear();
        childEnvironment.put("SPRING_PROFILES_ACTIVE", "api");
        childEnvironment.put("SPRING_MAIN_BANNER_MODE", "off");
        childEnvironment.put("SERVER_PORT", "0");
        String datasourceUrl = environment.getRequiredProperty("spring.datasource.url");
        String datasourceUsername = environment.getRequiredProperty("spring.datasource.username");
        String datasourcePassword = environment.getRequiredProperty("spring.datasource.password");
        String redisHost = environment.getRequiredProperty("spring.data.redis.host");
        String redisPort = environment.getRequiredProperty("spring.data.redis.port");
        String redisPassword = environment.getProperty("spring.data.redis.password", "");
        childEnvironment.put("SPRING_DATASOURCE_URL", datasourceUrl);
        childEnvironment.put("SPRING_DATASOURCE_USERNAME", datasourceUsername);
        childEnvironment.put("SPRING_DATASOURCE_PASSWORD", datasourcePassword);
        childEnvironment.put("SPRING_DATA_REDIS_HOST", redisHost);
        childEnvironment.put("SPRING_DATA_REDIS_PORT", redisPort);
        childEnvironment.put("SPRING_DATA_REDIS_PASSWORD", redisPassword);
        childEnvironment.put("SPRING_SESSION_REDIS_NAMESPACE",
            environment.getRequiredProperty("spring.session.redis.namespace"));
        childEnvironment.put("WORKFORCE_AUTH_REDIS_KEY_PREFIX",
            environment.getRequiredProperty("workforce.auth.redis-key-prefix"));
        childEnvironment.put("SPRING_AUTOCONFIGURE_EXCLUDE",
            environment.getRequiredProperty("spring.autoconfigure.exclude"));
        childEnvironment.put("WORKFORCE_PLUGIN_ROOT",
            pluginManager.getPluginsRoot().toAbsolutePath().normalize().toString());
        String trustedSigners = TEST_SIGNER_ID + "="
            + Base64.getEncoder().encodeToString(SIGNING_KEY.getPublic().getEncoded());
        childEnvironment.put("WORKFORCE_PLUGIN_TRUSTED_SIGNERS", trustedSigners);
        childEnvironment.put("WORKFORCE_WORKFLOW_ENABLED", "false");
        childEnvironment.put("WORKFORCE_AUTH_EMAIL_VERIFY_ENABLED", "false");
        childEnvironment.put("MANAGEMENT_HEALTH_MAIL_ENABLED", "false");
        childEnvironment.put("WORKFORCE_AGENT_MODEL_ENABLED", "false");
        childEnvironment.put("WORKFORCE_AGENT_TIMEOUT_SCAN_ENABLED", "false");
        childEnvironment.put("WORKFORCE_AGENT_PROVIDER_HEALTH_CHECK_ENABLED", "false");
        childEnvironment.put("WORKFORCE_IM_OUTBOX_DISPATCHER_ENABLED", "false");
        childEnvironment.put("FLOWABLE_ASYNC_EXECUTOR_ACTIVATE", "false");
        childEnvironment.put("WORKFORCE_PLUGIN_GATEWAY_CSRF_ALLOWED_ORIGINS", "http://127.0.0.1:8080");
        String mailHost = environment.getRequiredProperty("spring.mail.host");
        String mailPort = environment.getRequiredProperty("spring.mail.port");
        String mailUsername = environment.getProperty("spring.mail.username", "");
        String mailPassword = environment.getProperty("spring.mail.password", "");
        childEnvironment.put("SPRING_MAIL_HOST", mailHost);
        childEnvironment.put("SPRING_MAIL_PORT", mailPort);
        childEnvironment.put("SPRING_MAIL_USERNAME", mailUsername);
        childEnvironment.put("SPRING_MAIL_PASSWORD", mailPassword);
        String minioEndpoint = environment.getRequiredProperty("minio.endpoint");
        String minioAccessKey = environment.getRequiredProperty("minio.access-key");
        String minioSecretKey = environment.getRequiredProperty("minio.secret-key");
        String minioBucket = environment.getRequiredProperty("minio.bucket");
        childEnvironment.put("MINIO_ENDPOINT", minioEndpoint);
        childEnvironment.put("MINIO_ACCESS_KEY", minioAccessKey);
        childEnvironment.put("MINIO_SECRET_KEY", minioSecretKey);
        childEnvironment.put("MINIO_BUCKET", minioBucket);
        childEnvironment.put("WORKFORCE_FILE_LOCAL_ROOT",
            tempDir.resolve("restart-process-file-storage").toAbsolutePath().toString());
        childEnvironment.put("LOGGING_LEVEL_ROOT", "warn");
        childEnvironment.put("LOGGING_LEVEL_COM_WORKFORCEHUB", "info");

        List<String> sensitiveValues = new ArrayList<>();
        for (String value : List.of(
            datasourceUrl, datasourceUsername, datasourcePassword,
            redisHost, redisPassword, trustedSigners,
            mailHost, mailPort, mailUsername, mailPassword,
            minioEndpoint, minioAccessKey, minioSecretKey, minioBucket
        )) {
            if (value != null && !value.isBlank()) {
                sensitiveValues.add(value);
            }
        }
        sensitiveValues.sort(Comparator.comparingInt(String::length).reversed());

        Process process = processBuilder.start();
        long pid = process.pid();
        SanitizedLogTail logTail = new SanitizedLogTail(8192);
        Thread logReader = new Thread(
            () -> captureSanitizedLog(process, sensitiveValues, logTail),
            "plugin-restart-process-" + mode + "-log"
        );
        logReader.setDaemon(true);
        logReader.start();
        try {
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("插件重启子进程强制终止后仍存活，mode=" + mode);
                    }
                }
                awaitLogReaderTermination(process, logReader, mode);
                throw new AssertionError(
                    "插件重启子进程超时，mode=" + mode + "\n" + logTail.text()
                );
            }
            awaitLogReaderTermination(process, logReader, mode);
            assertEquals(
                0,
                process.exitValue(),
                () -> "插件重启子进程失败，mode=" + mode + "\n" + logTail.text()
            );
            return pid;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("插件重启子进程清理后仍存活，mode=" + mode);
                }
            }
            awaitLogReaderTermination(process, logReader, mode);
        }
    }

    private void awaitLogReaderTermination(Process process, Thread logReader, String mode)
        throws Exception {
        if (!logReader.isAlive()) {
            return;
        }
        try {
            process.getInputStream().close();
        } catch (IOException ignored) {
            // 子进程已结束时，关闭其输出管道失败不影响清理判定。
        }
        logReader.interrupt();
        logReader.join(5_000);
        if (logReader.isAlive()) {
            throw new AssertionError("插件重启子进程日志线程清理后仍存活，mode=" + mode);
        }
    }

    private void captureSanitizedLog(
        Process process,
        List<String> sensitiveValues,
        SanitizedLogTail logTail
    ) {
        try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String sanitized = line;
                for (String sensitiveValue : sensitiveValues) {
                    sanitized = sanitized.replace(sensitiveValue, "<redacted>");
                }
                logTail.append(sanitized);
                logTail.append(System.lineSeparator());
            }
        } catch (IOException e) {
            logTail.append("<无法读取子进程日志>");
        }
    }

    private static final class SanitizedLogTail {

        private final byte[] bytes;
        private int length;

        private SanitizedLogTail(int maxBytes) {
            this.bytes = new byte[maxBytes];
        }

        private synchronized void append(String value) {
            byte[] incoming = value.getBytes(StandardCharsets.UTF_8);
            if (incoming.length >= bytes.length) {
                System.arraycopy(incoming, incoming.length - bytes.length, bytes, 0, bytes.length);
                length = bytes.length;
                return;
            }
            int overflow = Math.max(0, length + incoming.length - bytes.length);
            if (overflow > 0) {
                System.arraycopy(bytes, overflow, bytes, 0, length - overflow);
                length -= overflow;
            }
            System.arraycopy(incoming, 0, bytes, length, incoming.length);
            length += incoming.length;
        }

        private synchronized String text() {
            String text = new String(bytes, 0, length, StandardCharsets.UTF_8);
            while (text.startsWith("\uFFFD")) {
                text = text.substring(1);
            }
            while (text.endsWith("\uFFFD")) {
                text = text.substring(0, text.length() - 1);
            }
            byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
            while (encoded.length > bytes.length && !text.isEmpty()) {
                text = text.substring(1);
                encoded = text.getBytes(StandardCharsets.UTF_8);
            }
            return text;
        }
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
