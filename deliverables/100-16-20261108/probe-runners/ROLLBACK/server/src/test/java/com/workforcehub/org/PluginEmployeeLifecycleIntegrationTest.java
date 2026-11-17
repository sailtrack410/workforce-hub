package com.workforcehub.org;

import com.workforcehub.auth.security.AdminUserDetails;
import com.workforcehub.common.event.org.EmployeeHiredEvent;
import com.workforcehub.common.event.org.EmployeeLeftEvent;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.org.dto.OrgEmploymentStatusUpdateRequest;
import com.workforcehub.org.dto.OrgUserCreateRequest;
import com.workforcehub.org.service.OrgManagementService;
import com.workforcehub.plugin.runtime.PluginEventDispatcher;
import com.workforcehub.support.LocalIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "workforce.plugin.root=target/test-plugins")
@ActiveProfiles("api")
@RecordApplicationEvents
class PluginEmployeeLifecycleIntegrationTest extends LocalIntegrationTestSupport {

    @Autowired
    private OrgManagementService orgManagementService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationEvents applicationEvents;

    @MockBean
    private PluginEventDispatcher eventDispatcher;

    @BeforeEach
    void setUpAuthentication() {
        authenticateAdmin();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeCreateShouldPublishCompleteHiredSnapshotAfterCommit() {
        Long userId = createUser("PLUGIN-ACTIVE", "ACTIVE", LocalDate.of(2026, 8, 1));

        EmployeeHiredEvent event = applicationEvents.stream(EmployeeHiredEvent.class)
            .filter(candidate -> userId.equals(candidate.userId()))
            .findFirst()
            .orElseThrow();
        assertEquals("PLUGIN-ACTIVE", event.employeeNo());
        assertEquals(111L, event.primaryDeptId());
        assertNull(event.positionId());
        assertNull(event.managerUserId());
        assertEquals("ACTIVE", event.employmentStatus());
        assertEquals(LocalDate.of(2026, 8, 1), event.hireDate());
        assertNotNull(event.occurredAt());
        verify(eventDispatcher).dispatch(argThat(pluginEvent ->
            ("employee-hired-" + userId).equals(pluginEvent.eventId())
        ));
    }

    @Test
    void probationCreateShouldPublishHiredAfterCommit() {
        Long userId = createUser("PLUGIN-PROBATION", "PROBATION", null);

        EmployeeHiredEvent event = applicationEvents.stream(EmployeeHiredEvent.class)
            .filter(candidate -> userId.equals(candidate.userId()))
            .findFirst()
            .orElseThrow();
        assertEquals("PROBATION", event.employmentStatus());
        assertNull(event.hireDate());
        verify(eventDispatcher).dispatch(argThat(pluginEvent ->
            ("employee-hired-" + userId).equals(pluginEvent.eventId())
                && pluginEvent.payload().containsKey("hireDate")
                && pluginEvent.payload().get("hireDate") == null
        ));
    }

    @Test
    void pendingOnboardToActiveShouldPublishHiredOnce() {
        Long userId = createUser("PLUGIN-DIRECT-ACTIVE", "PENDING_ONBOARD", LocalDate.of(2026, 8, 6));

        updateEmploymentStatus(userId, "ACTIVE");
        updateEmploymentStatus(userId, "ACTIVE");

        List<EmployeeHiredEvent> events = applicationEvents.stream(EmployeeHiredEvent.class)
            .filter(event -> userId.equals(event.userId()))
            .toList();
        assertEquals(1, events.size());
        assertEquals("ACTIVE", events.get(0).employmentStatus());
        assertEquals(1L, employeeEventCount(userId));
        verify(eventDispatcher, times(1)).dispatch(argThat(pluginEvent ->
            ("employee-hired-" + userId).equals(pluginEvent.eventId())
        ));
    }

    @Test
    void lifecycleShouldPublishHiredAndLeftOnceAtExactTransitions() {
        Long userId = createUser("PLUGIN-LIFECYCLE", "PENDING_ONBOARD", LocalDate.of(2026, 8, 2));
        verify(eventDispatcher, never()).dispatch(argThat(pluginEvent ->
            ("employee-hired-" + userId).equals(pluginEvent.eventId())
        ));

        updateEmploymentStatus(userId, "PROBATION");
        updateEmploymentStatus(userId, "ACTIVE");
        updateEmploymentStatus(userId, "PENDING_OFFBOARD");
        updateEmploymentStatus(userId, "OFFBOARDED");

        List<EmployeeHiredEvent> hiredEvents = applicationEvents.stream(EmployeeHiredEvent.class)
            .filter(event -> userId.equals(event.userId()))
            .toList();
        List<EmployeeLeftEvent> leftEvents = applicationEvents.stream(EmployeeLeftEvent.class)
            .filter(event -> userId.equals(event.userId()))
            .toList();
        assertEquals(1, hiredEvents.size());
        assertEquals("PROBATION", hiredEvents.get(0).employmentStatus());
        assertEquals(1, leftEvents.size());
        assertEquals("PLUGIN-LIFECYCLE", leftEvents.get(0).employeeNo());
        assertEquals(111L, leftEvents.get(0).primaryDeptId());
        assertNull(leftEvents.get(0).positionId());
        assertNull(leftEvents.get(0).managerUserId());
        assertEquals("PENDING_OFFBOARD", leftEvents.get(0).beforeEmploymentStatus());
        assertEquals("OFFBOARDED", leftEvents.get(0).afterEmploymentStatus());
        verify(eventDispatcher, times(1)).dispatch(argThat(pluginEvent ->
            ("employee-hired-" + userId).equals(pluginEvent.eventId())
        ));
        verify(eventDispatcher, times(1)).dispatch(argThat(pluginEvent ->
            ("employee-left-" + userId).equals(pluginEvent.eventId())
        ));
        assertEquals(4L, employeeEventCount(userId));
    }

    @Test
    void rolledBackActiveCreateMustNotPublishHiredEvent() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        Long[] userIdHolder = new Long[1];

        transactionTemplate.executeWithoutResult(status -> {
            userIdHolder[0] = createUser("PLUGIN-ROLLBACK", "ACTIVE", LocalDate.of(2026, 8, 3));
            status.setRollbackOnly();
        });

        Long userId = userIdHolder[0];
        assertNotNull(userId);
        assertEquals(0L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM org_user WHERE id = ?",
            Long.class,
            userId
        ));
        assertTrue(applicationEvents.stream(EmployeeHiredEvent.class)
            .noneMatch(event -> userId.equals(event.userId())));
        verify(eventDispatcher, never()).dispatch(argThat(pluginEvent ->
            ("employee-hired-" + userId).equals(pluginEvent.eventId())
        ));
    }

    @Test
    void rolledBackOffboardingMustNotPublishLeftEvent() {
        Long userId = createUser("PLUGIN-LEFT-ROLLBACK", "PENDING_OFFBOARD", LocalDate.of(2026, 8, 7));
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            updateEmploymentStatus(userId, "OFFBOARDED");
            status.setRollbackOnly();
        });

        assertEquals("PENDING_OFFBOARD", jdbcTemplate.queryForObject(
            "SELECT employment_status FROM org_user WHERE id = ?",
            String.class,
            userId
        ));
        assertEquals(0L, employeeEventCount(userId));
        assertTrue(applicationEvents.stream(EmployeeLeftEvent.class)
            .noneMatch(event -> userId.equals(event.userId())));
        verify(eventDispatcher, never()).dispatch(argThat(pluginEvent ->
            ("employee-left-" + userId).equals(pluginEvent.eventId())
        ));
    }

    @Test
    void concurrentSameTargetShouldProduceOneMigrationAndOneHiredEvent() throws Exception {
        Long userId = createUser("PLUGIN-CONCURRENT-SAME", "PENDING_ONBOARD", LocalDate.of(2026, 8, 4));

        List<Throwable> results = runConcurrentUpdates(userId, "PROBATION", "PROBATION");

        assertNull(results.get(0));
        assertNull(results.get(1));
        assertEquals(1L, employeeEventCount(userId));
        verify(eventDispatcher, times(1)).dispatch(argThat(pluginEvent ->
            ("employee-hired-" + userId).equals(pluginEvent.eventId())
        ));
    }

    @Test
    void concurrentConflictingTargetsShouldAllowOnlyOneMigration() throws Exception {
        Long userId = createUser("PLUGIN-CONCURRENT-CONFLICT", "PENDING_OFFBOARD", LocalDate.of(2026, 8, 5));

        List<Throwable> results = runConcurrentUpdates(userId, "ACTIVE", "OFFBOARDED");

        assertEquals(1L, results.stream().filter(Objects::isNull).count());
        Throwable failure = results.get(0) == null ? results.get(1) : results.get(0);
        assertInstanceOf(BizException.class, failure);
        assertEquals(1L, employeeEventCount(userId));
        String finalStatus = jdbcTemplate.queryForObject(
            "SELECT employment_status FROM org_user WHERE id = ?",
            String.class,
            userId
        );
        assertTrue(Set.of("ACTIVE", "OFFBOARDED").contains(finalStatus));
        assertTrue(applicationEvents.stream(EmployeeHiredEvent.class)
            .noneMatch(event -> userId.equals(event.userId())));
        verify(eventDispatcher, never()).dispatch(argThat(pluginEvent ->
            ("employee-hired-" + userId).equals(pluginEvent.eventId())
        ));
        if ("OFFBOARDED".equals(finalStatus)) {
            assertEquals(1L, applicationEvents.stream(EmployeeLeftEvent.class)
                .filter(event -> userId.equals(event.userId()))
                .count());
            verify(eventDispatcher, times(1)).dispatch(argThat(pluginEvent ->
                ("employee-left-" + userId).equals(pluginEvent.eventId())
            ));
        } else {
            assertTrue(applicationEvents.stream(EmployeeLeftEvent.class)
                .noneMatch(event -> userId.equals(event.userId())));
            verify(eventDispatcher, never()).dispatch(argThat(pluginEvent ->
                ("employee-left-" + userId).equals(pluginEvent.eventId())
            ));
        }
    }

    private Long createUser(String employeeNo, String employmentStatus, LocalDate hireDate) {
        OrgUserCreateRequest request = new OrgUserCreateRequest();
        request.setEmployeeNo(employeeNo);
        request.setRealName("Plugin Lifecycle Test");
        request.setPrimaryDeptId(111L);
        request.setEmploymentStatus(employmentStatus);
        request.setHireDate(hireDate);
        request.setStatus("ACTIVE");
        return orgManagementService.createUser(request);
    }

    private void updateEmploymentStatus(Long userId, String targetStatus) {
        OrgEmploymentStatusUpdateRequest request = new OrgEmploymentStatusUpdateRequest();
        request.setEmploymentStatus(targetStatus);
        orgManagementService.updateEmploymentStatus(userId, request);
    }

    private long employeeEventCount(Long userId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM hr_employee_event WHERE user_id = ?",
            Long.class,
            userId
        );
    }

    private List<Throwable> runConcurrentUpdates(Long userId, String firstTarget, String secondTarget) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Throwable> first = executor.submit(() -> runUpdate(userId, firstTarget, ready, start));
            Future<Throwable> second = executor.submit(() -> runUpdate(userId, secondTarget, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            return Arrays.asList(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private Throwable runUpdate(Long userId,
                                String targetStatus,
                                CountDownLatch ready,
                                CountDownLatch start) {
        authenticateAdmin();
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                return new AssertionError("并发更新未按时启动");
            }
            updateEmploymentStatus(userId, targetStatus);
            return null;
        } catch (Throwable failure) {
            return failure;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticateAdmin() {
        AdminUserDetails principal = new AdminUserDetails(
            1001L,
            "plugin-event-test",
            "test-password-placeholder",
            "Plugin Event Test",
            "ACTIVE",
            List.of("SUPER_ADMIN"),
            List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
