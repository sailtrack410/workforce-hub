package com.workforcehub.plugin;

import com.workforcehub.common.config.RequestIdFilter;
import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
@TestPropertySource(properties =
    "workforce.plugin.config.encryption-key-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class AdminPluginConfigIntegrationTest extends LocalIntegrationTestSupport {

    private static final String PLUGIN_A = "config-plugin-a";
    private static final String PLUGIN_B = "config-plugin-b";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RequestIdFilter requestIdFilter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc rawMockMvc;

    @BeforeEach
    void preparePlugins() {
        rawMockMvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(requestIdFilter)
            .apply(springSecurity())
            .build();
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_config WHERE plugin_id IN (?, ?)", PLUGIN_A, PLUGIN_B);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin WHERE plugin_id IN (?, ?)", PLUGIN_A, PLUGIN_B);
        insertPlugin(PLUGIN_A, "ACTIVE");
        insertPlugin(PLUGIN_B, "STOPPED");
    }

    @AfterEach
    void cleanupPlugins() {
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_config WHERE plugin_id IN (?, ?)", PLUGIN_A, PLUGIN_B);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin WHERE plugin_id IN (?, ?)", PLUGIN_A, PLUGIN_B);
        jdbcTemplate.update("""
            DELETE FROM sys_audit_log
            WHERE target_type = 'PLUGIN_CONFIG'
              AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.pluginId')) IN (?, ?)
            """, PLUGIN_A, PLUGIN_B);
        jdbcTemplate.update("DELETE FROM admin_user_role WHERE id = 6501");
        jdbcTemplate.update("DELETE FROM admin_role_permission WHERE id = 6401");
        jdbcTemplate.update("DELETE FROM admin_role WHERE id = 6301");
        jdbcTemplate.update("DELETE FROM admin_user WHERE id IN (8201, 8202)");
    }

    @Test
    void superAdminShouldCrudWithoutCrossingPluginNamespaces() throws Exception {
        Cookie session = loginAdmin("admin.demo", "Admin@123456");

        putConfig(session, PLUGIN_A, "shared.key", 0, "STRING", "value-a", null, false)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.version").value(1));
        putConfig(session, PLUGIN_B, "shared.key", 0, "STRING", "value-b", null, false)
            .andExpect(status().isOk());

        mockMvc.perform(get(configBase(PLUGIN_A)).cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].key").value("shared.key"))
            .andExpect(jsonPath("$.data[0].value").value("value-a"));
        mockMvc.perform(get(configBase(PLUGIN_B)).cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].value").value("value-b"));

        putConfig(session, PLUGIN_A, "shared.key", 1, "STRING", "value-a2", "updated", true)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.value").value("value-a2"))
            .andExpect(jsonPath("$.data.requiresRestart").value(true))
            .andExpect(jsonPath("$.data.version").value(2));

        mockMvc.perform(delete(configBase(PLUGIN_A) + "/shared.key")
                .queryParam("expectedVersion", "2")
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get(configBase(PLUGIN_A)).cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(0)));
        mockMvc.perform(get(configBase(PLUGIN_B)).cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].value").value("value-b"));

        Integer auditCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_audit_log
            WHERE target_type = 'PLUGIN_CONFIG'
              AND action_type IN (
                  'PLUGIN_CONFIG_CREATED', 'PLUGIN_CONFIG_UPDATED', 'PLUGIN_CONFIG_DELETED'
              )
              AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.pluginId')) IN (?, ?)
            """, Integer.class, PLUGIN_A, PLUGIN_B);
        assertEquals(4, auditCount);
    }

    @Test
    void secretShouldBeEncryptedMaskedPreservedAndSafelyRecreated() throws Exception {
        Cookie session = loginAdmin("admin.demo", "Admin@123456");
        String secret = "plugin-secret-value";

        mockMvc.perform(put(configBase(PLUGIN_A) + "/api.token")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "expectedVersion": 0,
                      "valueType": "SECRET",
                      "value": "%s",
                      "defaultValue": null,
                      "description": "API token",
                      "requiresRestart": false
                    }
                    """.formatted(secret)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.secret").value(true))
            .andExpect(jsonPath("$.data.hasValue").value(true))
            .andExpect(jsonPath("$.data.value").isEmpty())
            .andExpect(jsonPath("$.data.defaultValue").isEmpty())
            .andExpect(content().string(not(containsString(secret))));

        String ciphertext = jdbcTemplate.queryForObject("""
            SELECT config_value
            FROM sys_plugin_config
            WHERE plugin_id = ? AND config_key = 'api.token'
            """, String.class, PLUGIN_A);
        assertNotNull(ciphertext);
        assertTrue(ciphertext.startsWith("v1:"));
        assertNotEquals(secret, ciphertext);

        mockMvc.perform(get(configBase(PLUGIN_A)).cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].secret").value(true))
            .andExpect(jsonPath("$.data[0].value").isEmpty())
            .andExpect(content().string(not(containsString(secret))))
            .andExpect(content().string(not(containsString(ciphertext))));

        mockMvc.perform(put(configBase(PLUGIN_A) + "/api.token")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "expectedVersion": 1,
                      "valueType": "SECRET",
                      "value": null,
                      "defaultValue": null,
                      "description": "renamed",
                      "requiresRestart": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.version").value(2));

        String preserved = jdbcTemplate.queryForObject("""
            SELECT config_value
            FROM sys_plugin_config
            WHERE plugin_id = ? AND config_key = 'api.token'
            """, String.class, PLUGIN_A);
        assertEquals(ciphertext, preserved);

        mockMvc.perform(delete(configBase(PLUGIN_A) + "/api.token")
                .queryParam("expectedVersion", "2")
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(3));
        Map<String, Object> tombstone = jdbcTemplate.queryForMap("""
            SELECT config_value, value_type, description, requires_restart,
                   is_secret, is_deleted, version
            FROM sys_plugin_config
            WHERE plugin_id = ? AND config_key = 'api.token'
            """, PLUGIN_A);
        assertNull(tombstone.get("config_value"));
        assertEquals("STRING", tombstone.get("value_type"));
        assertNull(tombstone.get("description"));
        assertEquals(0, ((Number) tombstone.get("requires_restart")).intValue());
        assertEquals(0, ((Number) tombstone.get("is_secret")).intValue());
        assertEquals(1, ((Number) tombstone.get("is_deleted")).intValue());
        assertEquals(3L, ((Number) tombstone.get("version")).longValue());

        putConfig(session, PLUGIN_A, "api.token", 3, "SECRET", null, null, false)
            .andExpect(status().isBadRequest());
        String replacement = "replacement-secret-value";
        putConfig(session, PLUGIN_A, "api.token", 3, "SECRET", replacement, null, false)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.version").value(4));
        String recreatedCiphertext = jdbcTemplate.queryForObject("""
            SELECT config_value
            FROM sys_plugin_config
            WHERE plugin_id = ? AND config_key = 'api.token'
            """, String.class, PLUGIN_A);
        assertNotNull(recreatedCiphertext);
        assertTrue(recreatedCiphertext.startsWith("v1:"));
        assertNotEquals(ciphertext, recreatedCiphertext);
        assertNotEquals(replacement, recreatedCiphertext);

        List<String> auditVersions = jdbcTemplate.queryForList("""
            SELECT CONCAT(
                action_type, ':',
                JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.beforeVersion')), '>',
                JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.afterVersion'))
            )
            FROM sys_audit_log
            WHERE target_type = 'PLUGIN_CONFIG'
              AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.pluginId')) = ?
              AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.configKey')) = 'api.token'
            ORDER BY id
            """, String.class, PLUGIN_A);
        assertEquals(List.of(
            "PLUGIN_CONFIG_CREATED:0>1",
            "PLUGIN_CONFIG_UPDATED:1>2",
            "PLUGIN_CONFIG_DELETED:2>3",
            "PLUGIN_CONFIG_CREATED:3>4"
        ), auditVersions);

        Integer leakedAudit = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_audit_log
            WHERE target_type = 'PLUGIN_CONFIG'
              AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.pluginId')) = ?
              AND (detail_json LIKE ? OR detail_json LIKE ?)
            """, Integer.class, PLUGIN_A, "%" + secret + "%", "%" + replacement + "%");
        assertEquals(0, leakedAudit);
    }

    @Test
    void staleVersionShouldNotMatchRecreatedConfiguration() throws Exception {
        Cookie session = loginAdmin("admin.demo", "Admin@123456");

        mockMvc.perform(put(configBase(PLUGIN_A) + "/recreated.key")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "expectedVersion": 0,
                      "valueType": "STRING",
                      "value": "old-value",
                      "defaultValue": "old-default",
                      "description": "old description",
                      "requiresRestart": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.version").value(1));
        mockMvc.perform(delete(configBase(PLUGIN_A) + "/recreated.key")
                .queryParam("expectedVersion", "1")
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(2));
        mockMvc.perform(delete(configBase(PLUGIN_A) + "/recreated.key")
                .queryParam("expectedVersion", "1")
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(2));
        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_audit_log
            WHERE action_type = 'PLUGIN_CONFIG_DELETED'
              AND target_type = 'PLUGIN_CONFIG'
              AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.pluginId')) = ?
              AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.configKey')) = 'recreated.key'
            """, Integer.class, PLUGIN_A));

        Map<String, Object> tombstone = jdbcTemplate.queryForMap("""
            SELECT config_value, default_value, description, requires_restart,
                   is_deleted, version
            FROM sys_plugin_config
            WHERE plugin_id = ? AND config_key = 'recreated.key'
            """, PLUGIN_A);
        assertNull(tombstone.get("config_value"));
        assertNull(tombstone.get("default_value"));
        assertNull(tombstone.get("description"));
        assertEquals(0, ((Number) tombstone.get("requires_restart")).intValue());
        assertEquals(1, ((Number) tombstone.get("is_deleted")).intValue());
        assertEquals(2L, ((Number) tombstone.get("version")).longValue());

        mockMvc.perform(get(configBase(PLUGIN_A))
                .queryParam("includeDeleted", "true")
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].key").value("recreated.key"))
            .andExpect(jsonPath("$.data[0].deleted").value(true))
            .andExpect(jsonPath("$.data[0].hasValue").value(false))
            .andExpect(jsonPath("$.data[0].secret").value(false))
            .andExpect(jsonPath("$.data[0].requiresRestart").value(false))
            .andExpect(jsonPath("$.data[0].value").isEmpty())
            .andExpect(jsonPath("$.data[0].defaultValue").isEmpty())
            .andExpect(jsonPath("$.data[0].description").isEmpty())
            .andExpect(jsonPath("$.data[0].version").value(2));

        putConfig(session, PLUGIN_A, "recreated.key", 0, "STRING", "stale-create", null, false)
            .andExpect(status().isConflict());
        putConfig(session, PLUGIN_A, "recreated.key", 2, "STRING", "new-value", null, false)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.version").value(3));
        putConfig(session, PLUGIN_A, "recreated.key", 1, "STRING", "stale", null, false)
            .andExpect(status().isConflict());
        mockMvc.perform(delete(configBase(PLUGIN_A) + "/recreated.key")
                .queryParam("expectedVersion", "1")
                .cookie(session))
            .andExpect(status().isConflict());

        mockMvc.perform(get(configBase(PLUGIN_A)).cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].value").value("new-value"))
            .andExpect(jsonPath("$.data[0].version").value(3));
    }

    @Test
    void validationAndStateFailuresShouldNotPartiallyModifyConfiguration() throws Exception {
        Cookie session = loginAdmin("admin.demo", "Admin@123456");
        putConfig(session, PLUGIN_A, "feature.enabled", 0, "BOOL", "true", null, false)
            .andExpect(status().isOk());

        putConfig(session, PLUGIN_A, "feature.enabled", 1, "BOOL", "yes", null, false)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(100400));
        putConfig(session, PLUGIN_A, "feature.enabled", 1, "STRING", "changed", null, false)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(100409));
        mockMvc.perform(put(configBase(PLUGIN_A) + "/bad:key")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveBody(0, "STRING", "bad", null, false)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put(configBase(PLUGIN_A) + "/new.secret")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "expectedVersion": 0,
                      "valueType": "SECRET",
                      "value": "secret",
                      "defaultValue": "not-allowed",
                      "requiresRestart": false
                    }
                    """))
            .andExpect(status().isBadRequest());

        List<String> values = jdbcTemplate.queryForList("""
            SELECT config_value
            FROM sys_plugin_config
            WHERE plugin_id = ? AND config_key = 'feature.enabled'
            """, String.class, PLUGIN_A);
        assertEquals(List.of("true"), values);
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_config WHERE plugin_id = ?",
            Integer.class,
            PLUGIN_A
        );
        assertEquals(1, count);

        mockMvc.perform(get(configBase("missing-plugin")).cookie(session))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(100404));

        jdbcTemplate.update(
            "UPDATE sys_plugin SET status = 'UNINSTALLED' WHERE plugin_id = ?", PLUGIN_A);
        mockMvc.perform(get(configBase(PLUGIN_A)).cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].value").value("true"));
        putConfig(session, PLUGIN_A, "feature.enabled", 1, "BOOL", "false", null, false)
            .andExpect(status().isConflict());
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_config WHERE plugin_id = ?",
            Integer.class,
            PLUGIN_A
        ));
        mockMvc.perform(delete(configBase(PLUGIN_A) + "/feature.enabled")
                .queryParam("expectedVersion", "1")
                .cookie(session))
            .andExpect(status().isOk());
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_config WHERE plugin_id = ? AND is_deleted = 0",
            Integer.class,
            PLUGIN_A
        ));
    }

    @Test
    void authenticationPermissionAndCsrfBoundariesShouldBeEnforced() throws Exception {
        mockMvc.perform(get(configBase(PLUGIN_A)))
            .andExpect(status().isUnauthorized());

        Cookie employee = loginEmployee();
        mockMvc.perform(get(configBase(PLUGIN_A)).cookie(employee))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(URI.create("/api/%61dmin/plugins/" + PLUGIN_A + "/config"))
                .cookie(employee))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin;v=1/plugins/" + PLUGIN_A + "/config").cookie(employee))
            .andExpect(status().is4xxClientError());

        insertAdmin(8201L, "limited.plugin.admin", "Limited@123456");
        Cookie limited = loginAdmin("limited.plugin.admin", "Limited@123456");
        mockMvc.perform(get(configBase(PLUGIN_A)).cookie(limited))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));
        mockMvc.perform(get(URI.create("/api/admin/%70lugins/" + PLUGIN_A + "/config"))
                .cookie(limited))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));
        mockMvc.perform(get("/api/admin/plugins;v=1/" + PLUGIN_A + "/config").cookie(limited))
            .andExpect(status().is4xxClientError());

        insertAdmin(8202L, "plugin.manager", "Manager@123456");
        grantPluginManagePermission(8202L);
        Cookie authorized = loginAdmin("plugin.manager", "Manager@123456");
        mockMvc.perform(get(configBase(PLUGIN_A)).cookie(authorized))
            .andExpect(status().isOk());
        putConfig(authorized, PLUGIN_A, "authorized.key", 0, "STRING", "value", null, false)
            .andExpect(status().isOk());
        mockMvc.perform(delete(configBase(PLUGIN_A) + "/authorized.key")
                .queryParam("expectedVersion", "1")
                .cookie(authorized))
            .andExpect(status().isOk());

        rawMockMvc.perform(put(configBase(PLUGIN_A) + "/csrf.key")
                .cookie(authorized)
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveBody(0, "STRING", "value", null, false)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));
        assertEquals(0, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_plugin_config
            WHERE plugin_id = ? AND config_key = 'csrf.key'
            """, Integer.class, PLUGIN_A));

        putConfig(authorized, PLUGIN_A, "csrf.delete", 0, "STRING", "value", null, false)
            .andExpect(status().isOk());
        rawMockMvc.perform(delete(configBase(PLUGIN_A) + "/csrf.delete")
                .queryParam("expectedVersion", "1")
                .cookie(authorized))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));
        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_plugin_config
            WHERE plugin_id = ? AND config_key = 'csrf.delete'
            """, Integer.class, PLUGIN_A));
    }

    @Test
    void concurrentUpdatesWithSameVersionShouldHaveSingleWinner() throws Exception {
        Cookie session = loginAdmin("admin.demo", "Admin@123456");
        putConfig(session, PLUGIN_A, "race.key", 0, "STRING", "initial", null, false)
            .andExpect(status().isOk());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(
                () -> concurrentUpdate(session, ready, start, "first"));
            Future<Integer> second = executor.submit(
                () -> concurrentUpdate(session, ready, start, "second"));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "并发请求未按时就绪");
            start.countDown();

            List<Integer> statuses = List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
            );
            assertEquals(1, statuses.stream().filter(code -> code == 200).count());
            assertEquals(1, statuses.stream().filter(code -> code == 409).count());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "并发线程池未按时退出");
        }

        Long version = jdbcTemplate.queryForObject("""
            SELECT version FROM sys_plugin_config
            WHERE plugin_id = ? AND config_key = 'race.key'
            """, Long.class, PLUGIN_A);
        assertEquals(2L, version);
        Integer updateAudits = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_audit_log
            WHERE action_type = 'PLUGIN_CONFIG_UPDATED'
              AND target_type = 'PLUGIN_CONFIG'
              AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.pluginId')) = ?
              AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.configKey')) = 'race.key'
            """, Integer.class, PLUGIN_A);
        assertEquals(1, updateAudits);
    }

    @Test
    void sizeKeyAndQuotaLimitsShouldFailBeforeWriting() throws Exception {
        Cookie session = loginAdmin("admin.demo", "Admin@123456");
        for (int index = 0; index < 128; index++) {
            jdbcTemplate.update("""
                INSERT INTO sys_plugin_config
                    (plugin_id, config_key, config_value, value_type)
                VALUES (?, ?, 'value', 'STRING')
                """, PLUGIN_A, "quota." + index);
        }

        putConfig(session, PLUGIN_A, "quota.extra", 0, "STRING", "value", null, false)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value(100422));
        mockMvc.perform(delete(configBase(PLUGIN_A) + "/quota.0")
                .queryParam("expectedVersion", "1")
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(2));
        putConfig(session, PLUGIN_A, "quota.extra", 0, "STRING", "value", null, false)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value(100422));
        putConfig(session, PLUGIN_A, "quota.0", 2, "STRING", "restored", null, false)
            .andExpect(status().isOk());

        String oversized = "测".repeat(5462);
        putConfig(session, PLUGIN_B, "large.value", 0, "STRING", oversized, null, false)
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value(100413));

        String invalidKey = "a".repeat(129);
        putConfig(session, PLUGIN_B, invalidKey, 0, "STRING", "value", null, false)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(100400));

        String oversizedRequest = "x".repeat(300 * 1024);
        putConfig(session, PLUGIN_B, "large.request", 0, "STRING", oversizedRequest, null, false)
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value(100413))
            .andExpect(jsonPath("$.message").value("插件配置请求体不能超过 256 KiB"));
        mockMvc.perform(put(URI.create(
                "/api/admin/plugins/" + PLUGIN_B + "/%63onfig/encoded.large"))
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveBody(0, "STRING", oversizedRequest, null, false)))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value(100413))
            .andExpect(jsonPath("$.message").value("插件配置请求体不能超过 256 KiB"));

        mockMvc.perform(put(configBase(PLUGIN_B) + "/default.only")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "expectedVersion": 0,
                      "valueType": "BOOL",
                      "value": null,
                      "defaultValue": "true",
                      "description": null,
                      "requiresRestart": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.value").isEmpty())
            .andExpect(jsonPath("$.data.defaultValue").value("true"));

        assertEquals(128, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_config WHERE plugin_id = ?",
            Integer.class,
            PLUGIN_A
        ));
        assertEquals(128, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_config WHERE plugin_id = ? AND is_deleted = 0",
            Integer.class,
            PLUGIN_A
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_config WHERE plugin_id = ?",
            Integer.class,
            PLUGIN_B
        ));
    }

    private org.springframework.test.web.servlet.ResultActions putConfig(
            Cookie session,
            String pluginId,
            String key,
            long expectedVersion,
            String valueType,
            String value,
            String description,
            boolean requiresRestart) throws Exception {
        return mockMvc.perform(put(configBase(pluginId) + "/" + key)
            .cookie(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content(saveBody(expectedVersion, valueType, value, description, requiresRestart)));
    }

    private int concurrentUpdate(
            Cookie session,
            CountDownLatch ready,
            CountDownLatch start,
            String value) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("并发请求未按时开始");
        }
        return mockMvc.perform(put(configBase(PLUGIN_A) + "/race.key")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveBody(1, "STRING", value, null, false)))
            .andReturn()
            .getResponse()
            .getStatus();
    }

    private String saveBody(
            long expectedVersion,
            String valueType,
            String value,
            String description,
            boolean requiresRestart) {
        String jsonValue = value == null ? "null" : "\"" + value + "\"";
        String jsonDescription = description == null ? "null" : "\"" + description + "\"";
        return """
            {
              "expectedVersion": %d,
              "valueType": "%s",
              "value": %s,
              "defaultValue": null,
              "description": %s,
              "requiresRestart": %s
            }
            """.formatted(
                expectedVersion,
                valueType,
                jsonValue,
                jsonDescription,
                requiresRestart
            );
    }

    private String configBase(String pluginId) {
        return "/api/admin/plugins/" + pluginId + "/config";
    }

    private void insertPlugin(String pluginId, String status) {
        jdbcTemplate.update("""
            INSERT INTO sys_plugin
                (plugin_id, name, version, status, jar_path, installed_at, updated_at)
            VALUES (?, ?, '1.0.0', ?, ?, NOW(), NOW())
            """, pluginId, pluginId, status, "/tmp/" + pluginId + ".jar");
    }

    private void insertAdmin(long adminId, String username, String password) {
        jdbcTemplate.update("""
            INSERT INTO admin_user
                (id, username, password_hash, display_name, email, status, is_deleted)
            VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0)
            """,
            adminId,
            username,
            passwordEncoder.encode(password),
            username,
            username + "@example.com"
        );
    }

    private void grantPluginManagePermission(long adminId) {
        Long permissionId = jdbcTemplate.queryForObject("""
            SELECT id FROM admin_permission
            WHERE perm_code = 'ADMIN_PLUGIN_MANAGE'
            """, Long.class);
        jdbcTemplate.update("""
            INSERT INTO admin_role
                (id, role_code, role_name, status, created_by, updated_by, is_deleted)
            VALUES (6301, 'PLUGIN_MANAGER_TEST', '插件管理测试角色', 'ACTIVE', NULL, NULL, 0)
            """);
        jdbcTemplate.update("""
            INSERT INTO admin_role_permission (id, role_id, permission_id, created_by)
            VALUES (6401, 6301, ?, NULL)
            """, permissionId);
        jdbcTemplate.update("""
            INSERT INTO admin_user_role (id, admin_user_id, role_id, created_by)
            VALUES (6501, ?, 6301, NULL)
            """, adminId);
    }

    private Cookie loginAdmin(String username, String password) throws Exception {
        return login("/api/admin/auth/login", username, password);
    }

    private Cookie loginEmployee() throws Exception {
        return login("/api/employee/auth/login", "zhangsan.employee", "Employee@123456");
    }

    private Cookie login(String path, String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "%s"
                    }
                    """.formatted(username, password)))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session);
        return session;
    }
}
