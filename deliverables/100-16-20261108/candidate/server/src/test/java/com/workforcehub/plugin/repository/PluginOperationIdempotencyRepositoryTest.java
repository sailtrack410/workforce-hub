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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PluginOperationIdempotencyRepositoryTest {

    private JdbcTemplate jdbc;
    private PluginOperationIdempotencyRepository repository;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        repository = new PluginOperationIdempotencyRepository(jdbc);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findShouldMapRowAndQueryWithAllKeys() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("plugin_id")).thenReturn("leave-plugin");
        when(rs.getString("operation_code")).thenReturn("LEAVE_APPROVE");
        when(rs.getString("idempotency_key")).thenReturn("key-001");
        when(rs.getString("request_hash")).thenReturn("abc123def456");
        Timestamp ts = Timestamp.valueOf(LocalDateTime.of(2026, 7, 26, 10, 30));
        when(rs.getTimestamp("created_at")).thenReturn(ts);

        when(jdbc.query(
            eq("SELECT * FROM sys_plugin_operation_idempotency WHERE plugin_id = ? AND operation_code = ? AND idempotency_key = ? FOR UPDATE"),
            any(RowMapper.class),
            eq("leave-plugin"), eq("LEAVE_APPROVE"), eq("key-001")
        )).thenAnswer(invocation -> {
            RowMapper<PluginOperationIdempotencyRow> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });

        Optional<PluginOperationIdempotencyRow> result =
            repository.find("leave-plugin", "LEAVE_APPROVE", "key-001");

        assertTrue(result.isPresent());
        assertEquals("leave-plugin", result.get().pluginId());
        assertEquals("LEAVE_APPROVE", result.get().operationCode());
        assertEquals("key-001", result.get().idempotencyKey());
        assertEquals("abc123def456", result.get().requestHash());
        assertEquals(LocalDateTime.of(2026, 7, 26, 10, 30), result.get().createdAt());
    }

    @Test
    void findShouldReturnEmptyWhenNotFound() {
        when(jdbc.query(anyString(), any(RowMapper.class), any()))
            .thenReturn(List.of());

        Optional<PluginOperationIdempotencyRow> result =
            repository.find("leave-plugin", "LEAVE_APPROVE", "missing-key");
        assertTrue(result.isEmpty());
    }

    @Test
    void insertShouldUsePlainInsertWithCorrectParameterOrder() {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

        repository.insert("leave-plugin", "LEAVE_APPROVE", "key-001", "abc123hash");

        verify(jdbc).update(
            sqlCaptor.capture(),
            eq("leave-plugin"), eq("LEAVE_APPROVE"), eq("key-001"), eq("abc123hash")
        );
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("INSERT INTO sys_plugin_operation_idempotency"));
        assertFalse(sql.contains("INSERT IGNORE"));
        assertFalse(sql.contains("ON DUPLICATE KEY"));
    }
}
