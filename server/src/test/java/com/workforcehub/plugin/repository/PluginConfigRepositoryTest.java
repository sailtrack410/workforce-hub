package com.workforcehub.plugin.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginConfigRepositoryTest {

    private JdbcTemplate jdbc;
    private PluginConfigRepository repository;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        repository = new PluginConfigRepository(jdbc);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findShouldMapRowAndUseBothNamespaceKeys() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("plugin_id")).thenReturn("plugin-a");
        when(rs.getString("config_key")).thenReturn("shared.key");
        when(rs.getString("config_value")).thenReturn("value-a");
        when(rs.getString("value_type")).thenReturn("STRING");
        when(rs.getString("default_value")).thenReturn("default-a");
        when(rs.getString("description")).thenReturn("desc");
        when(rs.getInt("requires_restart")).thenReturn(1);
        when(rs.getInt("is_secret")).thenReturn(0);
        when(rs.getInt("is_deleted")).thenReturn(0);
        when(rs.getLong("version")).thenReturn(3L);
        when(rs.getTimestamp("updated_at")).thenReturn(
            Timestamp.valueOf(LocalDateTime.of(2026, 7, 29, 0, 0)));
        when(jdbc.query(anyString(), any(RowMapper.class), eq("plugin-a"), eq("shared.key")))
            .thenAnswer(invocation -> {
                RowMapper<PluginConfigRow> mapper = invocation.getArgument(1);
                return List.of(mapper.mapRow(rs, 0));
            });

        PluginConfigRow row = repository.find("plugin-a", "shared.key").orElseThrow();

        assertEquals("plugin-a", row.pluginId());
        assertEquals("shared.key", row.configKey());
        assertEquals(3L, row.version());
        assertTrue(row.requiresRestart());
        assertFalse(row.deleted());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq("plugin-a"), eq("shared.key"));
        assertTrue(sql.getValue().contains("plugin_id = ? AND config_key = ? AND is_deleted = 0"));
    }

    @Test
    void updateShouldUseOptimisticVersionAndBothNamespaceKeys() {
        PluginConfigRow row = new PluginConfigRow(
            "plugin-a", "shared.key", "next", "STRING", null, null,
            false, false, false, 2, LocalDateTime.now());
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        int updated = repository.update(row, 1);

        assertEquals(1, updated);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(
            sql.capture(),
            eq("next"), eq("STRING"), eq(null), eq(null), eq(0), eq(0),
            eq("plugin-a"), eq("shared.key"), eq(1L)
        );
        assertTrue(sql.getValue().contains("version = version + 1"));
        assertTrue(sql.getValue().contains("plugin_id = ? AND config_key = ? AND version = ?"));
        assertTrue(sql.getValue().contains("is_deleted = 0"));
    }

    @Test
    void deleteShouldUseVersionAndBothNamespaceKeys() {
        repository.delete("plugin-a", "shared.key", 7);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), eq("plugin-a"), eq("shared.key"), eq(7L));
        assertTrue(sql.getValue().contains("is_deleted = 1"));
        assertTrue(sql.getValue().contains("config_value = NULL"));
        assertTrue(sql.getValue().contains("version = version + 1"));
        assertTrue(sql.getValue().contains(
            "plugin_id = ? AND config_key = ? AND version = ?"));
    }
}
