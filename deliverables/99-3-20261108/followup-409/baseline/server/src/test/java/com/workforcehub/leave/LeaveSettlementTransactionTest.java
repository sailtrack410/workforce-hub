package com.workforcehub.leave;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.leave.repository.LeaveRepository;
import com.workforcehub.leave.service.LeaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Uses a private database to exercise real row locks, JDBC writes and Spring rollback. */
class LeaveSettlementTransactionTest {
    private JdbcTemplate jdbc;
    private LeaveService service;
    private TransactionTemplate transactions;
    private AnnotationConfigApplicationContext context;

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:leave_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean LeaveRepository leaveRepository(JdbcTemplate jdbc) { return new LeaveRepository(jdbc); }
        @Bean LeaveService leaveService(LeaveRepository repo) { return new LeaveService(repo); }
    }

    @BeforeEach
    void setup() throws Exception {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = context.getBean(JdbcTemplate.class);
        service = context.getBean(LeaveService.class);
        transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        jdbc.execute("CREATE TABLE org_user(id BIGINT PRIMARY KEY, employee_no VARCHAR(32), real_name VARCHAR(32))");
        jdbc.execute("CREATE TABLE lv_type(id BIGINT PRIMARY KEY, leave_code VARCHAR(32), leave_name VARCHAR(32),"
            + "balance_mode VARCHAR(32), status VARCHAR(32), is_deleted INT DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE wf_request(id BIGINT PRIMARY KEY, applicant_user_id BIGINT, biz_type VARCHAR(32))");
        jdbc.execute("CREATE TABLE wf_req_leave(request_id BIGINT PRIMARY KEY, leave_type_id BIGINT, duration_minutes INT)");
        // Exercise the production migration rather than silently creating the new column in a fixture.
        try (var input = getClass().getResourceAsStream("/db/migration/V69__snapshot_leave_balance_mode.sql")) {
            if (input != null) jdbc.execute(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            else jdbc.execute("ALTER TABLE wf_req_leave ADD COLUMN balance_mode_snapshot VARCHAR(32) DEFAULT NULL");
        }
        jdbc.execute("CREATE TABLE lv_account(id BIGINT PRIMARY KEY, user_id BIGINT, leave_type_id BIGINT, available_minutes INT, frozen_minutes INT, version INT, status VARCHAR(32), updated_by BIGINT)");
        jdbc.execute("CREATE TABLE lv_account_txn(id BIGINT PRIMARY KEY, account_id BIGINT, txn_type VARCHAR(32), change_minutes INT, balance_after INT, source_type VARCHAR(32), source_id BIGINT, source_txn_key VARCHAR(128) UNIQUE, effective_at TIMESTAMP)");
        jdbc.update("INSERT INTO lv_type(id, leave_code, leave_name, balance_mode, status) VALUES(2, 'ANNUAL', '年假', 'DEDUCT', 'ACTIVE')");
        jdbc.update("INSERT INTO lv_account VALUES(3, 1, 2, 1440, 0, 0, 'ACTIVE', 1)");
        request(10);
    }

    @AfterEach
    void close() {
        jdbc.execute("SHUTDOWN");
        context.close();
    }

    private void request(long id) {
        jdbc.update("INSERT INTO wf_request VALUES(?, 1, 'LEAVE')", id);
        jdbc.update("INSERT INTO wf_req_leave(request_id, leave_type_id, duration_minutes) VALUES(?, 2, 480)", id);
    }

    private void submit(long id) { service.freezeForWorkflowSubmission(1L, 2L, 480, id, 1L); }
    private void settle(long id, String outcome) {
        if ("APPROVED".equals(outcome)) service.consumeFrozenForWorkflowApproval(1L, 2L, 480, id, 1L);
        else service.releaseFrozenForWorkflowTerminal(1L, 2L, 480, id, outcome, 1L);
    }
    private void balances(int available, int frozen) {
        assertEquals(available, jdbc.queryForObject("SELECT available_minutes FROM lv_account WHERE id=3", Integer.class));
        assertEquals(frozen, jdbc.queryForObject("SELECT frozen_minutes FROM lv_account WHERE id=3", Integer.class));
    }

    @ParameterizedTest
    @CsvSource({"MODE,APPROVED", "MODE,REJECTED", "MODE,WITHDRAWN", "INACTIVE,APPROVED", "INACTIVE,REJECTED",
        "INACTIVE,WITHDRAWN", "DELETED,APPROVED", "DELETED,REJECTED", "DELETED,WITHDRAWN"})
    void frozenSettlementSurvivesConfigurationChanges(String change, String outcome) {
        submit(10);
        balances(960, 480);
        switch (change) {
            case "MODE" -> jdbc.update("UPDATE lv_type SET balance_mode='NO_DEDUCT' WHERE id=2");
            case "INACTIVE" -> jdbc.update("UPDATE lv_type SET status='INACTIVE' WHERE id=2");
            case "DELETED" -> jdbc.update("UPDATE lv_type SET is_deleted=1 WHERE id=2");
        }
        settle(10, outcome);
        System.out.printf("SETTLEMENT change=%s outcome=%s available=%d frozen=%d%n", change, outcome,
            jdbc.queryForObject("SELECT available_minutes FROM lv_account WHERE id=3", Integer.class),
            jdbc.queryForObject("SELECT frozen_minutes FROM lv_account WHERE id=3", Integer.class));
        settle(10, outcome);
        balances("APPROVED".equals(outcome) ? 960 : 1440, 0);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM lv_account_txn", Integer.class));
    }

    @Test
    void originallyNonDeductingRequestNeverUsesAnotherRequestsFreeze() {
        jdbc.update("UPDATE lv_type SET balance_mode='NO_DEDUCT' WHERE id=2");
        submit(10);
        jdbc.update("UPDATE lv_type SET balance_mode='DEDUCT' WHERE id=2");
        request(11);
        submit(11);
        settle(10, "APPROVED");
        settle(10, "REJECTED");
        balances(960, 480);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lv_account_txn", Integer.class));
    }

    @ParameterizedTest
    @CsvSource({"APPROVED", "REJECTED", "WITHDRAWN"})
    void legacyFreezeIsProofEvenWithoutSnapshot(String outcome) {
        submit(10);
        jdbc.update("UPDATE wf_req_leave SET balance_mode_snapshot=NULL WHERE request_id=10");
        jdbc.update("UPDATE lv_type SET is_deleted=1, status='INACTIVE', balance_mode='NO_DEDUCT' WHERE id=2");
        settle(10, outcome);
        balances("APPROVED".equals(outcome) ? 960 : 1440, 0);
    }

    @Test
    void unknownLegacyRuleCannotGuessDeductionOrStealFrozenFunds() {
        request(11);
        submit(11);
        assertThrows(BizException.class, () -> settle(10, "APPROVED"));
        settle(10, "REJECTED");
        balances(960, 480);
    }

    @Test
    void knownHistoricalDeductWithoutFreezeUsesOnlyAvailableBalance() {
        jdbc.update("UPDATE wf_req_leave SET balance_mode_snapshot='DEDUCT' WHERE request_id=10");
        request(11);
        submit(11);
        settle(10, "APPROVED");
        settle(10, "APPROVED");
        balances(480, 480);
    }

    @Test
    void differentTerminalOutcomesCannotReleaseOrConsumeTwice() {
        submit(10);
        settle(10, "REJECTED");
        assertThrows(BizException.class, () -> settle(10, "WITHDRAWN"));
        assertThrows(BizException.class, () -> settle(10, "APPROVED"));
        balances(1440, 0);
    }

    @ParameterizedTest
    @CsvSource({"APPROVED", "REJECTED", "WITHDRAWN"})
    void concurrentSameOutcomeSettlesExactlyOnce(String outcome) throws Exception {
        submit(10);
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Void> action = () -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                settle(10, outcome);
                return null;
            };
            var a = pool.submit(action);
            var b = pool.submit(action);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            a.get(10, TimeUnit.SECONDS);
            b.get(10, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        balances("APPROVED".equals(outcome) ? 960 : 1440, 0);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM lv_account_txn", Integer.class));
    }

    @Test
    void competingOutcomesCannotSpendAnotherRequestsFrozenBalance() throws Exception {
        submit(10);
        request(11);
        submit(11);
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Boolean> approve = () -> {
                start.await();
                try { settle(10, "APPROVED"); return true; }
                catch (BizException conflict) { return false; }
            };
            java.util.concurrent.Callable<Boolean> reject = () -> {
                start.await();
                try { settle(10, "REJECTED"); return true; }
                catch (BizException conflict) { return false; }
            };
            var a = pool.submit(approve);
            var b = pool.submit(reject);
            start.countDown();
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
        assertEquals(480, jdbc.queryForObject("SELECT frozen_minutes FROM lv_account WHERE id=3", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lv_account_txn WHERE source_id=10 AND txn_type IN ('DEDUCT','UNFREEZE')", Integer.class));
        settle(11, "REJECTED");
        assertEquals(0, jdbc.queryForObject("SELECT frozen_minutes FROM lv_account WHERE id=3", Integer.class));
    }

    @Test
    void submissionFailureRollsBackSnapshotAndBalance() {
        jdbc.update("UPDATE lv_account SET available_minutes=240 WHERE id=3");
        assertThrows(BizException.class, () -> submit(10));
        balances(240, 0);
        assertNull(jdbc.queryForObject("SELECT balance_mode_snapshot FROM wf_req_leave WHERE request_id=10", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM lv_account_txn", Integer.class));
    }

    @Test
    void settlementArgumentsMustMatchPersistedRequest() {
        submit(10);
        assertThrows(BizException.class, () -> service.consumeFrozenForWorkflowApproval(9L, 2L, 480, 10L, 1L));
        assertThrows(BizException.class, () -> service.consumeFrozenForWorkflowApproval(1L, 2L, 240, 10L, 1L));
        balances(960, 480);
    }

    @Test
    void invalidFreezeFingerprintAndMissingFundsFailWithoutChangingBalances() {
        submit(10);
        jdbc.update("UPDATE lv_account_txn SET change_minutes=-240 WHERE txn_type='FREEZE'");
        assertThrows(BizException.class, () -> settle(10, "APPROVED"));
        balances(960, 480);
        jdbc.update("UPDATE lv_account_txn SET change_minutes=-480 WHERE txn_type='FREEZE'");
        jdbc.update("UPDATE lv_account SET frozen_minutes=240 WHERE id=3");
        assertThrows(BizException.class, () -> settle(10, "REJECTED"));
        balances(960, 240);
    }

    @ParameterizedTest
    @CsvSource({"APPROVED", "REJECTED", "WITHDRAWN"})
    void failedLedgerWriteRollsBackBalanceAndAllowsRetry(String outcome) {
        submit(10);
        jdbc.execute("ALTER TABLE lv_account_txn ADD CONSTRAINT reject_terminal CHECK (txn_type='FREEZE')");
        assertThrows(RuntimeException.class, () -> settle(10, outcome));
        balances(960, 480);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lv_account_txn", Integer.class));
        jdbc.execute("ALTER TABLE lv_account_txn DROP CONSTRAINT reject_terminal");
        settle(10, outcome);
        balances("APPROVED".equals(outcome) ? 960 : 1440, 0);
    }

    @Test
    void outerWorkflowTransactionFailureRollsBackSettlement() {
        submit(10);
        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
            settle(10, "APPROVED");
            throw new IllegalStateException("workflow write failed");
        }));
        balances(960, 480);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lv_account_txn", Integer.class));
    }

    @ParameterizedTest
    @CsvSource({"INACTIVE", "DELETED"})
    void newSubmissionStillValidatesCurrentConfiguration(String change) {
        if ("INACTIVE".equals(change)) jdbc.update("UPDATE lv_type SET status='INACTIVE' WHERE id=2");
        else jdbc.update("UPDATE lv_type SET is_deleted=1 WHERE id=2");
        assertThrows(BizException.class, () -> submit(10));
        balances(1440, 0);
    }
}
