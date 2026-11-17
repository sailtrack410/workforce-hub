package com.workforcehub.plugin.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class PluginEventSubscriptionRepositoryTest {

    private JdbcTemplate jdbc;
    private PluginEventSubscriptionRepository repository;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        repository = new PluginEventSubscriptionRepository(jdbc);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findShouldMapRowAndQueryWithBothKeys() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("plugin_id")).thenReturn("p1");
        when(rs.getString("event_type")).thenReturn("ORDER_CREATED");
        when(rs.getString("action_code")).thenReturn("notify");
        Timestamp ts = Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(rs.getTimestamp("created_at")).thenReturn(ts);
        when(rs.getTimestamp("updated_at")).thenReturn(ts);

        when(jdbc.query(
            eq("SELECT * FROM sys_plugin_event_subscription WHERE plugin_id = ? AND event_type = ?"),
            any(RowMapper.class),
            eq("p1"), eq("ORDER_CREATED")
        )).thenAnswer(invocation -> {
            RowMapper<PluginEventSubscriptionRow> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });

        Optional<PluginEventSubscriptionRow> result = repository.find("p1", "ORDER_CREATED");

        assertTrue(result.isPresent());
        assertEquals("p1", result.get().pluginId());
        assertEquals("ORDER_CREATED", result.get().eventType());
        assertEquals("notify", result.get().actionCode());
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), result.get().createdAt());
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), result.get().updatedAt());
    }

    @Test
    void findShouldReturnEmptyWhenNotFound() {
        when(jdbc.query(anyString(), any(RowMapper.class), any()))
            .thenReturn(List.of());

        Optional<PluginEventSubscriptionRow> result = repository.find("p1", "MISSING");
        assertTrue(result.isEmpty());
    }

    @Test
    void findByEventTypeShouldQueryAndSortByPluginId() {
        when(jdbc.query(anyString(), any(RowMapper.class), any()))
            .thenReturn(List.of());

        repository.findByEventType("ORDER_CREATED");

        verify(jdbc).query(
            eq("SELECT * FROM sys_plugin_event_subscription WHERE event_type = ? ORDER BY plugin_id"),
            any(RowMapper.class),
            eq("ORDER_CREATED")
        );
    }

    @Test
    void saveShouldUpsertWithCorrectSqlAndParameters() {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

        repository.save("p1", "ORDER_CREATED", "notify");

        verify(jdbc).update(sqlCaptor.capture(), eq("p1"), eq("ORDER_CREATED"), eq("notify"));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("INSERT INTO sys_plugin_event_subscription"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(sql.contains("action_code = VALUES(action_code)"));
        assertTrue(sql.contains("updated_at = CURRENT_TIMESTAMP"));
    }

    @Test
    void deleteShouldQueryWithBothKeys() {
        repository.delete("p1", "ORDER_CREATED");

        verify(jdbc).update(
            eq("DELETE FROM sys_plugin_event_subscription WHERE plugin_id = ? AND event_type = ?"),
            eq("p1"), eq("ORDER_CREATED")
        );
    }
}
