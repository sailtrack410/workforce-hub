package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.repository.PluginRepository;
import com.workforcehub.plugin.repository.PluginRow;
import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.sdk.ActionDescriptor;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PluginManagerService 生命周期安全测试。
 * Mock Guard，精确验证 disable/uninstall/enable 的调用顺序和状态写入。
 * 真实 Guard 覆盖 0 秒排空超时边界。
 */
@ExtendWith(MockitoExtension.class)
class PluginManagerLifecycleSafetyTest {

    @Mock private WorkforcePluginManager pf4jManager;
    @Mock private PluginRepository pluginRepository;
    @Mock private PluginSignatureVerifier signatureVerifier;
    @Mock private PluginVersionService versionService;
    @Mock private PluginFlywayMigrator flywayMigrator;
    @Mock private PluginLifecycleGuard lifecycleGuard;

    private PluginManagerService service;
    private static final String PID = "test-plugin";

    @BeforeEach
    void setUp() {
        service = new PluginManagerService(
            pf4jManager,
            pluginRepository,
            signatureVerifier,
            versionService,
            flywayMigrator,
            lifecycleGuard
        );

        PluginRow row = new PluginRow();
        row.setPluginId(PID);
        row.setStatus(PluginStatus.ACTIVE);
        lenient().when(pluginRepository.findById(PID)).thenReturn(Optional.of(row));
        PluginWrapper wrapper = mock(PluginWrapper.class);
        lenient().when(wrapper.getPluginState()).thenReturn(PluginState.STARTED);
        lenient().when(pf4jManager.getPlugin(PID)).thenReturn(wrapper);
    }

    @Test
    void restore_shouldRunOnApplicationReady() throws Exception {
        EventListener listener = PluginManagerService.class
            .getMethod("restorePlugins")
            .getAnnotation(EventListener.class);

        assertNotNull(listener);
        assertArrayEquals(new Class<?>[]{ApplicationStartedEvent.class}, listener.value());
    }

    // ---- disable ----

    @Test
    void disable_shouldRejectLoadedDependentsBeforeClosingGate() {
        when(pf4jManager.getDependentPluginIds(PID)).thenReturn(List.of("dependent-plugin"));

        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> service.disable(PID)
        );

        assertTrue(thrown.getMessage().contains("dependent-plugin"));
        verifyNoInteractions(lifecycleGuard);
        verify(pluginRepository, never()).updateStatus(anyString(), any(), anyString());
        verify(pluginRepository, never()).updateStatus(anyString(), any());
        verify(pf4jManager, never()).stopPlugin(anyString());
    }

    @Test
    void disable_shouldNotCallStopPlugin_whenDrainReturnsFalse() {
        when(lifecycleGuard.awaitDrain(PID)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> service.disable(PID));

        InOrder order = inOrder(lifecycleGuard, pluginRepository, pf4jManager);
        order.verify(lifecycleGuard).beginDisable(PID);
        order.verify(pluginRepository).updateStatus(PID, PluginStatus.DISABLING);
        order.verify(lifecycleGuard).awaitDrain(PID);
        order.verify(pluginRepository).updateStatus(PID, PluginStatus.ERROR, "排空超时或中断，需重启");
        verify(pf4jManager, never()).stopPlugin(any());
        // 不应写 STOPPED
        verify(pluginRepository, never()).updateStatus(PID, PluginStatus.STOPPED);
    }

    @Test
    void disable_shouldMarkRestartRequiredAndKeepGateClosed_whenRealGuardDrainTimesOut() {
        PluginLifecycleGuard realGuard = new PluginLifecycleGuard(0);
        PluginManagerService realGuardService = new PluginManagerService(
            pf4jManager,
            pluginRepository,
            signatureVerifier,
            versionService,
            flywayMigrator,
            realGuard
        );
        realGuard.enable(PID);
        assertTrue(realGuard.tryAcquire(PID));

        RuntimeException thrown = assertThrows(
            RuntimeException.class,
            () -> realGuardService.disable(PID)
        );

        assertAll(
            () -> assertTrue(thrown.getMessage().contains("需重启")),
            () -> verify(pluginRepository, atLeastOnce()).updateStatus(
                eq(PID),
                eq(PluginStatus.ERROR),
                contains("排空超时或中断，需重启")
            ),
            () -> assertFalse(realGuard.tryAcquire(PID), "排空失败后入口应保持关闭"),
            () -> assertTrue(realGuard.hasSlot(PID), "排空失败后不得移除守卫"),
            () -> assertEquals(1, realGuard.getActiveCount(PID))
        );
        verify(pf4jManager, never()).stopPlugin(any());
        verify(pf4jManager, never()).unloadPlugin(any());
        realGuard.release(PID);
    }

    @Test
    void disable_shouldRecordError_whenDisablingStatusWriteFails() {
        RuntimeException dbError = new RuntimeException("disabling status write failed");
        doThrow(dbError).when(pluginRepository)
            .updateStatus(PID, PluginStatus.DISABLING);

        assertThrows(RuntimeException.class, () -> service.disable(PID));

        verify(lifecycleGuard).beginDisable(PID);
        verify(pluginRepository).updateStatus(PID, PluginStatus.ERROR, dbError.getMessage());
        verify(lifecycleGuard, never()).awaitDrain(PID);
        verify(pf4jManager, never()).stopPlugin(PID);
    }

    @Test
    void disable_shouldMarkErrorAndThrow_whenStopPluginReturnsNonStopped() {
        when(lifecycleGuard.awaitDrain(PID)).thenReturn(true);
        when(pf4jManager.stopPlugin(PID)).thenReturn(PluginState.FAILED);

        assertThrows(RuntimeException.class, () -> service.disable(PID));

        verify(pluginRepository).updateStatus(PID, PluginStatus.ERROR, "停止失败: FAILED");
        verify(pluginRepository, never()).updateStatus(PID, PluginStatus.STOPPED);
    }

    @Test
    void disable_shouldWriteSTOPPED_onlyWhenStopSucceeds() {
        when(lifecycleGuard.awaitDrain(PID)).thenReturn(true);
        when(pf4jManager.stopPlugin(PID)).thenReturn(PluginState.STOPPED);

        assertDoesNotThrow(() -> service.disable(PID));

        verify(pluginRepository).updateStatus(PID, PluginStatus.STOPPED);
    }

    // ---- uninstall ----

    @Test
    void uninstall_shouldRejectLoadedDependentsBeforeClosingGate() {
        when(pf4jManager.getDependentPluginIds(PID)).thenReturn(List.of("dependent-plugin"));

        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> service.uninstall(PID)
        );

        assertTrue(thrown.getMessage().contains("dependent-plugin"));
        verifyNoInteractions(lifecycleGuard);
        verify(pluginRepository, never()).updateStatus(anyString(), any(), anyString());
        verify(pluginRepository, never()).updateStatus(anyString(), any());
        verify(pf4jManager, never()).stopPlugin(anyString());
        verify(pf4jManager, never()).unloadPlugin(anyString());
    }

    @Test
    void uninstall_shouldNotCallStopUnload_whenDrainReturnsFalse() {
        when(lifecycleGuard.awaitDrain(PID)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> service.uninstall(PID));

        verify(pf4jManager, never()).stopPlugin(any());
        verify(pf4jManager, never()).unloadPlugin(any());
        verify(pluginRepository, never()).updateStatus(eq(PID), eq(PluginStatus.UNINSTALLED), any());
        verify(lifecycleGuard, never()).remove(PID);
    }

    @Test
    void uninstall_shouldMarkRestartRequiredAndKeepGateClosed_whenRealGuardDrainTimesOut() {
        PluginLifecycleGuard realGuard = new PluginLifecycleGuard(0);
        PluginManagerService realGuardService = new PluginManagerService(
            pf4jManager,
            pluginRepository,
            signatureVerifier,
            versionService,
            flywayMigrator,
            realGuard
        );
        realGuard.enable(PID);
        assertTrue(realGuard.tryAcquire(PID));

        RuntimeException thrown = assertThrows(
            RuntimeException.class,
            () -> realGuardService.uninstall(PID)
        );

        assertAll(
            () -> assertTrue(thrown.getMessage().contains("需重启")),
            () -> verify(pluginRepository, atLeastOnce()).updateStatus(
                eq(PID),
                eq(PluginStatus.ERROR),
                contains("排空超时或中断，需重启")
            ),
            () -> assertFalse(realGuard.tryAcquire(PID), "排空失败后入口应保持关闭"),
            () -> assertTrue(realGuard.hasSlot(PID), "排空失败后不得移除守卫"),
            () -> assertEquals(1, realGuard.getActiveCount(PID))
        );
        verify(pf4jManager, never()).stopPlugin(any());
        verify(pf4jManager, never()).unloadPlugin(any());
        realGuard.release(PID);
    }

    @Test
    void uninstall_shouldRecordError_whenDisablingStatusWriteFails() {
        RuntimeException dbError = new RuntimeException("disabling status write failed");
        doThrow(dbError).when(pluginRepository)
            .updateStatus(PID, PluginStatus.DISABLING);

        assertThrows(RuntimeException.class, () -> service.uninstall(PID));

        verify(lifecycleGuard).beginDisable(PID);
        verify(pluginRepository).updateStatus(PID, PluginStatus.ERROR, dbError.getMessage());
        verify(lifecycleGuard, never()).awaitDrain(PID);
        verify(pf4jManager, never()).stopPlugin(PID);
        verify(pf4jManager, never()).unloadPlugin(PID);
        verify(lifecycleGuard, never()).remove(PID);
    }

    @Test
    void uninstall_shouldMarkErrorAndNotRemoveGuard_whenStopThrows() {
        when(lifecycleGuard.awaitDrain(PID)).thenReturn(true);
        when(pf4jManager.stopPlugin(PID)).thenThrow(new RuntimeException("stop boom"));

        assertThrows(RuntimeException.class, () -> service.uninstall(PID));

        verify(pluginRepository).updateStatus(PID, PluginStatus.ERROR, "停止异常: stop boom");
        verify(pf4jManager, never()).unloadPlugin(any());
        verify(pluginRepository, never()).updateStatus(eq(PID), eq(PluginStatus.UNINSTALLED), any());
        verify(lifecycleGuard, never()).remove(PID);
    }

    @Test
    void uninstall_shouldMarkErrorAndNotRemoveGuard_whenUnloadThrows() {
        when(lifecycleGuard.awaitDrain(PID)).thenReturn(true);
        when(pf4jManager.stopPlugin(PID)).thenReturn(PluginState.STOPPED);
        when(pf4jManager.unloadPlugin(PID)).thenThrow(new RuntimeException("unload boom"));

        assertThrows(RuntimeException.class, () -> service.uninstall(PID));

        verify(pluginRepository).updateStatus(PID, PluginStatus.ERROR, "卸载异常: unload boom");
        verify(pluginRepository, never()).updateStatus(eq(PID), eq(PluginStatus.UNINSTALLED), any());
        verify(lifecycleGuard, never()).remove(PID);
    }

    @Test
    void uninstall_shouldMarkErrorAndNotRemoveGuard_whenUnloadReturnsFalse() {
        when(lifecycleGuard.awaitDrain(PID)).thenReturn(true);
        when(pf4jManager.stopPlugin(PID)).thenReturn(PluginState.STOPPED);
        when(pf4jManager.unloadPlugin(PID)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> service.uninstall(PID));

        verify(pluginRepository).updateStatus(PID, PluginStatus.ERROR, "卸载失败");
        verify(pluginRepository, never()).updateStatus(eq(PID), eq(PluginStatus.UNINSTALLED), any());
        verify(lifecycleGuard, never()).remove(PID);
    }

    @Test
    void uninstall_shouldRemoveGuardAndWriteUninstalled_onlyWhenStopUnloadSucceed() {
        when(lifecycleGuard.awaitDrain(PID)).thenReturn(true);
        when(pf4jManager.stopPlugin(PID)).thenReturn(PluginState.STOPPED);
        when(pf4jManager.unloadPlugin(PID)).thenReturn(true);

        assertDoesNotThrow(() -> service.uninstall(PID));

        InOrder order = inOrder(lifecycleGuard, pluginRepository);
        order.verify(lifecycleGuard).beginDisable(PID);
        order.verify(pluginRepository).updateStatus(PID, PluginStatus.DISABLING);
        order.verify(lifecycleGuard).awaitDrain(PID);
        order.verify(lifecycleGuard).remove(PID);
        order.verify(pluginRepository).updateStatus(PID, PluginStatus.UNINSTALLED, "已卸载");
    }

    @Test
    void uninstall_shouldUnloadWithoutStop_whenPluginWasNeverStarted() {
        PluginWrapper wrapper = pf4jManager.getPlugin(PID);
        when(wrapper.getPluginState()).thenReturn(PluginState.RESOLVED);
        when(lifecycleGuard.awaitDrain(PID)).thenReturn(true);
        when(pf4jManager.unloadPlugin(PID)).thenReturn(true);

        assertDoesNotThrow(() -> service.uninstall(PID));

        verify(pf4jManager, never()).stopPlugin(PID);
        verify(pf4jManager).unloadPlugin(PID);
        verify(pluginRepository).updateStatus(PID, PluginStatus.UNINSTALLED, "已卸载");
    }

    @Test
    void uninstall_shouldRetryFinalStatusWriteWithoutRepeatingPf4jUnload() {
        PluginWrapper wrapper = mock(PluginWrapper.class);
        when(wrapper.getPluginState()).thenReturn(PluginState.STARTED);
        when(pf4jManager.getPlugin(PID))
            .thenReturn(wrapper)
            .thenReturn((PluginWrapper) null);
        when(lifecycleGuard.awaitDrain(PID)).thenReturn(true);
        when(pf4jManager.stopPlugin(PID)).thenReturn(PluginState.STOPPED);
        when(pf4jManager.unloadPlugin(PID)).thenReturn(true);
        doReturn(LocalDateTime.of(2026, 7, 30, 10, 0))
            .when(pluginRepository)
            .updateStatus(PID, PluginStatus.DISABLING);
        RuntimeException dbError = new RuntimeException("uninstalled status write failed");
        doThrow(dbError)
            .doReturn(LocalDateTime.of(2026, 7, 30, 10, 1))
            .when(pluginRepository)
            .updateStatus(PID, PluginStatus.UNINSTALLED, "已卸载");

        assertSame(dbError, assertThrows(RuntimeException.class, () -> service.uninstall(PID)));
        assertDoesNotThrow(() -> service.uninstall(PID));

        verify(lifecycleGuard).beginDisable(PID);
        verify(lifecycleGuard).awaitDrain(PID);
        verify(pf4jManager).stopPlugin(PID);
        verify(pf4jManager).unloadPlugin(PID);
        verify(lifecycleGuard, times(2)).remove(PID);
        verify(pluginRepository, times(2))
            .updateStatus(PID, PluginStatus.UNINSTALLED, "已卸载");
    }

    // ---- enable ----

    @Test
    void enable_shouldNotOpenGuard_whenStartFails() {
        when(pf4jManager.startPlugin(PID)).thenReturn(PluginState.FAILED);

        assertThrows(RuntimeException.class, () -> service.enable(PID));

        verify(pluginRepository).updateStatus(PID, PluginStatus.ERROR, "插件启动失败: FAILED");
        verify(pluginRepository, never()).updateStatus(PID, PluginStatus.ACTIVE);
        verify(lifecycleGuard, never()).enable(PID);
    }

    @Test
    void enable_shouldPreserveStartFailure_whenErrorStatusWriteFails() {
        RuntimeException dbError = new RuntimeException("error status write failed");
        when(pf4jManager.startPlugin(PID)).thenReturn(PluginState.FAILED);
        doThrow(dbError).when(pluginRepository)
            .updateStatus(PID, PluginStatus.ERROR, "插件启动失败: FAILED");

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.enable(PID));

        assertEquals("插件启动失败: FAILED", thrown.getMessage());
        assertEquals(List.of(dbError), List.of(thrown.getSuppressed()));
        verify(lifecycleGuard, never()).enable(PID);
    }

    @Test
    void enable_shouldStopPluginAndRethrowOriginal_whenFirstStartDbUpdateThrows() {
        RuntimeException dbError = new RuntimeException("db boom");
        when(pf4jManager.getPlugin(PID).getPluginState()).thenReturn(PluginState.CREATED);
        when(pf4jManager.startPlugin(PID)).thenReturn(PluginState.STARTED);
        doThrow(dbError).when(pluginRepository).updateStatus(PID, PluginStatus.ACTIVE);
        when(pf4jManager.stopPlugin(PID)).thenReturn(PluginState.STOPPED);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.enable(PID));

        assertSame(dbError, thrown);
        InOrder order = inOrder(pf4jManager, pluginRepository);
        order.verify(pf4jManager).startPlugin(PID);
        order.verify(pluginRepository).updateStatus(PID, PluginStatus.ACTIVE);
        order.verify(pf4jManager).stopPlugin(PID);
        verify(lifecycleGuard, never()).enable(PID);
    }

    @Test
    void enable_shouldNotStopAlreadyStartedPlugin_whenDbUpdateThrows() {
        RuntimeException dbError = new RuntimeException("db boom");
        when(pf4jManager.getPlugin(PID).getPluginState()).thenReturn(PluginState.STARTED);
        when(pf4jManager.startPlugin(PID)).thenReturn(PluginState.STARTED);
        doThrow(dbError).when(pluginRepository).updateStatus(PID, PluginStatus.ACTIVE);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.enable(PID));

        assertSame(dbError, thrown);
        assertEquals(0, thrown.getSuppressed().length);
        verify(pf4jManager, never()).stopPlugin(PID);
        verify(lifecycleGuard, never()).enable(PID);
    }

    @Test
    void enable_shouldSuppressRollbackFailure_whenStopReturnsNonStopped() {
        RuntimeException dbError = new RuntimeException("db boom");
        when(pf4jManager.getPlugin(PID).getPluginState()).thenReturn(PluginState.CREATED);
        when(pf4jManager.startPlugin(PID)).thenReturn(PluginState.STARTED);
        doThrow(dbError).when(pluginRepository).updateStatus(PID, PluginStatus.ACTIVE);
        when(pf4jManager.stopPlugin(PID)).thenReturn(PluginState.FAILED);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.enable(PID));

        assertSame(dbError, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertTrue(thrown.getSuppressed()[0].getMessage().contains("FAILED"));
        verify(lifecycleGuard, never()).enable(PID);
    }

    @Test
    void enable_shouldSuppressRollbackFailure_whenStopThrows() {
        RuntimeException dbError = new RuntimeException("db boom");
        RuntimeException stopError = new RuntimeException("stop boom");
        when(pf4jManager.getPlugin(PID).getPluginState()).thenReturn(PluginState.CREATED);
        when(pf4jManager.startPlugin(PID)).thenReturn(PluginState.STARTED);
        doThrow(dbError).when(pluginRepository).updateStatus(PID, PluginStatus.ACTIVE);
        when(pf4jManager.stopPlugin(PID)).thenThrow(stopError);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.enable(PID));

        assertSame(dbError, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(stopError, thrown.getSuppressed()[0].getCause());
        verify(lifecycleGuard, never()).enable(PID);
    }

    @Test
    void enable_shouldOpenGuard_afterDbUpdateSucceeds() {
        when(pf4jManager.startPlugin(PID)).thenReturn(PluginState.STARTED);

        assertDoesNotThrow(() -> service.enable(PID));

        InOrder order = inOrder(pf4jManager, pluginRepository, lifecycleGuard);
        order.verify(pf4jManager).startPlugin(PID);
        order.verify(pluginRepository).updateStatus(PID, PluginStatus.ACTIVE);
        order.verify(lifecycleGuard).enable(PID);
    }

    // ---- 并发生命周期串行化 ----

    @Test
    void disable_shouldBlockEnable_untilDrainCompletes() throws Exception {
        CountDownLatch drainStarted = new CountDownLatch(1);
        CountDownLatch drainRelease = new CountDownLatch(1);
        CountDownLatch startPluginCalled = new CountDownLatch(1);

        when(lifecycleGuard.awaitDrain(PID)).thenAnswer(inv -> {
            drainStarted.countDown();
            assertTrue(drainRelease.await(5, TimeUnit.SECONDS), "drainRelease 应在 5 秒内到达");
            return true;
        });
        when(pf4jManager.stopPlugin(PID)).thenReturn(PluginState.STOPPED);
        when(pf4jManager.startPlugin(PID)).thenAnswer(inv -> {
            startPluginCalled.countDown();
            return PluginState.STARTED;
        });

        CompletableFuture<Void> disableTask = CompletableFuture.runAsync(() -> service.disable(PID));
        assertTrue(drainStarted.await(2, TimeUnit.SECONDS), "disable 应进入 awaitDrain");

        CompletableFuture<Void> enableTask = CompletableFuture.runAsync(() -> service.enable(PID));

        assertFalse(startPluginCalled.await(300, TimeUnit.MILLISECONDS),
            "释放 drain 前 startPlugin 不应被调用");

        drainRelease.countDown();

        disableTask.get(5, TimeUnit.SECONDS);
        enableTask.get(5, TimeUnit.SECONDS);
        assertTrue(startPluginCalled.await(0, TimeUnit.MILLISECONDS), "startPlugin 应已调用");

        InOrder order = inOrder(pf4jManager);
        order.verify(pf4jManager).stopPlugin(PID);
        order.verify(pf4jManager).startPlugin(PID);
    }

    // ---- findActionHandler 不因 DISABLING 拒绝 ----

    @Test
    void findActionHandler_shouldReturnHandler_evenWhenDbStatusIsDisabling() {
        PluginRow disabling = new PluginRow();
        disabling.setPluginId(PID);
        disabling.setStatus(PluginStatus.DISABLING);
        lenient().when(pluginRepository.findById(PID)).thenReturn(Optional.of(disabling));

        PluginActionHandler mockHandler = mock(PluginActionHandler.class);
        ActionDescriptor descriptor = new ActionDescriptor(
            "test.action", "测试", "desc", Map.of(), Map.of(), "TEST_CAP", Duration.ofSeconds(5), true);
        when(mockHandler.getDescriptor()).thenReturn(descriptor);
        when(pf4jManager.getExtensions(PluginActionHandler.class, PID))
            .thenReturn(List.of(mockHandler));

        Optional<PluginActionHandler> result = service.findActionHandler(PID, "test.action");

        assertTrue(result.isPresent(), "guard 已 acquire 时 findActionHandler 应返回 handler");
        assertSame(mockHandler, result.get());
    }
}
