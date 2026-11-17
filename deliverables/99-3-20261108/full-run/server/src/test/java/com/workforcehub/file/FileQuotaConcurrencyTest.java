package com.workforcehub.file;

import com.workforcehub.auth.security.WfhUserDetails;
import com.workforcehub.common.audit.AuditLogWriter;
import com.workforcehub.common.event.DomainEventPublisher;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.file.config.FileStorageProperties;
import com.workforcehub.file.dto.ConfirmUploadRequest;
import com.workforcehub.file.dto.CreateUploadTicketRequest;
import com.workforcehub.file.repository.AttachmentRefRepository;
import com.workforcehub.file.repository.FileObjectRepository;
import com.workforcehub.file.service.*;
import com.workforcehub.file.storage.StorageProvider;
import com.workforcehub.file.storage.StorageProviderRegistry;
import com.workforcehub.im.repository.ImRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 显式指定独立 MySQL 测试库后运行；不读取应用配置、.env 或部署数据库凭证。
 */
@EnabledIfSystemProperty(named = "quota.mysql.url", matches = "jdbc:mysql://127\\.0\\.0\\.1:.*")
class FileQuotaConcurrencyTest {
    private static JdbcTemplate jdbc;
    private static DataSourceTransactionManager transactions;
    private StorageUsageLedgerService ledger;
    private FileQuotaPolicyService quota;
    private FileQueryService files;
    private FileUploadConfirmService confirms;
    private FileUploadTicketService tickets;
    private AuditLogWriter audit;

    @BeforeAll
    static void schema() throws Exception {
        String url = System.getProperty("quota.mysql.url");
        assertTrue(url.contains("/quota_2033_test?"), "只允许本任务的隔离测试库");
        DriverManagerDataSource ds = new DriverManagerDataSource(url, "root", "");
        jdbc = new JdbcTemplate(ds);
        transactions = new DataSourceTransactionManager(ds);
        for (String table : List.of("sys_file_cleanup_job_detail", "sys_file_cleanup_job_run", "sys_file_folder",
            "sys_file_upload_policy", "sys_download_ticket", "sys_storage_usage", "sys_storage_quota_policy",
            "sys_attachment_ref", "sys_upload_ticket", "sys_file_object")) {
            jdbc.execute("DROP TABLE IF EXISTS " + table);
        }
        String init = new ClassPathResource("db/migration/V1__init_schema.sql")
            .getContentAsString(StandardCharsets.UTF_8);
        for (String table : List.of("sys_file_object", "sys_attachment_ref", "sys_upload_ticket")) {
            var ddl = Pattern.compile("CREATE TABLE `" + table + "`.*?;", Pattern.DOTALL).matcher(init);
            assertTrue(ddl.find());
            jdbc.execute(ddl.group());
        }
        for (String migration : List.of("V2__enhance_file_center_schema.sql",
            "V3__link_attachment_to_upload_ticket.sql", "V4__harden_file_center_idempotency.sql", "V5__add_file_upload_policy.sql",
            "V8__add_file_cleanup_job_records.sql", "V29__employee_file_folders.sql")) {
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/" + migration)).execute(ds);
        }
        System.out.println("DATABASE=" + jdbc.queryForObject("SELECT VERSION()", String.class));
    }

    @BeforeEach
    void setup() {
        for (String table : List.of("sys_attachment_ref", "sys_upload_ticket", "sys_file_object",
            "sys_storage_usage", "sys_storage_quota_policy", "sys_file_cleanup_job_detail", "sys_file_folder")) {
            jdbc.update("DELETE FROM " + table);
        }
        ledger = new StorageUsageLedgerService(jdbc);
        audit = mock(AuditLogWriter.class);
        StorageProviderRegistry providers = mock(StorageProviderRegistry.class);
        when(providers.require(any())).thenReturn(mock(StorageProvider.class));
        quota = spy(new FileQuotaPolicyService(jdbc, providers, new FileStorageProperties(), ledger, audit));
        FileUploadRules rules = new FileUploadRules(ledger, quota, mock(ImRepository.class), jdbc);
        FileObjectRepository objects = new FileObjectRepository(jdbc);
        AttachmentRefRepository refs = new AttachmentRefRepository(jdbc);
        DomainEventPublisher events = mock(DomainEventPublisher.class);
        files = new FileQueryService(jdbc, quota, ledger, audit);
        confirms = new FileUploadConfirmService(jdbc, providers, ledger, objects, refs, quota, rules, audit, events);
        tickets = new FileUploadTicketService(jdbc, providers, new FileStorageProperties(), new com.fasterxml.jackson.databind.ObjectMapper(), ledger,
            objects, refs, quota, rules, audit, events);
        policy("USER_DEFAULT", null, 10);
        policy("GLOBAL", null, 10);
    }

    @AfterEach
    void clearUser() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void differentUploadsWithFirstLedgerCreationRespectPersonalQuota() throws Exception {
        policy("GLOBAL", null, 100);
        var first = upload(1, 101, "PERSONAL_FILE");
        var second = upload(2, 101, "PERSONAL_FILE");
        race(101, () -> confirms.confirmEmployeeUpload(first), 101, () -> confirms.confirmEmployeeUpload(second));
        consistent(1, 10, 1);
        assertEquals(1, count("SELECT COUNT(*) FROM sys_upload_ticket WHERE status = 'CONFIRMED'"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_upload_ticket WHERE status = 'PENDING'"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_file_object"));
        assertEquals(10, ledger.usedBytes("USER", 101L));
    }

    @Test
    void differentUsersCompeteForGlobalQuotaWithoutLeakingPersonalReservations() throws Exception {
        policy("USER_DEFAULT", null, 100);
        var first = upload(1, 101, "PERSONAL_FILE");
        var second = upload(2, 102, "PERSONAL_FILE");
        race(101, () -> confirms.confirmEmployeeUpload(first), 102, () -> confirms.confirmEmployeeUpload(second));
        consistent(1, 10, 1);
        assertEquals(10, count("SELECT COALESCE(SUM(used_bytes), 0) FROM sys_storage_usage WHERE scope_type='USER'"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_storage_usage WHERE scope_type='GLOBAL'"));
    }

    @Test
    void concurrentCopiesRespectPersonalQuota() throws Exception {
        seedSource(101, 11, 21);
        ledger.increaseUsage("USER", 101L, 10L, 1);
        ledger.increaseUsage("GLOBAL", null, 10L, 1);
        policy("USER_DEFAULT", null, 20);
        policy("GLOBAL", null, 100);
        race(101, () -> files.copyEmployeeFile(21L, null), 101, () -> files.copyEmployeeFile(21L, null));
        consistent(2, 20, 2);
        assertEquals(20, ledger.usedBytes("USER", 101L));
    }

    @Test
    void concurrentCopiesAcrossUsersRespectGlobalQuota() throws Exception {
        seedSource(101, 11, 21);
        seedSource(102, 12, 22);
        ledger.increaseUsage("USER", 101L, 10L, 1);
        ledger.increaseUsage("USER", 102L, 10L, 1);
        ledger.increaseUsage("GLOBAL", null, 20L, 2);
        policy("USER_DEFAULT", null, 100);
        policy("GLOBAL", null, 30);
        race(101, () -> files.copyEmployeeFile(21L, null), 102, () -> files.copyEmployeeFile(22L, null));
        consistent(3, 30, 3);
        assertEquals(30, count("SELECT SUM(used_bytes) FROM sys_storage_usage WHERE scope_type='USER'"));
    }

    @Test
    void confirmationAndCopyShareTheSameCapacityGate() throws Exception {
        seedSource(101, 11, 21);
        ledger.increaseUsage("USER", 101L, 10L, 1);
        ledger.increaseUsage("GLOBAL", null, 10L, 1);
        policy("USER_DEFAULT", null, 20);
        policy("GLOBAL", null, 20);
        var upload = upload(1, 101, "PERSONAL_FILE");
        race(101, () -> confirms.confirmEmployeeUpload(upload), 101, () -> files.copyEmployeeFile(21L, null));
        consistent(2, 20, 2);
    }

    @Test
    void adminConfirmationAndGroupConfirmationUseGlobalOnly() throws Exception {
        var admin = upload(1, 101, "ADMIN_FILE");
        jdbc.update("UPDATE sys_upload_ticket SET uploader_type='ADMIN' WHERE id=1");
        var group = upload(2, 102, "IM_GROUP_FILE");
        group.setOwnerId(900L);
        jdbc.update("UPDATE sys_upload_ticket SET biz_id=900 WHERE id=2");
        // 本测试只隔离群成员鉴权，配额/引用/事务均使用真实服务和 MySQL。
        // 群配额并未在现有实现执行，因此这里只验证实际生效的 GLOBAL 作用域。
        var rules = mock(FileUploadRules.class);
        var providers = mock(StorageProviderRegistry.class);
        when(providers.require(any())).thenReturn(mock(StorageProvider.class));
        confirms = new FileUploadConfirmService(jdbc, providers, ledger, new FileObjectRepository(jdbc),
            new AttachmentRefRepository(jdbc), quota, rules, audit, mock(DomainEventPublisher.class));
        race(101, () -> confirms.confirmAdminUpload(admin), 102, () -> confirms.confirmEmployeeUpload(group));
        consistent(1, 10, 1);
        assertEquals(0, count("SELECT COUNT(*) FROM sys_storage_usage WHERE scope_type='USER'"));
    }

    @Test
    void confirmedRequestRetryAtFullQuotaDoesNotChargeTwice() {
        var request = upload(1, 101, "PERSONAL_FILE");
        Long first = tx(101, () -> confirms.confirmEmployeeUpload(request)).getAttachmentRefId();
        assertEquals(first, tx(101, () -> confirms.confirmEmployeeUpload(request)).getAttachmentRefId());
        consistent(1, 10, 1);
    }

    @Test
    void concurrentSameTicketRetryDoesNotChargeTwice() throws Exception {
        var request = upload(1, 101, "PERSONAL_FILE");
        synchronizePrechecks();
        List<Object> results = together(101, () -> confirms.confirmEmployeeUpload(request),
            101, () -> confirms.confirmEmployeeUpload(request));
        results.forEach(result -> assertFalse(result instanceof Throwable, result.toString()));
        consistent(1, 10, 1);
    }

    @Test
    void auditFailureRollsBackReservationAndReferencesAndAllowsRetry() {
        var request = upload(1, 101, "PERSONAL_FILE");
        doThrow(new IllegalStateException("quota rollback fixture")).when(audit)
            .write(any(), any(), any(), any(), any(), any(), any());
        assertThrows(IllegalStateException.class, () -> tx(101, () -> confirms.confirmEmployeeUpload(request)));
        consistent(0, 0, 0);
        assertEquals(0, count("SELECT COUNT(*) FROM sys_storage_usage"));
        reset(audit);
        tx(101, () -> confirms.confirmEmployeeUpload(request));
        consistent(1, 10, 1);
    }

    @Test
    void deduplicatedTicketAndCopyShareTheSameCapacityGate() throws Exception {
        seedSource(101, 11, 21);
        ledger.increaseUsage("USER", 101L, 10L, 1);
        ledger.increaseUsage("GLOBAL", null, 10L, 1);
        policy("USER_DEFAULT", null, 20);
        policy("GLOBAL", null, 20);
        var request = new CreateUploadTicketRequest();
        request.setOwnerType("PERSONAL_FILE");
        request.setFileName("dedup.txt");
        request.setMimeType("text/plain");
        request.setFileSize(10L);
        request.setContentSha256(hash(11));
        request.setClientRequestId("dedup-quota-race");
        race(101, () -> tickets.createEmployeeUploadTicket(request), 101, () -> files.copyEmployeeFile(21L, null));
        consistent(2, 20, 2);
    }

    private void synchronizePrechecks() {
        CyclicBarrier barrier = new CyclicBarrier(2);
        doAnswer(call -> {
            call.callRealMethod();
            barrier.await(10, TimeUnit.SECONDS);
            return null;
        }).when(quota).ensureGlobalQuota(10L);
    }

    private void race(long leftUser, Supplier<?> left, long rightUser, Supplier<?> right) throws Exception {
        synchronizePrechecks();
        List<Object> results = together(leftUser, left, rightUser, right);
        long successes = results.stream().filter(value -> !(value instanceof Throwable)).count();
        System.out.println("RACE results=" + results.stream().map(value -> value instanceof Throwable error
            ? error.getClass().getSimpleName() + ":" + error.getMessage() : "SUCCESS").toList());
        assertEquals(1, successes, "余量只容一份时必须恰一成功: " + results);
        Object rejected = results.stream().filter(value -> value instanceof Throwable).findFirst().orElseThrow();
        BizException error = assertInstanceOf(BizException.class, rejected);
        assertEquals(507, error.getHttpStatus().value());
        assertTrue(error.getMessage().contains("空间不足"));
    }

    private List<Object> together(long leftUser, Supplier<?> left, long rightUser, Supplier<?> right) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> a = pool.submit(() -> outcome(leftUser, left));
            Future<Object> b = pool.submit(() -> outcome(rightUser, right));
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Object outcome(long user, Supplier<?> action) {
        try { return tx(user, action); }
        catch (Throwable error) { return error; }
        finally { SecurityContextHolder.clearContext(); }
    }

    private <T> T tx(long userId, Supplier<T> action) {
        WfhUserDetails user = mock(WfhUserDetails.class);
        when(user.getUserId()).thenReturn(userId);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, List.of()));
        TransactionTemplate tx = new TransactionTemplate(transactions);
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        return tx.execute(status -> action.get());
    }

    private void policy(String scope, Long id, long bytes) {
        jdbc.update("INSERT INTO sys_storage_quota_policy (id, policy_code, policy_name, scope_type, scope_id, quota_bytes) "
            + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE quota_bytes=VALUES(quota_bytes)",
            com.workforcehub.common.util.IdGenerator.nextId(), scope, scope, scope, id, bytes);
    }

    private ConfirmUploadRequest upload(long id, long user, String owner) {
        jdbc.update("INSERT INTO sys_upload_ticket (id, ticket_no, uploader_type, uploader_id, biz_type, "
            + "file_name, mime_type, file_size, content_sha256, object_key, expire_at, status) "
            + "VALUES (?, ?, 'USER', ?, ?, 'fixture.txt', 'text/plain', 10, ?, ?, DATE_ADD(NOW(), INTERVAL 1 HOUR), 'PENDING')",
            id, "quota-ticket-" + id, user, owner, hash(id), "quota-object-" + id);
        var request = new ConfirmUploadRequest();
        request.setUploadTicketId(id);
        request.setOwnerType(owner);
        request.setFileSize(10L);
        request.setContentSha256(hash(id));
        request.setObjectKey("quota-object-" + id);
        request.setDisplayName("fixture.txt");
        return request;
    }

    private void seedSource(long user, long objectId, long refId) {
        jdbc.update("INSERT INTO sys_file_object (id, storage_provider, object_key, file_name, mime_type, "
            + "file_size, content_sha256, upload_status, ref_count) VALUES (?, 'LOCAL', ?, 'source.txt', 'text/plain', 10, ?, 'UPLOADED', 1)",
            objectId, "source-" + objectId, hash(objectId));
        new AttachmentRefRepository(jdbc).create(refId, "PERSONAL_FILE", null, objectId, null,
            "source.txt", "source.txt", "USER", user, null);
    }

    private static String hash(long id) { return "%064x".formatted(id); }

    private long count(String sql) { return jdbc.queryForObject(sql, Long.class); }

    private void consistent(long refs, long bytes, long fileCount) {
        assertEquals(refs, count("SELECT COUNT(*) FROM sys_attachment_ref"));
        assertEquals(refs, count("SELECT COALESCE(SUM(ref_count),0) FROM sys_file_object"));
        assertEquals(bytes, ledger.usedBytes("GLOBAL", null));
        assertEquals(fileCount, count("SELECT COALESCE(SUM(file_count),0) FROM sys_storage_usage WHERE scope_type='GLOBAL'"));
    }
}
