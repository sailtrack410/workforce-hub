package com.workforcehub.common.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

@Tag("durable-id")
class IdGeneratorTest {
    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        source = new DriverManagerDataSource("jdbc:h2:mem:sequence_" + java.util.UUID.randomUUID()
            + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_id_sequence(sequence_name VARCHAR(32) PRIMARY KEY, high_watermark BIGINT)");
        jdbc.update("INSERT INTO sys_id_sequence VALUES('global', ?)", 1L << 62);
    }

    @AfterEach void cleanup() { jdbc.execute("SHUTDOWN"); }

    @Test void uninitializedAndClosedGeneratorsFailWithoutFallback() throws Exception {
        try (var isolated = new java.net.URLClassLoader(new java.net.URL[]{
            IdGenerator.class.getProtectionDomain().getCodeSource().getLocation()}, null)) {
            var isolatedType = Class.forName(IdGenerator.class.getName(), true, isolated);
            var failure = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> isolatedType.getMethod("nextId").invoke(null));
            assertInstanceOf(IllegalStateException.class, failure.getCause());
        }
        var generator = new DatabaseIdGenerator(source);
        generator.close();
        assertThrows(IllegalStateException.class, generator::nextId);
    }

    @Test void independentInstancesAndRestartNeverRecycleCommittedRanges() {
        long first;
        try (var one = new DatabaseIdGenerator(source); var two = new DatabaseIdGenerator(source)) {
            first = one.nextId();
            assertEquals(first + 1000, two.nextId());
            assertEquals(first + 1, one.nextId());
        }
        try (var restarted = new DatabaseIdGenerator(source)) {
            assertEquals(first + 2000, restarted.nextId());
        }
    }

    @Test void businessRollbackCannotRecycleAllocatedIds() {
        long[] allocated = new long[1];
        try (var generator = new DatabaseIdGenerator(source)) {
            new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(status -> {
                allocated[0] = IdGenerator.nextId();
                status.setRollbackOnly();
            });
        }
        try (var restarted = new DatabaseIdGenerator(source)) {
            assertTrue(restarted.nextId() > allocated[0]);
        }
    }

    @Test void closingNewestContextRestoresPreviousAndPreservesStringContract() {
        try (var one = new DatabaseIdGenerator(source)) {
            long first = IdGenerator.nextId();
            try (var two = new DatabaseIdGenerator(source)) {
                assertTrue(IdGenerator.nextId() >= first + 1000);
            }
            assertEquals(Long.toString(first + 1), IdGenerator.nextIdString());
        }
    }

    @Test void missingSequenceAndDatabaseErrorsFailWithoutFallback() {
        jdbc.update("DELETE FROM sys_id_sequence");
        try (var generator = new DatabaseIdGenerator(source)) {
            assertThrows(IllegalStateException.class, IdGenerator::nextId);
            jdbc.execute("DROP TABLE sys_id_sequence");
            assertThrows(IllegalStateException.class, IdGenerator::nextId);
        }
    }

    @Test void exhaustionNeverWrapsToNegativeIds() {
        jdbc.update("UPDATE sys_id_sequence SET high_watermark=?", Long.MAX_VALUE - 999);
        try (var generator = new DatabaseIdGenerator(source)) {
            assertThrows(IllegalStateException.class, IdGenerator::nextId);
            assertEquals(Long.MAX_VALUE - 999,
                jdbc.queryForObject("SELECT high_watermark FROM sys_id_sequence", Long.class));
        }
    }

    @Test void exhaustedSingleConnectionBusinessPoolCannotBlockIdReservation() {
        var config = new HikariConfig();
        config.setJdbcUrl(source.getUrl());
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(500);
        try (var business = new HikariDataSource(config)) {
            long[] allocated = new long[1];
            try (var generator = new DatabaseIdGenerator(business)) {
                new TransactionTemplate(new DataSourceTransactionManager(business)).executeWithoutResult(status -> {
                    allocated[0] = assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), IdGenerator::nextId);
                    assertEquals(1, business.getHikariPoolMXBean().getActiveConnections());
                    status.setRollbackOnly();
                });
            }
            try (var restarted = new DatabaseIdGenerator(business)) {
                assertTrue(restarted.nextId() > allocated[0]);
            }
        }
    }
}
