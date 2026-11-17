package com.workforcehub.common.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.datasource.AbstractDriverBasedDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;

@Component
@DependsOnDatabaseInitialization
public final class DatabaseIdGenerator implements AutoCloseable {
    private static final long BLOCK_SIZE = 1000;
    private final DataSource dataSource;
    private final HikariDataSource dedicatedPool;
    private long next;
    private long end;
    private boolean closed;

    public DatabaseIdGenerator(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource pooled) {
            // 业务事务可能耗尽业务池；发号必须使用独立小池，不能等待业务连接释放。
            var config = new HikariConfig();
            pooled.copyStateTo(config);
            config.setPoolName(pooled.getPoolName() + "-ids");
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(0);
            config.setRegisterMbeans(false);
            config.setReadOnly(false);
            dedicatedPool = new HikariDataSource(config);
            this.dataSource = dedicatedPool;
        } else if (dataSource instanceof AbstractDriverBasedDataSource) {
            dedicatedPool = null;
            this.dataSource = dataSource;
        } else {
            throw new IllegalArgumentException("ID generator requires Hikari or an unpooled driver data source");
        }
        IdGenerator.register(this, this::nextId);
    }

    public synchronized long nextId() {
        if (closed) throw new IllegalStateException("ID generator is closed");
        if (next == end) reserve();
        return ++next;
    }

    private void reserve() {
        // 不使用线程绑定的业务连接，号段必须先独立提交才能交付，业务回滚不能回收已发 ID。
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long start;
                try (var statement = connection.prepareStatement(
                    "SELECT high_watermark FROM sys_id_sequence WHERE sequence_name = 'global' FOR UPDATE");
                     var rows = statement.executeQuery()) {
                    if (!rows.next()) throw new SQLException("Global ID sequence is missing");
                    start = rows.getLong(1);
                }
                if (start < 0 || start > Long.MAX_VALUE - BLOCK_SIZE) {
                    throw new SQLException("Global ID sequence is exhausted or invalid");
                }
                try (var statement = connection.prepareStatement(
                    "UPDATE sys_id_sequence SET high_watermark = ? WHERE sequence_name = 'global'")) {
                    statement.setLong(1, start + BLOCK_SIZE);
                    if (statement.executeUpdate() != 1) throw new SQLException("Global ID sequence is missing");
                }
                connection.commit();
                next = start;
                end = start + BLOCK_SIZE;
            } catch (SQLException failure) {
                try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot reserve durable IDs", failure);
        }
    }

    @Override
    public void close() {
        // 与静态入口采用相同锁顺序，防止关闭上下文和发号发生死锁。
        synchronized (IdGenerator.class) {
            synchronized (this) {
                closed = true;
                IdGenerator.unregister(this);
                if (dedicatedPool != null) dedicatedPool.close();
            }
        }
    }
}
