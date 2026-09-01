package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.repository.PluginEventSubscriptionRepository;
import com.workforcehub.plugin.repository.PluginOperationIdempotencyRepository;
import com.workforcehub.plugin.repository.PluginOperationIdempotencyRow;
import com.workforcehub.plugin.sdk.ActionDescriptor;
import com.workforcehub.plugin.sdk.ErrorCodes;
import com.workforcehub.plugin.sdk.EventTypes;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import com.workforcehub.plugin.sdk.ServiceResult;
import com.workforcehub.plugin.sdk.TestInvocationContextBinder;
import com.workforcehub.plugin.sdk.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DefaultEventServiceTest {

    private static final String PLUGIN_ID = "test-plugin";
    private static final String ACTION_CODE = "handle.workflow.approved";
    private static final String EVENT_TYPE = EventTypes.WORKFLOW_APPROVED;
    private static final String IDEMPOTENCY_KEY = "key-001";

    private ObjectProvider<PluginManagerService> pluginManagerProvider;
    private PluginEventSubscriptionRepository subscriptionRepo;
    private PluginOperationIdempotencyRepository idempotencyRepo;
    private PluginPermissionService permissionService;
    private PluginAuditService auditService;
    private PluginManagerService pluginManagerService;
    private PluginActionHandler pluginActionHandler;
    private DefaultEventService service;
    private TestInvocationContextBinder binder;
    private InvocationContext ctx;

    @BeforeEach
    void setUp() {
        pluginManagerProvider = mock(ObjectProvider.class);
        subscriptionRepo = mock(PluginEventSubscriptionRepository.class);
        idempotencyRepo = mock(PluginOperationIdempotencyRepository.class);
        permissionService = mock(PluginPermissionService.class);
        auditService = mock(PluginAuditService.class);
        pluginManagerService = mock(PluginManagerService.class);
        pluginActionHandler = mock(PluginActionHandler.class);
        binder = new TestInvocationContextBinder();
        ctx = binder.forPluginStartup(PLUGIN_ID);

        when(pluginManagerProvider.getIfAvailable()).thenReturn(pluginManagerService);
        lenient().when(pluginActionHandler.getDescriptor()).thenReturn(new ActionDescriptor(
            ACTION_CODE,
            "test",
            "test",
            Map.of(),
            Map.of(),
            "EVENT_SUBSCRIBE",
            Duration.ofSeconds(5),
            true
        ));
        lenient().when(pluginManagerService.findActionHandler(anyString(), anyString()))
            .thenReturn(Optional.of(pluginActionHandler));

        service = new DefaultEventService(
            pluginManagerProvider,
            subscriptionRepo,
            idempotencyRepo,
            permissionService,
            auditService
        );
    }

    @Test
    void subscribeSuccess() {
        ServiceResult<Void> result = service.subscribe(ctx, EVENT_TYPE, ACTION_CODE, IDEMPOTENCY_KEY);

        assertEquals(0, result.getCode());
        verify(permissionService).checkPluginCapability(PLUGIN_ID, "EVENT_SUBSCRIBE");
        verify(permissionService).checkPluginCapability(PLUGIN_ID, "EVENT_SUBSCRIBE_WORKFLOW_APPROVED");
        verify(idempotencyRepo).insert(eq(PLUGIN_ID), eq("EVENT_SUBSCRIBE"), eq(IDEMPOTENCY_KEY), anyString());
        verify(subscriptionRepo).save(PLUGIN_ID, EVENT_TYPE, ACTION_CODE);
        verify(auditService).writeEventSubscription(ctx, EVENT_TYPE, ACTION_CODE, true);
    }

    @Test
    void subscribeShouldUseHostThreadStateForAuditAfterDescriptorPollution() {
        Thread hostThread = Thread.currentThread();
        ClassLoader original = hostThread.getContextClassLoader();
        ClassLoader pluginClassLoader = new ClassLoader(original) { };
        AtomicReference<ClassLoader> auditClassLoader = new AtomicReference<>();
        AtomicReference<Boolean> auditInterrupted = new AtomicReference<>();
        ActionDescriptor descriptor = new ActionDescriptor(
            ACTION_CODE,
            "test",
            "test",
            Map.of(),
            Map.of(),
            "EVENT_SUBSCRIBE",
            Duration.ofSeconds(5),
            true
        );
        when(pluginActionHandler.getDescriptor()).thenAnswer(invocation -> {
            hostThread.setContextClassLoader(pluginClassLoader);
            hostThread.interrupt();
            return descriptor;
        });
        doAnswer(invocation -> {
            auditClassLoader.set(hostThread.getContextClassLoader());
            auditInterrupted.set(hostThread.isInterrupted());
            return null;
        }).when(auditService).writeEventSubscription(ctx, EVENT_TYPE, ACTION_CODE, true);

        try {
            ServiceResult<Void> result = service.subscribe(
                ctx, EVENT_TYPE, ACTION_CODE, IDEMPOTENCY_KEY);

            assertEquals(0, result.getCode());
            assertSame(DefaultEventService.class.getClassLoader(), auditClassLoader.get());
            assertEquals(Boolean.FALSE, auditInterrupted.get());
            assertSame(DefaultEventService.class.getClassLoader(), hostThread.getContextClassLoader());
            assertFalse(hostThread.isInterrupted());
        } finally {
            hostThread.setContextClassLoader(original);
            Thread.interrupted();
        }
    }

    @Test
    void unsubscribeSuccess() {
        ServiceResult<Void> result = service.unsubscribe(ctx, EVENT_TYPE, IDEMPOTENCY_KEY);

        assertEquals(0, result.getCode());
        verify(permissionService).checkPluginCapability(PLUGIN_ID, "EVENT_SUBSCRIBE");
        verify(idempotencyRepo).insert(eq(PLUGIN_ID), eq("EVENT_UNSUBSCRIBE"), eq(IDEMPOTENCY_KEY), anyString());
        verify(subscriptionRepo).delete(PLUGIN_ID, EVENT_TYPE);
        verify(auditService).writeEventSubscription(ctx, EVENT_TYPE, null, false);
    }

    @Test
    void totalCapabilityDeniedThrowsBeforeSpecificCapabilityOrWrites() {
        doThrow(new PluginPermissionService.PermissionDeniedException("no total cap"))
            .when(permissionService).checkPluginCapability(PLUGIN_ID, "EVENT_SUBSCRIBE");

        assertThrows(PluginPermissionService.PermissionDeniedException.class,
            () -> service.subscribe(ctx, EVENT_TYPE, ACTION_CODE, IDEMPOTENCY_KEY));

        verify(permissionService, never())
            .checkPluginCapability(PLUGIN_ID, "EVENT_SUBSCRIBE_WORKFLOW_APPROVED");
        verifyNoInteractions(idempotencyRepo, subscriptionRepo);
        verify(pluginManagerService, never()).findActionHandler(anyString(), anyString());
    }

    @Test
    void unsubscribeTotalCapabilityDeniedDoesNotWrite() {
        doThrow(new PluginPermissionService.PermissionDeniedException("no total cap"))
            .when(permissionService).checkPluginCapability(PLUGIN_ID, "EVENT_SUBSCRIBE");

        assertThrows(PluginPermissionService.PermissionDeniedException.class,
            () -> service.unsubscribe(ctx, EVENT_TYPE, IDEMPOTENCY_KEY));

        verifyNoInteractions(idempotencyRepo, subscriptionRepo);
    }

    @Test
    void subscribeChecksCorrectCapabilityForEachEventType() {
        record TestCase(String eventType, String expectedCap) {}
        var cases = new TestCase[] {
            new TestCase(EventTypes.WORKFLOW_APPROVED, "EVENT_SUBSCRIBE_WORKFLOW_APPROVED"),
            new TestCase(EventTypes.WORKFLOW_REJECTED, "EVENT_SUBSCRIBE_WORKFLOW_REJECTED"),
            new TestCase(EventTypes.EMPLOYEE_HIRED, "EVENT_SUBSCRIBE_EMPLOYEE_HIRED"),
            new TestCase(EventTypes.EMPLOYEE_LEFT, "EVENT_SUBSCRIBE_EMPLOYEE_LEFT"),
            new TestCase(EventTypes.FILE_UPLOADED, "EVENT_SUBSCRIBE_FILE_UPLOADED"),
            new TestCase(EventTypes.ATTENDANCE_EXCEPTION, "EVENT_SUBSCRIBE_ATTENDANCE_EXCEPTION"),
        };

        for (TestCase tc : cases) {
            reset(permissionService, idempotencyRepo, subscriptionRepo);
            service.subscribe(ctx, tc.eventType(), ACTION_CODE, "key-" + tc.eventType());
            verify(permissionService).checkPluginCapability(PLUGIN_ID, "EVENT_SUBSCRIBE");
            verify(permissionService).checkPluginCapability(PLUGIN_ID, tc.expectedCap());
        }
    }

    @Test
    void eventCapabilityDeniedThrowsAndDoesNotDiscoverActionOrWriteIdempotency() {
        doThrow(new PluginPermissionService.PermissionDeniedException("no cap"))
            .when(permissionService).checkPluginCapability(PLUGIN_ID, "EVENT_SUBSCRIBE_WORKFLOW_APPROVED");

        assertThrows(PluginPermissionService.PermissionDeniedException.class,
            () -> service.subscribe(ctx, EVENT_TYPE, ACTION_CODE, IDEMPOTENCY_KEY));

        verifyNoInteractions(idempotencyRepo, subscriptionRepo);
        verify(pluginManagerService, never()).findActionHandler(anyString(), anyString());
    }

    @Test
    void duplicateSameSubscribeHashReturnsOkWithoutRewrite() {
        String hash = DefaultEventService.computeHash(EVENT_TYPE, ACTION_CODE);
        doThrow(new DuplicateKeyException("dup"))
            .when(idempotencyRepo).insert(eq(PLUGIN_ID), eq("EVENT_SUBSCRIBE"), eq(IDEMPOTENCY_KEY), anyString());
        when(idempotencyRepo.find(PLUGIN_ID, "EVENT_SUBSCRIBE", IDEMPOTENCY_KEY))
            .thenReturn(Optional.of(new PluginOperationIdempotencyRow(
                PLUGIN_ID, "EVENT_SUBSCRIBE", IDEMPOTENCY_KEY, hash, LocalDateTime.now())));

        ServiceResult<Void> result = service.subscribe(ctx, EVENT_TYPE, ACTION_CODE, IDEMPOTENCY_KEY);

        assertEquals(0, result.getCode());
        verify(subscriptionRepo, never()).save(anyString(), anyString(), anyString());
        verifyNoInteractions(auditService);
    }

    @Test
    void duplicateSameUnsubscribeHashReturnsOkWithoutDelete() {
        String hash = DefaultEventService.computeHash(EVENT_TYPE);
        doThrow(new DuplicateKeyException("dup"))
            .when(idempotencyRepo).insert(eq(PLUGIN_ID), eq("EVENT_UNSUBSCRIBE"), eq(IDEMPOTENCY_KEY), anyString());
        when(idempotencyRepo.find(PLUGIN_ID, "EVENT_UNSUBSCRIBE", IDEMPOTENCY_KEY))
            .thenReturn(Optional.of(new PluginOperationIdempotencyRow(
                PLUGIN_ID, "EVENT_UNSUBSCRIBE", IDEMPOTENCY_KEY, hash, LocalDateTime.now())));

        ServiceResult<Void> result = service.unsubscribe(ctx, EVENT_TYPE, IDEMPOTENCY_KEY);

        assertEquals(0, result.getCode());
        verify(subscriptionRepo, never()).delete(anyString(), anyString());
        verifyNoInteractions(auditService);
    }

    @Test
    void duplicateDifferentHashReturnsConflictWithoutDelete() {
        doThrow(new DuplicateKeyException("dup"))
            .when(idempotencyRepo).insert(eq(PLUGIN_ID), eq("EVENT_UNSUBSCRIBE"), eq(IDEMPOTENCY_KEY), anyString());
        when(idempotencyRepo.find(PLUGIN_ID, "EVENT_UNSUBSCRIBE", IDEMPOTENCY_KEY))
            .thenReturn(Optional.of(new PluginOperationIdempotencyRow(
                PLUGIN_ID, "EVENT_UNSUBSCRIBE", IDEMPOTENCY_KEY, "different-hash", LocalDateTime.now())));

        ServiceResult<Void> result = service.unsubscribe(ctx, EVENT_TYPE, IDEMPOTENCY_KEY);

        assertEquals(ErrorCodes.DUPLICATE_OPERATION, result.getCode());
        verify(subscriptionRepo, never()).delete(anyString(), anyString());
    }

    @Test
    void duplicateDifferentSubscribeHashReturnsConflict() {
        doThrow(new DuplicateKeyException("dup"))
            .when(idempotencyRepo).insert(eq(PLUGIN_ID), eq("EVENT_SUBSCRIBE"), eq(IDEMPOTENCY_KEY), anyString());
        when(idempotencyRepo.find(PLUGIN_ID, "EVENT_SUBSCRIBE", IDEMPOTENCY_KEY))
            .thenReturn(Optional.of(new PluginOperationIdempotencyRow(
                PLUGIN_ID, "EVENT_SUBSCRIBE", IDEMPOTENCY_KEY, "different-hash", LocalDateTime.now())));

        ServiceResult<Void> result = service.subscribe(ctx, EVENT_TYPE, ACTION_CODE, IDEMPOTENCY_KEY);

        assertEquals(ErrorCodes.DUPLICATE_OPERATION, result.getCode());
        verify(subscriptionRepo, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void nullCtxReturnsInvalidParam() {
        ServiceResult<Void> result = service.subscribe(null, EVENT_TYPE, ACTION_CODE, IDEMPOTENCY_KEY);

        assertEquals(ErrorCodes.INVALID_PARAM, result.getCode());
        verifyNoInteractions(idempotencyRepo, subscriptionRepo, permissionService);
    }

    @Test
    void nullActionCodeReturnsInvalidParam() {
        ServiceResult<Void> result = service.subscribe(ctx, EVENT_TYPE, null, IDEMPOTENCY_KEY);

        assertEquals(ErrorCodes.INVALID_PARAM, result.getCode());
        verifyNoInteractions(idempotencyRepo, subscriptionRepo, permissionService);
    }

    @Test
    void blankActionCodeReturnsInvalidParam() {
        ServiceResult<Void> result = service.subscribe(ctx, EVENT_TYPE, "  ", IDEMPOTENCY_KEY);

        assertEquals(ErrorCodes.INVALID_PARAM, result.getCode());
        verifyNoInteractions(idempotencyRepo, subscriptionRepo, permissionService);
    }

    @Test
    void tooLongActionCodeReturnsInvalidParam() {
        String longCode = "a".repeat(129);

        ServiceResult<Void> result = service.subscribe(ctx, EVENT_TYPE, longCode, IDEMPOTENCY_KEY);

        assertEquals(ErrorCodes.INVALID_PARAM, result.getCode());
        verifyNoInteractions(idempotencyRepo, subscriptionRepo, permissionService);
    }

    @Test
    void tooLongPluginIdReturnsInvalidParam() {
        InvocationContext longIdCtx = binder.forPluginStartup("a".repeat(65));

        ServiceResult<Void> result = service.subscribe(longIdCtx, EVENT_TYPE, ACTION_CODE, IDEMPOTENCY_KEY);

        assertEquals(ErrorCodes.INVALID_PARAM, result.getCode());
        verifyNoInteractions(idempotencyRepo, subscriptionRepo, permissionService);
    }

    @Test
    void tooLongEventTypeReturnsInvalidParam() {
        String longType = "A".repeat(129);

        ServiceResult<Void> result = service.subscribe(ctx, longType, ACTION_CODE, IDEMPOTENCY_KEY);

        assertEquals(ErrorCodes.INVALID_PARAM, result.getCode());
    }

    @Test
    void tooLongIdempotencyKeyReturnsInvalidParam() {
        String longKey = "k".repeat(129);

        ServiceResult<Void> result = service.subscribe(ctx, EVENT_TYPE, ACTION_CODE, longKey);

        assertEquals(ErrorCodes.INVALID_PARAM, result.getCode());
    }

    @Test
    void unknownEventTypeReturnsInvalidParam() {
        ServiceResult<Void> result = service.subscribe(ctx, "UNKNOWN_EVENT", ACTION_CODE, IDEMPOTENCY_KEY);

        assertEquals(ErrorCodes.INVALID_PARAM, result.getCode());
        verifyNoInteractions(idempotencyRepo, subscriptionRepo, permissionService);
    }

    @Test
    void blankEventTypeReturnsInvalidParam() {
        ServiceResult<Void> result = service.subscribe(ctx, "", ACTION_CODE, IDEMPOTENCY_KEY);

        assertEquals(ErrorCodes.INVALID_PARAM, result.getCode());
    }

    @Test
    void blankIdempotencyKeyReturnsInvalidParam() {
        ServiceResult<Void> result = service.subscribe(ctx, EVENT_TYPE, ACTION_CODE, "");

        assertEquals(ErrorCodes.INVALID_PARAM, result.getCode());
    }

    @Test
    void nullIdempotencyKeyReturnsInvalidParam() {
        ServiceResult<Void> result = service.subscribe(ctx, EVENT_TYPE, ACTION_CODE, null);

        assertEquals(ErrorCodes.INVALID_PARAM, result.getCode());
    }

    @Test
    void nullEventTypeReturnsInvalidParam() {
        ServiceResult<Void> result = service.subscribe(ctx, null, ACTION_CODE, IDEMPOTENCY_KEY);

        assertEquals(ErrorCodes.INVALID_PARAM, result.getCode());
    }

    @Test
    void providerNullThrowsIllegalState() {
        when(pluginManagerProvider.getIfAvailable()).thenReturn(null);

        assertThrows(IllegalStateException.class,
            () -> service.subscribe(ctx, EVENT_TYPE, ACTION_CODE, IDEMPOTENCY_KEY));
        verifyNoInteractions(idempotencyRepo, subscriptionRepo);
    }

    @Test
    void actionNotFoundReturnsStateConflict() {
        when(pluginManagerService.findActionHandler(PLUGIN_ID, ACTION_CODE))
            .thenReturn(Optional.empty());

        ServiceResult<Void> result = service.subscribe(ctx, EVENT_TYPE, ACTION_CODE, IDEMPOTENCY_KEY);

        assertEquals(ErrorCodes.STATE_CONFLICT, result.getCode());
        verify(idempotencyRepo, never()).insert(anyString(), anyString(), anyString(), anyString());
        verify(subscriptionRepo, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void nonIdempotentActionCannotSubscribeToEvents() {
        when(pluginActionHandler.getDescriptor()).thenReturn(new ActionDescriptor(
            ACTION_CODE,
            "test",
            "test",
            Map.of(),
            Map.of(),
            "EVENT_SUBSCRIBE",
            Duration.ofSeconds(5),
            false
        ));

        ServiceResult<Void> result = service.subscribe(ctx, EVENT_TYPE, ACTION_CODE, IDEMPOTENCY_KEY);

        assertEquals(ErrorCodes.STATE_CONFLICT, result.getCode());
        verifyNoInteractions(idempotencyRepo, subscriptionRepo);
    }

    @Test
    void nonDuplicateKeyExceptionPropagates() {
        doThrow(new RuntimeException("db error"))
            .when(idempotencyRepo).insert(anyString(), anyString(), anyString(), anyString());

        assertThrows(RuntimeException.class,
            () -> service.subscribe(ctx, EVENT_TYPE, ACTION_CODE, IDEMPOTENCY_KEY));
    }

    @Test
    void duplicateKeyWithFindEmptyThrowsIllegalState() {
        doThrow(new DuplicateKeyException("dup"))
            .when(idempotencyRepo).insert(eq(PLUGIN_ID), eq("EVENT_SUBSCRIBE"), eq(IDEMPOTENCY_KEY), anyString());
        when(idempotencyRepo.find(PLUGIN_ID, "EVENT_SUBSCRIBE", IDEMPOTENCY_KEY))
            .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
            () -> service.subscribe(ctx, EVENT_TYPE, ACTION_CODE, IDEMPOTENCY_KEY));
    }

    @Test
    void unknownEventDoesNotWriteIdempotencyOrSubscription() {
        ServiceResult<Void> result = service.subscribe(ctx, "UNKNOWN_EVENT", ACTION_CODE, IDEMPOTENCY_KEY);

        assertEquals(ErrorCodes.INVALID_PARAM, result.getCode());
        verifyNoInteractions(idempotencyRepo, subscriptionRepo);
    }

    @Test
    void nullCtxDoesNotWriteIdempotencyOrSubscription() {
        ServiceResult<Void> result = service.subscribe(null, EVENT_TYPE, ACTION_CODE, IDEMPOTENCY_KEY);

        assertEquals(ErrorCodes.INVALID_PARAM, result.getCode());
        verifyNoInteractions(idempotencyRepo, subscriptionRepo);
    }

    @Test
    void hashIs64HexAndStable() {
        String h1 = DefaultEventService.computeHash("evt", "action");
        String h2 = DefaultEventService.computeHash("evt", "action");
        assertEquals(h1, h2);
        assertEquals(64, h1.length());
        assertTrue(h1.matches("[0-9a-f]{64}"));
    }

    @Test
    void differentActionsProduceDifferentHashes() {
        String h1 = DefaultEventService.computeHash("evt", "action1");
        String h2 = DefaultEventService.computeHash("evt", "action2");
        assertNotEquals(h1, h2);
    }

    @Test
    void hashCapturedFromInsertIsValid64Hex() {
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        service.subscribe(ctx, EVENT_TYPE, ACTION_CODE, IDEMPOTENCY_KEY);

        verify(idempotencyRepo).insert(eq(PLUGIN_ID), eq("EVENT_SUBSCRIBE"), eq(IDEMPOTENCY_KEY), captor.capture());
        String hash = captor.getValue();
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void lengthPrefixEncodingPreventsAmbiguity() {
        // "ab"+"c" must differ from "a"+"bc"
        String h1 = DefaultEventService.computeHash("ab", "c");
        String h2 = DefaultEventService.computeHash("a", "bc");
        assertNotEquals(h1, h2);
    }

    @Test
    void subscribeAndUnsubscribeHaveTransactionalAnnotation() throws NoSuchMethodException {
        Method subscribe = DefaultEventService.class.getMethod("subscribe",
            InvocationContext.class, String.class, String.class, String.class);
        Method unsubscribe = DefaultEventService.class.getMethod("unsubscribe",
            InvocationContext.class, String.class, String.class);

        assertNotNull(subscribe.getAnnotation(Transactional.class), "subscribe 缺少 @Transactional");
        assertNotNull(unsubscribe.getAnnotation(Transactional.class), "unsubscribe 缺少 @Transactional");
    }
}
