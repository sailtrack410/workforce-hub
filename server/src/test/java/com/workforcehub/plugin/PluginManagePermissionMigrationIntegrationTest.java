package com.workforcehub.plugin;

import com.workforcehub.support.LocalIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("api")
class PluginManagePermissionMigrationIntegrationTest extends LocalIntegrationTestSupport {

    private long occupiedPermissionId;
    private long occupiedRelationId;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void arrangeMissingPermissionWithOccupiedIds() {
        occupiedPermissionId = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(id), 7000) + 1 FROM admin_permission", Long.class);
        occupiedRelationId = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(id), 6100) + 1 FROM admin_role_permission", Long.class);
        jdbcTemplate.update("""
            DELETE rp
            FROM admin_role_permission rp
            JOIN admin_permission p ON p.id = rp.permission_id
            WHERE p.perm_code = 'ADMIN_PLUGIN_MANAGE'
            """);
        jdbcTemplate.update("DELETE FROM admin_permission WHERE perm_code = 'ADMIN_PLUGIN_MANAGE'");
        jdbcTemplate.update("""
            INSERT INTO admin_permission (
                id, perm_code, perm_name, module_code, action_code,
                status, created_by, updated_by, is_deleted
            ) VALUES (?, 'ADMIN_PLUGIN_MANAGE_UNRELATED', '迁移无关权限', 'MIGRATION', 'TEST',
                      'ACTIVE', NULL, NULL, 0)
            """, occupiedPermissionId);
        jdbcTemplate.update("""
            INSERT INTO admin_role_permission (id, role_id, permission_id, created_by)
            VALUES (?, 6001, ?, NULL)
            """, occupiedRelationId, occupiedPermissionId);
    }

    @AfterEach
    void cleanupOccupiedIds() {
        jdbcTemplate.update("DELETE FROM admin_role_permission WHERE id = ?", occupiedRelationId);
        jdbcTemplate.update("DELETE FROM admin_permission WHERE id = ?", occupiedPermissionId);
    }

    @Test
    void shouldAllocatePermissionAndRelationWithoutOverwritingExistingRows() {
        executeMigration();
        executeMigration();

        Map<String, Object> unrelated = jdbcTemplate.queryForMap("""
            SELECT perm_code, perm_name
            FROM admin_permission
            WHERE id = ?
            """, occupiedPermissionId);
        assertEquals("ADMIN_PLUGIN_MANAGE_UNRELATED", unrelated.get("perm_code"));
        assertEquals("迁移无关权限", unrelated.get("perm_name"));

        Map<String, Object> permission = jdbcTemplate.queryForMap("""
            SELECT id, module_code, action_code, status, is_deleted
            FROM admin_permission
            WHERE perm_code = 'ADMIN_PLUGIN_MANAGE'
            """);
        long permissionId = ((Number) permission.get("id")).longValue();
        assertTrue(permissionId > occupiedPermissionId);
        assertEquals("PLUGIN", permission.get("module_code"));
        assertEquals("MANAGE", permission.get("action_code"));
        assertEquals("ACTIVE", permission.get("status"));
        assertEquals(0, ((Number) permission.get("is_deleted")).intValue());

        Integer relationCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM admin_role_permission rp
            JOIN admin_role r ON r.id = rp.role_id
            WHERE r.role_code = 'SUPER_ADMIN'
              AND rp.permission_id = ?
            """, Integer.class, permissionId);
        assertEquals(1, relationCount);

        Map<String, Object> occupiedRelation = jdbcTemplate.queryForMap("""
            SELECT role_id, permission_id
            FROM admin_role_permission
            WHERE id = ?
            """, occupiedRelationId);
        assertEquals(6001L, ((Number) occupiedRelation.get("role_id")).longValue());
        assertEquals(occupiedPermissionId,
            ((Number) occupiedRelation.get("permission_id")).longValue());
    }

    private void executeMigration() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding("UTF-8");
        populator.addScript(new ClassPathResource(
            "db/migration/V58__add_plugin_manage_permission.sql"));
        DatabasePopulatorUtils.execute(populator, dataSource);
    }
}
