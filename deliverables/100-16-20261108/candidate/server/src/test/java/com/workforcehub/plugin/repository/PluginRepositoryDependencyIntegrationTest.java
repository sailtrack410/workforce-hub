package com.workforcehub.plugin.repository;

import com.workforcehub.support.LocalIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("api")
class PluginRepositoryDependencyIntegrationTest extends LocalIntegrationTestSupport {

    private static final String PREFIX = "repo-dep-";
    private static final String SIGNER_ID = "repo-dependency-signer";

    @Autowired
    private PluginRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanupPluginRows() {
        jdbc.update("DELETE FROM sys_plugin_operation_idempotency WHERE plugin_id LIKE ?", PREFIX + "%");
        jdbc.update("DELETE FROM sys_plugin_event_subscription WHERE plugin_id LIKE ?", PREFIX + "%");
        jdbc.update("DELETE FROM sys_plugin_action_idempotency WHERE plugin_id LIKE ?", PREFIX + "%");
        jdbc.update("DELETE FROM sys_plugin_action_permission WHERE plugin_id LIKE ?", PREFIX + "%");
        jdbc.update("DELETE FROM sys_plugin_visibility WHERE plugin_id LIKE ?", PREFIX + "%");
        jdbc.update("DELETE FROM sys_plugin_capability WHERE plugin_id LIKE ?", PREFIX + "%");
        jdbc.update("DELETE FROM sys_plugin_config WHERE plugin_id LIKE ?", PREFIX + "%");
        jdbc.update(
            "DELETE FROM sys_plugin_dependency WHERE plugin_id LIKE ? OR depends_on LIKE ?",
            PREFIX + "%",
            PREFIX + "%"
        );
        jdbc.update("DELETE FROM sys_plugin WHERE plugin_id LIKE ?", PREFIX + "%");
    }

    @Test
    void pluginRowShouldDefensivelyCopyDependenciesAndRejectNull() {
        PluginRow row = plugin(PREFIX + "copy", PluginStatus.INSTALLED, "0.1.0");
        List<PluginDependencySpec> source = new ArrayList<>(List.of(
            dependency(PREFIX + "target", "1.0.0", "2.0.0")
        ));

        row.setDependencies(source);
        source.clear();

        assertEquals(1, row.getDependencies().size());
        assertThrows(UnsupportedOperationException.class, () -> row.getDependencies().clear());
        assertThrows(NullPointerException.class, () -> row.setDependencies(null));
    }

    @Test
    void insertShouldPersistAndReadDependenciesSortedByTarget() {
        PluginRow row = plugin(PREFIX + "insert", PluginStatus.INSTALLED, "0.1.0");
        row.setDependencies(List.of(
            dependency(PREFIX + "target-z", "2.0.0", null),
            dependency(PREFIX + "target-a", null, "3.0.0")
        ));

        repository.insert(row);

        PluginRow stored = repository.findById(row.getPluginId()).orElseThrow();
        assertDependencyIds(stored, PREFIX + "target-a", PREFIX + "target-z");
        assertEquals("3.0.0", stored.getDependencies().get(0).maxVersion());
        assertEquals("2.0.0", stored.getDependencies().get(1).minVersion());
        assertEquals(1, count("sys_plugin", row.getPluginId()));
        assertEquals(2, count("sys_plugin_dependency", row.getPluginId()));
    }

    @Test
    void insertShouldRollbackPluginWhenDependencyBatchFails() {
        PluginRow row = plugin(PREFIX + "insert-rollback", PluginStatus.INSTALLED, "0.1.0");
        PluginDependencySpec duplicate = dependency(PREFIX + "duplicate", null, null);
        row.setDependencies(List.of(duplicate, duplicate));

        assertThrows(DataAccessException.class, () -> repository.insert(row));

        assertEquals(0, count("sys_plugin", row.getPluginId()));
        assertEquals(0, count("sys_plugin_dependency", row.getPluginId()));
    }

    @Test
    void findAllAndFindByStatusShouldHydrateEveryPluginDependencyList() {
        PluginRow active = plugin(PREFIX + "active", PluginStatus.ACTIVE, "0.1.0");
        active.setDependencies(List.of(
            dependency(PREFIX + "target-b", null, null),
            dependency(PREFIX + "target-a", "1.0.0", null)
        ));
        PluginRow stopped = plugin(PREFIX + "stopped", PluginStatus.STOPPED, "0.1.0");
        stopped.setDependencies(List.of(dependency(PREFIX + "target-c", null, "2.0.0")));
        PluginRow empty = plugin(PREFIX + "empty", PluginStatus.ACTIVE, "0.1.0");
        repository.insert(active);
        repository.insert(stopped);
        repository.insert(empty);

        Map<String, PluginRow> all = repository.findAll().stream()
            .filter(row -> row.getPluginId().startsWith(PREFIX))
            .collect(Collectors.toMap(PluginRow::getPluginId, row -> row));
        assertDependencyIds(all.get(active.getPluginId()), PREFIX + "target-a", PREFIX + "target-b");
        assertDependencyIds(all.get(stopped.getPluginId()), PREFIX + "target-c");
        assertTrue(all.get(empty.getPluginId()).getDependencies().isEmpty());

        Map<String, PluginRow> activeRows = repository.findByStatus(PluginStatus.ACTIVE).stream()
            .filter(row -> row.getPluginId().startsWith(PREFIX))
            .collect(Collectors.toMap(PluginRow::getPluginId, row -> row));
        assertEquals(2, activeRows.size());
        assertDependencyIds(activeRows.get(active.getPluginId()), PREFIX + "target-a", PREFIX + "target-b");
        assertTrue(activeRows.get(empty.getPluginId()).getDependencies().isEmpty());
    }

    @Test
    void replaceShouldSwapOutgoingDependenciesPreserveIncomingAndClearOldLedgers() {
        String ownerId = PREFIX + "replace";
        PluginRow previous = plugin(ownerId, PluginStatus.UNINSTALLED, "0.1.0");
        previous.setDependencies(List.of(dependency(PREFIX + "old-target", "0.1.0", null)));
        repository.insert(previous);

        PluginRow dependent = plugin(PREFIX + "incoming", PluginStatus.ACTIVE, "0.1.0");
        dependent.setDependencies(List.of(dependency(ownerId, "0.1.0", null)));
        repository.insert(dependent);
        insertOldLedgers(ownerId);

        PluginRow replacement = plugin(ownerId, PluginStatus.INSTALLED, "0.2.0");
        replacement.setJarPath(PREFIX + "replacement.jar");
        replacement.setDependencies(List.of(
            dependency(PREFIX + "new-z", "2.0.0", null),
            dependency(PREFIX + "new-a", null, "3.0.0")
        ));

        assertTrue(repository.replaceUninstalled(
            replacement,
            previous.getJarPath(),
            previous.getSignerId()
        ));

        PluginRow stored = repository.findById(ownerId).orElseThrow();
        assertEquals("0.2.0", stored.getVersion());
        assertEquals(PluginStatus.INSTALLED, stored.getStatus());
        assertDependencyIds(stored, PREFIX + "new-a", PREFIX + "new-z");
        assertEquals(List.of(dependent.getPluginId()), repository.findDependentPluginIds(ownerId));
        assertEquals(0, ledgerCount("sys_plugin_operation_idempotency", ownerId));
        assertEquals(0, ledgerCount("sys_plugin_event_subscription", ownerId));
    }

    @Test
    void replaceShouldRollbackRecordDependenciesAndLedgersWhenDependencyInsertFails() {
        String ownerId = PREFIX + "replace-rollback";
        PluginRow previous = plugin(ownerId, PluginStatus.UNINSTALLED, "0.1.0");
        previous.setDependencies(List.of(dependency(PREFIX + "old-target", "0.1.0", null)));
        repository.insert(previous);
        insertOldLedgers(ownerId);

        PluginRow replacement = plugin(ownerId, PluginStatus.INSTALLED, "0.2.0");
        replacement.setJarPath(PREFIX + "replacement-failed.jar");
        PluginDependencySpec duplicate = dependency(PREFIX + "duplicate-target", null, null);
        replacement.setDependencies(List.of(duplicate, duplicate));

        assertThrows(DataAccessException.class, () -> repository.replaceUninstalled(
            replacement,
            previous.getJarPath(),
            previous.getSignerId()
        ));

        PluginRow stored = repository.findById(ownerId).orElseThrow();
        assertEquals("0.1.0", stored.getVersion());
        assertEquals(PluginStatus.UNINSTALLED, stored.getStatus());
        assertEquals(previous.getJarPath(), stored.getJarPath());
        assertDependencyIds(stored, PREFIX + "old-target");
        assertEquals(1, ledgerCount("sys_plugin_operation_idempotency", ownerId));
        assertEquals(1, ledgerCount("sys_plugin_event_subscription", ownerId));
    }

    @Test
    void findDependentPluginIdsShouldSortAndIgnoreUninstalledOwners() {
        String targetId = PREFIX + "reverse-target";
        repository.insert(plugin(targetId, PluginStatus.INSTALLED, "0.1.0"));
        insertDependent(PREFIX + "z-active", PluginStatus.ACTIVE, targetId);
        insertDependent(PREFIX + "a-stopped", PluginStatus.STOPPED, targetId);
        insertDependent(PREFIX + "m-uninstalled", PluginStatus.UNINSTALLED, targetId);

        assertEquals(
            List.of(PREFIX + "a-stopped", PREFIX + "z-active"),
            repository.findDependentPluginIds(targetId)
        );
    }

    @Test
    void reverseDependencyLookupIndexShouldExist() {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sys_plugin_dependency'
              AND INDEX_NAME = 'idx_plugin_dependency_depends_on'
            """, Integer.class);

        assertEquals(1, count);
    }

    private void insertDependent(String pluginId, PluginStatus status, String targetId) {
        PluginRow dependent = plugin(pluginId, status, "0.1.0");
        dependent.setDependencies(List.of(dependency(targetId, null, null)));
        repository.insert(dependent);
    }

    private void insertOldLedgers(String pluginId) {
        jdbc.update("""
            INSERT INTO sys_plugin_operation_idempotency
                (plugin_id, operation_code, idempotency_key, request_hash)
            VALUES (?, 'event.subscribe', 'old-operation', ?)
            """, pluginId, "a".repeat(64));
        jdbc.update("""
            INSERT INTO sys_plugin_event_subscription
                (plugin_id, event_type, action_code)
            VALUES (?, 'employee.updated', 'old.action')
            """, pluginId);
    }

    private PluginRow plugin(String pluginId, PluginStatus status, String version) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        PluginRow row = new PluginRow();
        row.setPluginId(pluginId);
        row.setName(pluginId);
        row.setVersion(version);
        row.setSdkVersion("0.1.0");
        row.setJavaVersion("17");
        row.setType("FUNCTIONAL");
        row.setStatus(status);
        row.setJarPath(PREFIX + pluginId + ".jar");
        row.setJarSha256("a".repeat(64));
        row.setSignature(new byte[] {1, 2, 3});
        row.setSignerId(SIGNER_ID);
        row.setProvider("Workforce Hub");
        row.setInstalledAt(now);
        row.setUpdatedAt(now);
        return row;
    }

    private PluginDependencySpec dependency(
            String pluginId,
            String minVersion,
            String maxVersion) {
        return new PluginDependencySpec(pluginId, minVersion, maxVersion);
    }

    private void assertDependencyIds(PluginRow row, String... expectedIds) {
        assertEquals(
            List.of(expectedIds),
            row.getDependencies().stream().map(PluginDependencySpec::pluginId).toList()
        );
    }

    private int count(String tableName, String pluginId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM " + tableName + " WHERE plugin_id = ?",
            Integer.class,
            pluginId
        );
        return count == null ? 0 : count;
    }

    private int ledgerCount(String tableName, String pluginId) {
        return count(tableName, pluginId);
    }
}
