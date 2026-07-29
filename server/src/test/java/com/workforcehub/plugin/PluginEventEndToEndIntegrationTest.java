package com.workforcehub.plugin;

import com.workforcehub.auth.security.AdminUserDetails;
import com.workforcehub.org.dto.OrgUserCreateRequest;
import com.workforcehub.org.service.OrgManagementService;
import com.workforcehub.plugin.repository.PluginRow;
import com.workforcehub.plugin.runtime.PluginLifecycleGuard;
import com.workforcehub.plugin.runtime.PluginManagerService;
import com.workforcehub.plugin.runtime.WorkforcePluginManager;
import com.workforcehub.plugin.sdk.ErrorCodes;
import com.workforcehub.plugin.sdk.EventTypes;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import com.workforcehub.plugin.sdk.ServiceResult;
import com.workforcehub.plugin.sdk.TestInvocationContextBinder;
import com.workforcehub.plugin.sdk.service.EventService;
import com.workforcehub.support.LocalIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.PluginState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
import java.security.Signature;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "workforce.plugin.root=target/test-plugins-event-e2e")
@ActiveProfiles("api")
class PluginEventEndToEndIntegrationTest extends LocalIntegrationTestSupport {

    private static final String PLUGIN_ID = "event-probe-plugin";
    private static final String ACTION_CODE = "event.probe.capture";
    private static final String EVENT_CAPABILITY = "EVENT_SUBSCRIBE_" + EventTypes.EMPLOYEE_HIRED;
    private static final String TEST_SIGNER_ID = "event-probe-test";
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

    @Autowired
    private WorkforcePluginManager pluginManager;

    @Autowired
    private PluginLifecycleGuard lifecycleGuard;

    @Autowired
    private EventService eventService;

    @Autowired
    private OrgManagementService orgManagementService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        cleanupPlugin();
        authenticateAdmin();
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        cleanupPlugin();
    }

    @Test
    void employeeHireShouldReachRealPf4jSubscriber() throws Exception {
        Path jarPath = buildPluginJar();
        PluginRow metadata = new PluginRow();
        metadata.setName("Event Probe Plugin");
        metadata.setSignerId(TEST_SIGNER_ID);
        metadata.setSignature(sign(jarPath));

        pluginManagerService.install(jarPath, metadata);
        pluginManagerService.enable(PLUGIN_ID);
        grantEventCapabilities();

        ServiceResult<Void> subscribed = eventService.subscribe(
            new TestInvocationContextBinder().forPluginStartup(PLUGIN_ID),
            EventTypes.EMPLOYEE_HIRED,
            ACTION_CODE,
            "event-probe-subscribe"
        );
        assertEquals(ErrorCodes.SUCCESS, subscribed.getCode());

        OrgUserCreateRequest request = new OrgUserCreateRequest();
        request.setEmployeeNo("PF4J-E2E-EMPLOYEE");
        request.setRealName("Sensitive Employee Name");
        request.setPrimaryDeptId(111L);
        request.setEmploymentStatus("ACTIVE");
        request.setHireDate(LocalDate.of(2026, 8, 8));
        request.setStatus("ACTIVE");
        Long userId = orgManagementService.createUser(request);

        PluginActionHandler handler = awaitInvocation();
        assertEquals(1, invocationCount(handler));

        Map<?, ?> event = assertInstanceOf(
            Map.class,
            handler.getClass().getMethod("getLastEvent").invoke(handler)
        );
        String eventId = "employee-hired-" + userId;
        assertEquals(eventId, event.get("eventId"));
        assertEquals(EventTypes.EMPLOYEE_HIRED, event.get("eventType"));
        assertTrue(assertInstanceOf(Long.class, event.get("occurredAt")) > 0);
        assertEquals(1L, event.get("version"));
        assertEquals(eventId, event.get("idempotencyKey"));
        assertEquals("1001", event.get("triggeredBy"));
        assertEquals(
            Set.of("eventId", "eventType", "occurredAt", "version", "idempotencyKey", "triggeredBy", "payload"),
            event.keySet()
        );

        Map<?, ?> payload = assertInstanceOf(Map.class, event.get("payload"));
        assertEquals(
            Set.of("userId", "employeeNo", "primaryDeptId", "positionId", "managerUserId", "employmentStatus", "hireDate"),
            payload.keySet()
        );
        assertEquals(userId, payload.get("userId"));
        assertEquals("PF4J-E2E-EMPLOYEE", payload.get("employeeNo"));
        assertEquals(111L, payload.get("primaryDeptId"));
        assertNull(payload.get("positionId"));
        assertNull(payload.get("managerUserId"));
        assertEquals("ACTIVE", payload.get("employmentStatus"));
        assertEquals("2026-08-08", payload.get("hireDate"));

        Map<?, ?> context = assertInstanceOf(
            Map.class,
            handler.getClass().getMethod("getLastContext").invoke(handler)
        );
        assertEquals(Map.of(
            "pluginId", PLUGIN_ID,
            "userId", 1001L,
            "callerType", "PLUGIN",
            "requestId", eventId,
            "source", "EVENT_CALLBACK"
        ), context);
    }

    private void grantEventCapabilities() {
        jdbc.update(
            "INSERT INTO sys_plugin_capability (plugin_id, capability_code, description) VALUES (?, ?, ?)",
            PLUGIN_ID,
            "EVENT_SUBSCRIBE",
            "event end-to-end test"
        );
        jdbc.update(
            "INSERT INTO sys_plugin_capability (plugin_id, capability_code, description) VALUES (?, ?, ?)",
            PLUGIN_ID,
            EVENT_CAPABILITY,
            "event end-to-end test"
        );
    }

    private PluginActionHandler awaitInvocation() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        PluginActionHandler handler;
        do {
            handler = pluginManagerService.findActionHandler(PLUGIN_ID, ACTION_CODE).orElseThrow();
            if (invocationCount(handler) > 0 && lifecycleGuard.getActiveCount(PLUGIN_ID) == 0) {
                return handler;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        assertEquals(1, invocationCount(handler), "插件事件回调未在 10 秒内完成");
        assertEquals(0, lifecycleGuard.getActiveCount(PLUGIN_ID), "插件事件回调未在 10 秒内释放生命周期计数");
        return handler;
    }

    private int invocationCount(PluginActionHandler handler) throws Exception {
        return (Integer) handler.getClass().getMethod("getInvocationCount").invoke(handler);
    }

    private Path buildPluginJar() throws IOException {
        Path sourceRoot = Path.of("src/test/plugin-fixtures/event-probe")
            .toAbsolutePath()
            .normalize();
        Path packageRoot = sourceRoot.resolve("com/workforcehub/plugin/eventprobe");
        Path classes = tempDir.resolve("event-probe-classes");
        Files.createDirectories(classes);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "需要 JDK");
        int result = compiler.run(
            null,
            null,
            null,
            "-classpath",
            System.getProperty("java.class.path"),
            "-d",
            classes.toString(),
            packageRoot.resolve("EventProbePlugin.java").toString(),
            packageRoot.resolve("EventProbeHandler.java").toString()
        );
        assertEquals(0, result, "测试插件编译失败");

        Files.writeString(classes.resolve("plugin.properties"), """
            plugin.id=event-probe-plugin
            plugin.class=com.workforcehub.plugin.eventprobe.EventProbePlugin
            plugin.version=0.1.0
            plugin.provider=Workforce Hub
            plugin.description=Plugin event end-to-end integration probe
            """);
        Files.writeString(classes.resolve("plugin.json"), """
            {
              "id":"event-probe-plugin",
              "version":"0.1.0",
              "sdkVersion":"0.1.0",
              "minHostVersion":"0.1.0",
              "maxHostVersion":"0.2.0",
              "javaVersion":"17",
              "type":"FUNCTIONAL",
              "provider":"Workforce Hub"
            }
            """);

        Path jarPath = tempDir.resolve("event-probe-plugin.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath));
                var files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).sorted(Comparator.naturalOrder()).toList()) {
                String entryName = classes.relativize(file).toString().replace('\\', '/');
                output.putNextEntry(new JarEntry(entryName));
                Files.copy(file, output);
                output.closeEntry();
            }
        }
        return jarPath;
    }

    private byte[] sign(Path jarPath) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(SIGNING_KEY.getPrivate());
        signer.update(Files.readAllBytes(jarPath));
        return signer.sign();
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

    private void authenticateAdmin() {
        AdminUserDetails principal = new AdminUserDetails(
            1001L,
            "plugin-event-e2e",
            "test-password-placeholder",
            "Plugin Event E2E",
            "ACTIVE",
            List.of("SUPER_ADMIN"),
            List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private void cleanupPlugin() throws Exception {
        List<String> snapshotPaths = jdbc.query(
            "SELECT jar_path FROM sys_plugin WHERE plugin_id = ?",
            (rs, rowNum) -> rs.getString("jar_path"),
            PLUGIN_ID
        );

        var wrapper = pluginManager.getPlugin(PLUGIN_ID);
        if (wrapper != null) {
            if (!snapshotPaths.isEmpty() && wrapper.getPluginState() == PluginState.STARTED) {
                pluginManagerService.uninstall(PLUGIN_ID);
            } else {
                if (wrapper.getPluginState() == PluginState.STARTED
                        || wrapper.getPluginState() == PluginState.FAILED) {
                    assertEquals(PluginState.STOPPED, pluginManager.stopPlugin(PLUGIN_ID));
                }
                assertTrue(pluginManager.unloadPlugin(PLUGIN_ID));
            }
        }
        lifecycleGuard.remove(PLUGIN_ID);

        jdbc.update("DELETE FROM sys_plugin_event_subscription WHERE plugin_id = ?", PLUGIN_ID);
        jdbc.update("DELETE FROM sys_plugin_operation_idempotency WHERE plugin_id = ?", PLUGIN_ID);
        jdbc.update("DELETE FROM sys_plugin_action_permission WHERE plugin_id = ?", PLUGIN_ID);
        jdbc.update("DELETE FROM sys_plugin_capability WHERE plugin_id = ?", PLUGIN_ID);
        jdbc.update("DELETE FROM sys_plugin_visibility WHERE plugin_id = ?", PLUGIN_ID);
        jdbc.update("DELETE FROM sys_plugin_config WHERE plugin_id = ?", PLUGIN_ID);
        jdbc.update("DELETE FROM sys_plugin_dependency WHERE plugin_id = ? OR depends_on = ?", PLUGIN_ID, PLUGIN_ID);
        jdbc.update("DELETE FROM sys_plugin_upgrade_history WHERE plugin_id = ?", PLUGIN_ID);
        jdbc.update("DELETE FROM sys_plugin WHERE plugin_id = ?", PLUGIN_ID);

        for (String snapshotPath : snapshotPaths) {
            Files.deleteIfExists(Path.of(snapshotPath));
        }
        Path snapshotDirectory = pluginManager.getPluginsRoot()
            .toAbsolutePath()
            .normalize()
            .resolve(".snapshots");
        if (Files.isDirectory(snapshotDirectory)) {
            try (var snapshots = Files.list(snapshotDirectory)) {
                for (Path snapshot : snapshots.filter(Files::isRegularFile).toList()) {
                    Files.deleteIfExists(snapshot);
                }
            }
        }
    }
}
