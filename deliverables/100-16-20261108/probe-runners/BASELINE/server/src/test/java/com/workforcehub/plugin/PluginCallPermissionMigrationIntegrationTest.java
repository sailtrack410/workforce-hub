package com.workforcehub.plugin;

import com.workforcehub.support.LocalIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V56 插件调用权限修复迁移集成测试。
 */
@SpringBootTest
@ActiveProfiles("api")
class PluginCallPermissionMigrationIntegrationTest extends LocalIntegrationTestSupport {

    private static final long OCCUPIED_RELATION_ID = 6111L;
    private static final long OCCUPIED_PERMISSION_ID = 7011L;
    private static final long TEMP_ROLE_ID = 6002L;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void arrangeV53State() {
        jdbcTemplate.update("DELETE FROM admin_role_permission WHERE id = ?", OCCUPIED_RELATION_ID);
        jdbcTemplate.update("DELETE FROM admin_permission WHERE id = ?", OCCUPIED_PERMISSION_ID);
        jdbcTemplate.update("""
            UPDATE admin_permission
            SET perm_name = '插件调用权限',
                module_code = 'PLUGIN',
                action_code = 'CALL',
                status = 'ACTIVE',
                is_deleted = 0
            WHERE id = 7010
            """);
        jdbcTemplate.update("""
            INSERT INTO admin_role
                (id, role_code, role_name, status, created_by, updated_by, is_deleted)
            VALUES (?, 'MIGRATION_TEST_ROLE', '迁移测试角色', 'ACTIVE', NULL, NULL, 0)
            """, TEMP_ROLE_ID);
    }

    @AfterEach
    void cleanupV56State() {
        jdbcTemplate.update("DELETE FROM admin_role_permission WHERE role_id = ?", TEMP_ROLE_ID);
        jdbcTemplate.update("""
            DELETE rp
            FROM admin_role_permission rp
            JOIN admin_permission p ON p.id = rp.permission_id
            WHERE p.perm_code IN ('ADMIN_PLUGIN_CALL', 'ADMIN_MIGRATION_UNRELATED')
            """);
        jdbcTemplate.update("DELETE FROM admin_role WHERE id = ?", TEMP_ROLE_ID);
        jdbcTemplate.update("""
            DELETE FROM admin_permission
            WHERE perm_code IN ('ADMIN_PLUGIN_CALL', 'ADMIN_MIGRATION_UNRELATED')
            """);
        jdbcTemplate.update("""
            UPDATE admin_permission
            SET perm_code = 'ADMIN_NOTIFICATION_CHANNEL_MANAGE',
                perm_name = '通知渠道配置',
                module_code = 'NOTIFICATION',
                action_code = 'MANAGE',
                status = 'ACTIVE',
                updated_by = NULL,
                is_deleted = 0
            WHERE id = 7010
            """);
        jdbcTemplate.update("""
            INSERT INTO admin_permission (
                id, perm_code, perm_name, module_code, action_code,
                status, created_by, updated_by, is_deleted
            )
            SELECT 7011, 'ADMIN_PLUGIN_CALL', '插件调用权限', 'PLUGIN', 'CALL',
                   'ACTIVE', NULL, NULL, 0
            WHERE NOT EXISTS (
                SELECT 1 FROM admin_permission WHERE id = 7011
            )
              AND NOT EXISTS (
                SELECT 1 FROM admin_permission WHERE perm_code = 'ADMIN_PLUGIN_CALL'
            )
            """);
        jdbcTemplate.update("""
            INSERT INTO admin_role_permission (id, role_id, permission_id, created_by)
            SELECT seq.next_id, r.id, p.id, NULL
            FROM admin_role r
            JOIN admin_permission p ON p.perm_code = 'ADMIN_PLUGIN_CALL'
            CROSS JOIN (
                SELECT COALESCE(MAX(id), 6100) + 1 AS next_id
                FROM admin_role_permission
            ) seq
            WHERE r.role_code = 'SUPER_ADMIN'
              AND r.is_deleted = 0
              AND NOT EXISTS (
                  SELECT 1
                  FROM admin_role_permission existing
                  WHERE existing.role_id = r.id
                    AND existing.permission_id = p.id
              )
            """);
    }

    @Test
    void shouldPreserveOccupiedRelationIdAndAllocateNewPluginPermissionRelationId() {
        insertOccupiedRelation(7001L);

        executeV56();

        Map<String, Object> notification = jdbcTemplate.queryForMap(
            "SELECT id, perm_code FROM admin_permission WHERE id = 7010");
        assertEquals("ADMIN_NOTIFICATION_CHANNEL_MANAGE", notification.get("perm_code"));

        Map<String, Object> pluginPermission = jdbcTemplate.queryForMap(
            "SELECT id, perm_code FROM admin_permission WHERE perm_code = 'ADMIN_PLUGIN_CALL'");
        assertEquals("ADMIN_PLUGIN_CALL", pluginPermission.get("perm_code"));
        assertTrue(((Number) pluginPermission.get("id")).longValue() > 7010L);

        Map<String, Object> oldRelation = jdbcTemplate.queryForMap("""
            SELECT role_id, permission_id
            FROM admin_role_permission
            WHERE id = ?
            """, OCCUPIED_RELATION_ID);
        assertEquals(TEMP_ROLE_ID, ((Number) oldRelation.get("role_id")).longValue());
        assertEquals(7001L, ((Number) oldRelation.get("permission_id")).longValue());

        List<Map<String, Object>> pluginRelations = jdbcTemplate.queryForList("""
            SELECT rp.id, rp.role_id, rp.permission_id
            FROM admin_role_permission rp
            JOIN admin_role r ON r.id = rp.role_id
            JOIN admin_permission p ON p.id = rp.permission_id
            WHERE r.role_code = 'SUPER_ADMIN'
              AND p.perm_code = 'ADMIN_PLUGIN_CALL'
            """);
        assertEquals(1, pluginRelations.size());
        long pluginRelationId = ((Number) pluginRelations.get(0).get("id")).longValue();
        assertNotEquals(OCCUPIED_RELATION_ID, pluginRelationId);
        assertTrue(pluginRelationId > OCCUPIED_RELATION_ID);
    }

    @Test
    void shouldPreserveOccupiedPermissionAndItsRelationWhenAllocatingPluginPermissionId() {
        jdbcTemplate.update("""
            INSERT INTO admin_permission (
                id, perm_code, perm_name, module_code, action_code,
                status, created_by, updated_by, is_deleted
            ) VALUES (?, 'ADMIN_MIGRATION_UNRELATED', '迁移无关权限', 'MIGRATION', 'TEST',
                      'ACTIVE', NULL, NULL, 0)
            """, OCCUPIED_PERMISSION_ID);
        insertOccupiedRelation(OCCUPIED_PERMISSION_ID);

        executeV56();

        Map<String, Object> unrelatedPermission = jdbcTemplate.queryForMap("""
            SELECT perm_code, perm_name, module_code, action_code
            FROM admin_permission
            WHERE id = ?
            """, OCCUPIED_PERMISSION_ID);
        assertEquals("ADMIN_MIGRATION_UNRELATED", unrelatedPermission.get("perm_code"));
        assertEquals("迁移无关权限", unrelatedPermission.get("perm_name"));

        Map<String, Object> oldRelation = jdbcTemplate.queryForMap("""
            SELECT role_id, permission_id
            FROM admin_role_permission
            WHERE id = ?
            """, OCCUPIED_RELATION_ID);
        assertEquals(TEMP_ROLE_ID, ((Number) oldRelation.get("role_id")).longValue());
        assertEquals(OCCUPIED_PERMISSION_ID,
            ((Number) oldRelation.get("permission_id")).longValue());

        Map<String, Object> pluginPermission = jdbcTemplate.queryForMap("""
            SELECT id, perm_code
            FROM admin_permission
            WHERE perm_code = 'ADMIN_PLUGIN_CALL'
            """);
        long pluginPermissionId = ((Number) pluginPermission.get("id")).longValue();
        assertNotEquals(OCCUPIED_PERMISSION_ID, pluginPermissionId);
        assertTrue(pluginPermissionId > OCCUPIED_PERMISSION_ID);

        Integer pluginRelationCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM admin_role_permission rp
            JOIN admin_role r ON r.id = rp.role_id
            WHERE r.role_code = 'SUPER_ADMIN'
              AND rp.permission_id = ?
            """, Integer.class, pluginPermissionId);
        assertEquals(1, pluginRelationCount);
    }

    @Test
    void seedShouldAllocatePluginPermissionAndRelationWhenLegacyIdsAreOccupied() {
        jdbcTemplate.update("""
            INSERT INTO admin_permission (
                id, perm_code, perm_name, module_code, action_code,
                status, created_by, updated_by, is_deleted
            ) VALUES (?, 'ADMIN_MIGRATION_UNRELATED', '迁移无关权限', 'MIGRATION', 'TEST',
                      'ACTIVE', NULL, NULL, 0)
            """, OCCUPIED_PERMISSION_ID);
        jdbcTemplate.update("""
            INSERT INTO admin_role_permission (id, role_id, permission_id, created_by)
            VALUES (?, ?, ?, NULL)
            """, OCCUPIED_RELATION_ID, TEMP_ROLE_ID, OCCUPIED_PERMISSION_ID);

        executeSeed();

        Map<String, Object> unrelatedPermission = jdbcTemplate.queryForMap("""
            SELECT perm_code, perm_name
            FROM admin_permission
            WHERE id = ?
            """, OCCUPIED_PERMISSION_ID);
        assertEquals("ADMIN_MIGRATION_UNRELATED", unrelatedPermission.get("perm_code"));
        assertEquals("迁移无关权限", unrelatedPermission.get("perm_name"));

        Map<String, Object> pluginPermission = jdbcTemplate.queryForMap("""
            SELECT id, perm_code
            FROM admin_permission
            WHERE perm_code = 'ADMIN_PLUGIN_CALL'
            """);
        long pluginPermissionId = ((Number) pluginPermission.get("id")).longValue();
        assertEquals("ADMIN_PLUGIN_CALL", pluginPermission.get("perm_code"));
        assertTrue(pluginPermissionId > OCCUPIED_PERMISSION_ID);

        Map<String, Object> occupiedRelation = jdbcTemplate.queryForMap("""
            SELECT role_id, permission_id
            FROM admin_role_permission
            WHERE id = ?
            """, OCCUPIED_RELATION_ID);
        assertEquals(TEMP_ROLE_ID, ((Number) occupiedRelation.get("role_id")).longValue());
        assertEquals(OCCUPIED_PERMISSION_ID,
            ((Number) occupiedRelation.get("permission_id")).longValue());

        List<Map<String, Object>> pluginRelations = jdbcTemplate.queryForList("""
            SELECT rp.id
            FROM admin_role_permission rp
            JOIN admin_role r ON r.id = rp.role_id
            WHERE r.role_code = 'SUPER_ADMIN'
              AND rp.permission_id = ?
            """, pluginPermissionId);
        assertEquals(1, pluginRelations.size());
        long pluginRelationId = ((Number) pluginRelations.get(0).get("id")).longValue();
        assertTrue(pluginRelationId > OCCUPIED_RELATION_ID);
    }

    private void insertOccupiedRelation(long permissionId) {
        jdbcTemplate.update("""
            INSERT INTO admin_role_permission (id, role_id, permission_id, created_by)
            VALUES (?, ?, ?, NULL)
            """, OCCUPIED_RELATION_ID, TEMP_ROLE_ID, permissionId);
    }

    private void executeV56() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/V56__repair_plugin_call_permission_id.sql"));
        populator.setSqlScriptEncoding("UTF-8");
        DatabasePopulatorUtils.execute(populator, dataSource);
    }

    private void executeSeed() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path seedPath = cwd.resolve("scripts/sql/seed_module_org_smoke_data.sql");
        if (!Files.exists(seedPath)) {
            seedPath = cwd.resolve("server/scripts/sql/seed_module_org_smoke_data.sql");
        }
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
            new FileSystemResource(seedPath));
        populator.setSqlScriptEncoding("UTF-8");
        DatabasePopulatorUtils.execute(populator, dataSource);
    }
}
