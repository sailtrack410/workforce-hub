package com.workforcehub.task;

import com.workforcehub.common.util.DatabaseIdGenerator;
import com.workforcehub.personal.service.PersonalTodoReminderJob;
import com.workforcehub.system.repository.SysNotificationRepository;
import com.workforcehub.system.service.SysNotificationService;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PersonalTodoReminderTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private DatabaseIdGenerator ids;
    private PersonalTodoReminderJob job;

    @BeforeEach
    void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:reminder_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds); tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V71__add_team_task_and_personal_todo.sql"),
            new ClassPathResource("db/migration/V72__add_personal_todo_start_at.sql"),
            new ClassPathResource("db/migration/V74__add_personal_todo_schedule_and_reminder.sql"),
            new ClassPathResource("db/migration/V75__add_personal_todo_sync_version.sql")).execute(ds);
        jdbc.execute("CREATE TABLE sys_id_sequence (sequence_name VARCHAR(64) PRIMARY KEY, high_watermark BIGINT)");
        jdbc.update("INSERT INTO sys_id_sequence VALUES ('global', 10000)"); ids = new DatabaseIdGenerator(ds);
        jdbc.execute("CREATE TABLE sys_notification (id BIGINT PRIMARY KEY,user_id BIGINT,type VARCHAR(32),title VARCHAR(256),content VARCHAR(1024),link_type VARCHAR(32),link_id VARCHAR(64))");
        job = new PersonalTodoReminderJob(jdbc, new SysNotificationService(new SysNotificationRepository(jdbc)));
        jdbc.update("INSERT INTO personal_todo (todo_id,owner_id,title,category,status,reminder_at) VALUES (101,2002,'会议','MEETING','PENDING',?), (102,2003,'未来','TASK','PENDING',?), (103,2002,'已完成','TASK','DONE',?)", LocalDateTime.now().minusMinutes(1),LocalDateTime.now().plusHours(1),LocalDateTime.now().minusMinutes(1));
    }
    @AfterEach
    void close() { ids.close(); jdbc.execute("SHUTDOWN"); }

    @Test
    void sendsDueReminderToOwnerOnceAndSkipsFutureCompletedAndUndated() {
        tx.executeWithoutResult(s -> job.deliverDueReminders());
        tx.executeWithoutResult(s -> job.deliverDueReminders());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_notification", Integer.class));
        var row = jdbc.queryForMap("SELECT * FROM sys_notification");
        assertEquals(2002L, ((Number)row.get("user_id")).longValue());
        assertEquals("PERSONAL_TODO", row.get("link_type")); assertEquals("101", row.get("link_id"));
        assertNotNull(jdbc.queryForObject("SELECT reminded_at FROM personal_todo WHERE todo_id=101",java.sql.Timestamp.class));
    }

    @Test
    void concurrentWorkersClaimTheSameReminderOnlyOnce() throws Exception {
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> tx.executeWithoutResult(s -> job.deliverDueReminders()));
            var second = executor.submit(() -> tx.executeWithoutResult(s -> job.deliverDueReminders()));
            first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            second.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_notification", Integer.class));
        } finally { executor.shutdownNow(); }
    }

    @Test
    void failedNotificationRollsBackClaimAndCanRetry() {
        jdbc.execute("ALTER TABLE sys_notification ADD CONSTRAINT reject_notice CHECK (user_id <> 2002)");
        assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(s -> job.deliverDueReminders()));
        assertNull(jdbc.queryForObject("SELECT reminded_at FROM personal_todo WHERE todo_id=101",java.sql.Timestamp.class));
        jdbc.execute("ALTER TABLE sys_notification DROP CONSTRAINT reject_notice");
        tx.executeWithoutResult(s -> job.deliverDueReminders());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_notification",Integer.class));
    }
}
