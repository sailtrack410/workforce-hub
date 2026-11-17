package com.workforcehub.common.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import db.migration.V70__create_durable_id_sequence;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

@Tag("durable-id")
@EnabledIfSystemProperty(named = "id.mysql.url", matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/id_995_test\\?.*")
class IdGeneratorMysqlTest {
    @Test void migrationSpringLifecycleConcurrencyAndBusinessRollback() throws Exception {
        var source = new DriverManagerDataSource(System.getProperty("id.mysql.url"), "root", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP TABLE IF EXISTS flyway_schema_history, sys_id_sequence, legacy_ids");
        jdbc.execute("CREATE TABLE legacy_ids(id BIGINT PRIMARY KEY) ENGINE=InnoDB");
        jdbc.update("INSERT INTO legacy_ids VALUES (?)", 6_000_000_000_000_000_000L);
        Flyway.configure().dataSource(source).locations(new String[0]).baselineOnMigrate(true)
            .javaMigrations(new V70__create_durable_id_sequence()).load().migrate();
        assertEquals(6_000_000_000_000_000_000L,
            jdbc.queryForObject("SELECT high_watermark FROM sys_id_sequence", Long.class));
        var config = new HikariConfig();
        config.setDataSource(source);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(500);
        DatabaseIdGenerator springGenerator;
        try (var business = new HikariDataSource(config); var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(DataSource.class, () -> business);
            context.scan("com.workforcehub.common.util");
            context.refresh();
            springGenerator = context.getBean(DatabaseIdGenerator.class);
            assertNotNull(springGenerator);
            var ids = java.util.concurrent.ConcurrentHashMap.<Long>newKeySet();
            var executor = Executors.newFixedThreadPool(8);
            try {
                var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
                for (int thread = 0; thread < 8; thread++) futures.add(executor.submit(() -> {
                    for (int i = 0; i < 2500; i++) assertTrue(ids.add(IdGenerator.nextId()));
                }));
                for (var future : futures) future.get(30, java.util.concurrent.TimeUnit.SECONDS);
            } finally { executor.shutdownNow(); }
            assertEquals(20000, ids.size());
            long[] rolledBack = new long[1];
            new TransactionTemplate(new DataSourceTransactionManager(business)).executeWithoutResult(status -> {
                rolledBack[0] = assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), IdGenerator::nextId);
                assertEquals(1, business.getHikariPoolMXBean().getActiveConnections());
                status.setRollbackOnly();
            });
            try (var restarted = new DatabaseIdGenerator(source)) {
                assertTrue(restarted.nextId() > rolledBack[0]);
            }
            assertTrue(IdGenerator.nextId() > 6_000_000_000_000_000_000L);
        }
        assertThrows(IllegalStateException.class, springGenerator::nextId);
        System.out.println("MYSQL_MIGRATION_LEGACY_MAX=6000000000000000000; SPRING_THREADS=8; UNIQUE_IDS=20000; BUSINESS_POOL_CAPACITY=1; BUSINESS_POOL_OCCUPIED=1; ID_RESERVATION=success; BUSINESS_ROLLBACK=durable; CONTEXT_CLOSE=fail-closed");
    }
}
