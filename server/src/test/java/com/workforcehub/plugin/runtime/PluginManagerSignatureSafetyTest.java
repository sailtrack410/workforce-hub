package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.repository.PluginRepository;
import com.workforcehub.plugin.repository.PluginDependencySpec;
import com.workforcehub.plugin.repository.PluginRow;
import com.workforcehub.plugin.repository.PluginStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginManagerSignatureSafetyTest {

    private static final String PLUGIN_ID = "signed-plugin";
    private static final String SIGNER_ID = "official";
    private static final String VERIFIED_SHA256 = "a".repeat(64);
    private static final String SIGNED_FRONTEND_SRI = "sha384-" + "A".repeat(64);

    private enum SignedUiField {
        TYPE,
        PROVIDER,
        FRONTEND_ENTRY,
        FRONTEND_SHA384
    }

    @TempDir
    Path tempDir;

    @Mock private WorkforcePluginManager pf4jManager;
    @Mock private PluginRepository pluginRepository;
    @Mock private PluginSignatureVerifier signatureVerifier;
    @Mock private PluginVersionService versionService;
    @Mock private PluginFlywayMigrator flywayMigrator;
    @Mock private PluginLifecycleGuard lifecycleGuard;

    private PluginManagerService service;

    @BeforeEach
    void setUp() {
        lenient().when(pf4jManager.getPluginsRoot())
            .thenReturn(tempDir.resolve("plugins").toAbsolutePath());
        service = new PluginManagerService(
            pf4jManager,
            pluginRepository,
            signatureVerifier,
            versionService,
            flywayMigrator,
            lifecycleGuard
        );
    }

    @Test
    void installShouldVerifyBeforeLoadAndPersistVerifiedSignatureData() throws Exception {
        Path jarPath = Files.writeString(tempDir.resolve("signed-plugin.jar"), "signed-content");
        byte[] signature = {1, 2, 3};
        PluginRow metadata = metadata(signature);
        metadata.setJarSha256("client-supplied-hash");
        metadata.setVersion("client-supplied-version");
        metadata.setSdkVersion("client-supplied-sdk-version");
        metadata.setMinHostVersion("client-supplied-min-host-version");
        metadata.setMaxHostVersion("client-supplied-max-host-version");
        metadata.setJavaVersion("99");
        PluginWrapper wrapper = wrapper("1.0.0");

        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(any(Path.class))).thenReturn(versionDescriptor());
        when(pf4jManager.loadPlugin(any(Path.class))).thenReturn(PLUGIN_ID);
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(wrapper);

        PluginRow installed = service.install(jarPath, metadata);

        ArgumentCaptor<PluginRow> rowCaptor = ArgumentCaptor.forClass(PluginRow.class);
        verify(pluginRepository).insert(rowCaptor.capture());
        assertEquals(VERIFIED_SHA256, rowCaptor.getValue().getJarSha256());
        assertEquals(SIGNER_ID, rowCaptor.getValue().getSignerId());
        assertArrayEquals(signature, rowCaptor.getValue().getSignature());
        assertEquals("1.0.0", rowCaptor.getValue().getVersion());
        assertEquals("0.1.0", rowCaptor.getValue().getSdkVersion());
        assertEquals("0.1.0", rowCaptor.getValue().getMinHostVersion());
        assertEquals("0.2.0", rowCaptor.getValue().getMaxHostVersion());
        assertEquals("17", rowCaptor.getValue().getJavaVersion());
        assertEquals(VERIFIED_SHA256, installed.getJarSha256());
        Path snapshotPath = Path.of(installed.getJarPath());

        InOrder order = inOrder(
            signatureVerifier,
            versionService,
            pf4jManager,
            flywayMigrator,
            pluginRepository
        );
        order.verify(signatureVerifier).verify(eq(snapshotPath), eq(SIGNER_ID), any(byte[].class));
        order.verify(versionService).checkCompatibility(snapshotPath);
        order.verify(pf4jManager).loadPlugin(snapshotPath);
        order.verify(flywayMigrator).migrate(PLUGIN_ID, snapshotPath);
        order.verify(pluginRepository).insert(any(PluginRow.class));
    }

    @Test
    void installShouldIgnoreUnsignedMetadataForSignedUiFields() throws Exception {
        Path jarPath = Files.writeString(tempDir.resolve("signed-ui-plugin.jar"), "signed-content");
        PluginRow metadata = metadata(new byte[]{1, 2, 3});
        metadata.setType("FUNCTIONAL");
        metadata.setProvider("forged-provider");
        metadata.setFrontendEntry("/plugins/forged/index.js");
        metadata.setFrontendSha384("sha384-" + "B".repeat(64));
        PluginWrapper wrapper = wrapper("1.0.0");

        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(any(Path.class))).thenReturn(uiVersionDescriptor());
        when(pf4jManager.loadPlugin(any(Path.class))).thenReturn(PLUGIN_ID);
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(wrapper);

        PluginRow installed = service.install(jarPath, metadata);

        ArgumentCaptor<PluginRow> rowCaptor = ArgumentCaptor.forClass(PluginRow.class);
        verify(pluginRepository).insert(rowCaptor.capture());
        PluginRow inserted = rowCaptor.getValue();
        assertEquals("UI", installed.getType());
        assertEquals("Workforce Hub", installed.getProvider());
        assertEquals("/plugins/" + PLUGIN_ID + "/index.js", installed.getFrontendEntry());
        assertEquals(SIGNED_FRONTEND_SRI, installed.getFrontendSha384());
        assertEquals("UI", inserted.getType());
        assertEquals("Workforce Hub", inserted.getProvider());
        assertEquals("/plugins/" + PLUGIN_ID + "/index.js", inserted.getFrontendEntry());
        assertEquals(SIGNED_FRONTEND_SRI, inserted.getFrontendSha384());
    }

    @Test
    void installShouldUseHostOwnedSnapshotAfterSourceJarIsReplaced() throws Exception {
        Path pluginRoot = tempDir.resolve("plugins").toAbsolutePath();
        Path sourceJar = Files.writeString(tempDir.resolve("uploaded-plugin.jar"), "signed-content");
        PluginRow metadata = metadata(new byte[]{1, 2, 3});
        PluginWrapper wrapper = wrapper("1.0.0");
        AtomicReference<Path> verifiedPath = new AtomicReference<>();

        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenAnswer(invocation -> {
                Path snapshotPath = invocation.getArgument(0);
                verifiedPath.set(snapshotPath);
                assertNotEquals(sourceJar.toAbsolutePath(), snapshotPath);
                assertTrue(snapshotPath.startsWith(pluginRoot));
                assertEquals("signed-content", Files.readString(snapshotPath));
                Files.writeString(sourceJar, "replaced-content");
                return VERIFIED_SHA256;
            });
        when(versionService.checkCompatibility(any(Path.class))).thenAnswer(invocation -> {
            Path snapshotPath = invocation.getArgument(0);
            assertEquals(verifiedPath.get(), snapshotPath);
            assertEquals("signed-content", Files.readString(snapshotPath));
            return versionDescriptor();
        });
        when(pf4jManager.loadPlugin(any(Path.class))).thenReturn(PLUGIN_ID);
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(wrapper);

        PluginRow installed = service.install(sourceJar, metadata);

        Path snapshotPath = Path.of(installed.getJarPath());
        assertNotNull(verifiedPath.get());
        assertEquals(verifiedPath.get(), snapshotPath);
        assertEquals("replaced-content", Files.readString(sourceJar));
        assertEquals("signed-content", Files.readString(snapshotPath));
        verify(versionService).checkCompatibility(snapshotPath);
        verify(pf4jManager).loadPlugin(snapshotPath);
        verify(flywayMigrator).migrate(PLUGIN_ID, snapshotPath);
    }

    @Test
    void installShouldDeleteUnpersistedSnapshotWhenMigrationFails() throws Exception {
        Path sourceJar = Files.writeString(tempDir.resolve("failed-plugin.jar"), "signed-content");
        PluginRow metadata = metadata(new byte[]{1, 2, 3});
        PluginWrapper wrapper = wrapper("1.0.0");
        AtomicReference<Path> verifiedPath = new AtomicReference<>();
        PluginFlywayMigrator.PluginMigrationException migrationError =
            new PluginFlywayMigrator.PluginMigrationException("migration failed", null);

        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenAnswer(invocation -> {
                Path snapshotPath = invocation.getArgument(0);
                verifiedPath.set(snapshotPath);
                return VERIFIED_SHA256;
            });
        when(versionService.checkCompatibility(any(Path.class))).thenReturn(versionDescriptor());
        when(pf4jManager.loadPlugin(any(Path.class))).thenReturn(PLUGIN_ID);
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(wrapper);
        doThrow(migrationError).when(flywayMigrator).migrate(eq(PLUGIN_ID), any(Path.class));
        when(pf4jManager.unloadPluginWithoutDependents(PLUGIN_ID)).thenReturn(true);

        assertThrows(
            PluginFlywayMigrator.PluginMigrationException.class,
            () -> service.install(sourceJar, metadata)
        );

        assertNotNull(verifiedPath.get());
        assertFalse(Files.exists(verifiedPath.get()));
        assertTrue(Files.exists(sourceJar));
        verify(pluginRepository, never()).insert(any(PluginRow.class));
    }

    @Test
    void installShouldNotLeaveEmptySnapshotWhenSourceCopyFails() throws Exception {
        Path snapshotDirectory = tempDir.resolve("plugins").resolve(".snapshots").toAbsolutePath();

        assertThrows(
            IllegalStateException.class,
            () -> service.install(tempDir.resolve("missing-plugin.jar"), metadata(new byte[]{1}))
        );

        assertTrue(Files.isDirectory(snapshotDirectory));
        try (var snapshots = Files.list(snapshotDirectory)) {
            assertEquals(0, snapshots.count());
        }
        verify(signatureVerifier, never()).verify(any(Path.class), anyString(), any(byte[].class));
        verify(pf4jManager, never()).loadPlugin(any(Path.class));
        verify(pluginRepository, never()).insert(any(PluginRow.class));
    }

    @Test
    void installShouldRejectOversizedJarBeforeVerification() throws Exception {
        Path jarPath = tempDir.resolve("oversized-plugin.jar");
        try (FileChannel channel = FileChannel.open(
                jarPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE)) {
            channel.position(PluginManagerService.MAX_JAR_BYTES);
            channel.write(ByteBuffer.wrap(new byte[] {1}));
        }

        assertThrows(
            PluginManagerService.PluginPackageTooLargeException.class,
            () -> service.install(jarPath, metadata(new byte[] {1}))
        );

        verify(signatureVerifier, never()).verify(any(Path.class), anyString(), any(byte[].class));
        verify(pf4jManager, never()).loadPlugin(any(Path.class));
        assertSnapshotDirectoryEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = PluginStatus.class, names = "UNINSTALLED", mode = EnumSource.Mode.EXCLUDE)
    void installShouldRejectExistingPluginBeforePf4jLoadAndMigration(
            PluginStatus existingStatus) throws Exception {
        Path jarPath = Files.writeString(tempDir.resolve("existing-plugin.jar"), "signed-content");
        Path previousJarPath = Files.writeString(tempDir.resolve("previous-plugin.jar"), "previous");
        PluginRow metadata = metadata(new byte[]{1, 2, 3});
        PluginRow existing = storedPlugin(previousJarPath, "b".repeat(64));
        existing.setStatus(existingStatus);

        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(any(Path.class))).thenReturn(versionDescriptor());
        when(pluginRepository.findById(PLUGIN_ID)).thenReturn(Optional.of(existing));

        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> service.install(jarPath, metadata)
        );

        assertEquals("插件已存在: " + PLUGIN_ID, thrown.getMessage());
        verify(pluginRepository).findById(PLUGIN_ID);
        verify(pf4jManager, never()).getPlugin(PLUGIN_ID);
        verify(pf4jManager, never()).loadPlugin(any(Path.class));
        verify(flywayMigrator, never()).migrate(anyString(), any(Path.class));
        verify(pluginRepository, never()).insert(any(PluginRow.class));
        assertSnapshotDirectoryEmpty();
    }

    @Test
    void installNewShouldRejectUninstalledExistingIdBeforePf4jLoadAndMigration()
            throws Exception {
        Path jarPath = Files.writeString(tempDir.resolve("upload-existing.jar"), "signed-content");
        Path previousJarPath = Files.writeString(tempDir.resolve("previous-upload.jar"), "previous");
        PluginRow existing = storedPlugin(previousJarPath, "b".repeat(64));
        existing.setStatus(PluginStatus.UNINSTALLED);

        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(any(Path.class))).thenReturn(versionDescriptor());
        when(pluginRepository.findById(PLUGIN_ID)).thenReturn(Optional.of(existing));

        PluginManagerService.PluginAlreadyExistsException thrown = assertThrows(
            PluginManagerService.PluginAlreadyExistsException.class,
            () -> service.installNew(jarPath, metadata(new byte[] {1, 2, 3}))
        );

        assertEquals(PLUGIN_ID, thrown.getPluginId());
        verify(pf4jManager, never()).getPlugin(PLUGIN_ID);
        verify(pf4jManager, never()).loadPlugin(any(Path.class));
        verify(flywayMigrator, never()).migrate(anyString(), any(Path.class));
        verify(pluginRepository, never()).replaceUninstalled(
            any(PluginRow.class), anyString(), anyString());
        assertSnapshotDirectoryEmpty();
    }

    @Test
    void reinstallShouldRejectDifferentSignerBeforePf4jLoadAndMigration() throws Exception {
        Path jarPath = Files.writeString(tempDir.resolve("different-signer.jar"), "signed-content");
        Path previousJarPath = Files.writeString(tempDir.resolve("previous-signer.jar"), "previous");
        PluginRow existing = storedPlugin(previousJarPath, "b".repeat(64));
        existing.setStatus(PluginStatus.UNINSTALLED);
        existing.setSignerId("another-trusted-signer");

        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(any(Path.class))).thenReturn(versionDescriptor());
        when(pluginRepository.findById(PLUGIN_ID)).thenReturn(Optional.of(existing));

        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> service.install(jarPath, metadata(new byte[]{1, 2, 3}))
        );

        assertEquals("插件签名者与原安装记录不一致: " + PLUGIN_ID, thrown.getMessage());
        verify(pf4jManager, never()).getPlugin(PLUGIN_ID);
        verify(pf4jManager, never()).loadPlugin(any(Path.class));
        verify(flywayMigrator, never()).migrate(anyString(), any(Path.class));
        verify(pluginRepository, never()).replaceUninstalled(
            any(PluginRow.class), anyString(), anyString());
        assertSnapshotDirectoryEmpty();
    }

    @Test
    void reinstallShouldRejectUninstalledRecordWithLoadedWrapper() throws Exception {
        Path jarPath = Files.writeString(tempDir.resolve("loaded-wrapper.jar"), "signed-content");
        Path previousJarPath = Files.writeString(tempDir.resolve("previous-wrapper.jar"), "previous");
        PluginRow existing = storedPlugin(previousJarPath, "b".repeat(64));
        existing.setStatus(PluginStatus.UNINSTALLED);
        PluginWrapper loadedWrapper = org.mockito.Mockito.mock(PluginWrapper.class);

        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(any(Path.class))).thenReturn(versionDescriptor());
        when(pluginRepository.findById(PLUGIN_ID)).thenReturn(Optional.of(existing));
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(loadedWrapper);

        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> service.install(jarPath, metadata(new byte[]{1, 2, 3}))
        );

        assertEquals("已卸载插件仍存在运行时实例: " + PLUGIN_ID, thrown.getMessage());
        verify(pf4jManager, never()).loadPlugin(any(Path.class));
        verify(flywayMigrator, never()).migrate(anyString(), any(Path.class));
        verify(pluginRepository, never()).replaceUninstalled(
            any(PluginRow.class), anyString(), anyString());
        assertSnapshotDirectoryEmpty();
    }

    @Test
    void reinstallShouldReplaceRecordAndDeleteManagedPreviousSnapshot() throws Exception {
        Path snapshotDirectory = tempDir.resolve("plugins").resolve(".snapshots").toAbsolutePath();
        Files.createDirectories(snapshotDirectory);
        Path previousSnapshot = Files.writeString(snapshotDirectory.resolve("plugin-previous.jar"), "previous");
        Path jarPath = Files.writeString(tempDir.resolve("replacement.jar"), "replacement");
        PluginRow existing = storedPlugin(previousSnapshot, "b".repeat(64));
        existing.setStatus(PluginStatus.UNINSTALLED);
        PluginWrapper wrapper = wrapper("1.0.0");

        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(any(Path.class))).thenReturn(versionDescriptor());
        when(pluginRepository.findById(PLUGIN_ID)).thenReturn(Optional.of(existing));
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(null, wrapper);
        when(pf4jManager.loadPlugin(any(Path.class))).thenReturn(PLUGIN_ID);
        when(pluginRepository.replaceUninstalled(
            any(PluginRow.class), eq(previousSnapshot.toString()), eq(SIGNER_ID)
        )).thenReturn(true);

        PluginRow installed = service.install(jarPath, metadata(new byte[]{1, 2, 3}));

        assertFalse(Files.exists(previousSnapshot));
        assertTrue(Files.isRegularFile(Path.of(installed.getJarPath())));
        verify(pluginRepository).replaceUninstalled(
            any(PluginRow.class), eq(previousSnapshot.toString()), eq(SIGNER_ID));
        verify(pluginRepository, never()).insert(any(PluginRow.class));
    }

    @Test
    void reinstallShouldKeepExternalPreviousJar() throws Exception {
        Path previousJar = Files.writeString(tempDir.resolve("external-previous.jar"), "previous");
        Path jarPath = Files.writeString(tempDir.resolve("external-replacement.jar"), "replacement");
        PluginRow existing = storedPlugin(previousJar, "b".repeat(64));
        existing.setStatus(PluginStatus.UNINSTALLED);
        PluginWrapper wrapper = wrapper("1.0.0");

        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(any(Path.class))).thenReturn(versionDescriptor());
        when(pluginRepository.findById(PLUGIN_ID)).thenReturn(Optional.of(existing));
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(null, wrapper);
        when(pf4jManager.loadPlugin(any(Path.class))).thenReturn(PLUGIN_ID);
        when(pluginRepository.replaceUninstalled(
            any(PluginRow.class), eq(previousJar.toString()), eq(SIGNER_ID)
        )).thenReturn(true);

        service.install(jarPath, metadata(new byte[]{1, 2, 3}));

        assertTrue(Files.isRegularFile(previousJar));
    }

    @Test
    void reinstallShouldKeepPreviousSnapshotReferencedByAnotherPlugin() throws Exception {
        Path snapshotDirectory = tempDir.resolve("plugins").resolve(".snapshots").toAbsolutePath();
        Files.createDirectories(snapshotDirectory);
        Path previousSnapshot = Files.writeString(
            snapshotDirectory.resolve("plugin-shared.jar"),
            "previous"
        );
        Path jarPath = Files.writeString(tempDir.resolve("shared-replacement.jar"), "replacement");
        PluginRow existing = storedPlugin(previousSnapshot, "b".repeat(64));
        existing.setStatus(PluginStatus.UNINSTALLED);
        PluginWrapper wrapper = wrapper("1.0.0");

        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(any(Path.class))).thenReturn(versionDescriptor());
        when(pluginRepository.findById(PLUGIN_ID)).thenReturn(Optional.of(existing));
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(null, wrapper);
        when(pf4jManager.loadPlugin(any(Path.class))).thenReturn(PLUGIN_ID);
        when(pluginRepository.replaceUninstalled(
            any(PluginRow.class), eq(previousSnapshot.toString()), eq(SIGNER_ID)
        )).thenReturn(true);
        when(pluginRepository.isJarPathReferencedByAnotherPlugin(
            previousSnapshot.toString(),
            PLUGIN_ID
        )).thenReturn(true);

        service.install(jarPath, metadata(new byte[]{1, 2, 3}));

        assertTrue(Files.isRegularFile(previousSnapshot));
    }

    @Test
    void reinstallShouldCleanupCandidateWhenConditionalReplaceLosesRace() throws Exception {
        Path snapshotDirectory = tempDir.resolve("plugins").resolve(".snapshots").toAbsolutePath();
        Files.createDirectories(snapshotDirectory);
        Path previousSnapshot = Files.writeString(snapshotDirectory.resolve("race-previous.jar"), "previous");
        Path jarPath = Files.writeString(tempDir.resolve("race-replacement.jar"), "replacement");
        PluginRow existing = storedPlugin(previousSnapshot, "b".repeat(64));
        existing.setStatus(PluginStatus.UNINSTALLED);
        PluginWrapper wrapper = wrapper("1.0.0");
        AtomicReference<Path> candidateSnapshot = new AtomicReference<>();

        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenAnswer(invocation -> {
                candidateSnapshot.set(invocation.getArgument(0));
                return VERIFIED_SHA256;
            });
        when(versionService.checkCompatibility(any(Path.class))).thenReturn(versionDescriptor());
        when(pluginRepository.findById(PLUGIN_ID)).thenReturn(Optional.of(existing));
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(null, wrapper);
        when(pf4jManager.loadPlugin(any(Path.class))).thenReturn(PLUGIN_ID);
        when(pluginRepository.replaceUninstalled(
            any(PluginRow.class), eq(previousSnapshot.toString()), eq(SIGNER_ID)
        )).thenReturn(false);
        when(pf4jManager.unloadPluginWithoutDependents(PLUGIN_ID)).thenReturn(true);

        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> service.install(jarPath, metadata(new byte[]{1, 2, 3}))
        );

        assertEquals("插件状态已变化，替换安装失败: " + PLUGIN_ID, thrown.getMessage());
        assertTrue(Files.isRegularFile(previousSnapshot));
        assertNotNull(candidateSnapshot.get());
        assertFalse(Files.exists(candidateSnapshot.get()));
        verify(pf4jManager).unloadPluginWithoutDependents(PLUGIN_ID);
        verify(pluginRepository, never()).insert(any(PluginRow.class));
    }

    @Test
    void reinstallShouldPreservePreviousSnapshotWhenMigrationFails() throws Exception {
        Path snapshotDirectory = tempDir.resolve("plugins").resolve(".snapshots").toAbsolutePath();
        Files.createDirectories(snapshotDirectory);
        Path previousSnapshot = Files.writeString(snapshotDirectory.resolve("migration-previous.jar"), "previous");
        Path jarPath = Files.writeString(tempDir.resolve("migration-replacement.jar"), "replacement");
        PluginRow existing = storedPlugin(previousSnapshot, "b".repeat(64));
        existing.setStatus(PluginStatus.UNINSTALLED);
        PluginWrapper wrapper = wrapper("1.0.0");
        AtomicReference<Path> candidateSnapshot = new AtomicReference<>();
        PluginFlywayMigrator.PluginMigrationException migrationError =
            new PluginFlywayMigrator.PluginMigrationException("migration failed", null);

        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenAnswer(invocation -> {
                candidateSnapshot.set(invocation.getArgument(0));
                return VERIFIED_SHA256;
            });
        when(versionService.checkCompatibility(any(Path.class))).thenReturn(versionDescriptor());
        when(pluginRepository.findById(PLUGIN_ID)).thenReturn(Optional.of(existing));
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(null, wrapper);
        when(pf4jManager.loadPlugin(any(Path.class))).thenReturn(PLUGIN_ID);
        doThrow(migrationError).when(flywayMigrator).migrate(eq(PLUGIN_ID), any(Path.class));
        when(pf4jManager.unloadPluginWithoutDependents(PLUGIN_ID)).thenReturn(true);

        assertThrows(
            PluginFlywayMigrator.PluginMigrationException.class,
            () -> service.install(jarPath, metadata(new byte[]{1, 2, 3}))
        );

        assertTrue(Files.isRegularFile(previousSnapshot));
        assertNotNull(candidateSnapshot.get());
        assertFalse(Files.exists(candidateSnapshot.get()));
        assertEquals(PluginStatus.UNINSTALLED, existing.getStatus());
        verify(pluginRepository, never()).replaceUninstalled(
            any(PluginRow.class), anyString(), anyString());
    }

    @Test
    void reinstallShouldReturnPersistedRecordWhenCommitAcknowledgementIsLost() throws Exception {
        Path snapshotDirectory = tempDir.resolve("plugins").resolve(".snapshots").toAbsolutePath();
        Files.createDirectories(snapshotDirectory);
        Path previousSnapshot = Files.writeString(
            snapshotDirectory.resolve("plugin-commit-previous.jar"),
            "previous"
        );
        Path jarPath = Files.writeString(tempDir.resolve("commit-replacement.jar"), "replacement");
        PluginRow existing = storedPlugin(previousSnapshot, "b".repeat(64));
        existing.setStatus(PluginStatus.UNINSTALLED);
        PluginWrapper wrapper = wrapper("1.0.0");
        AtomicReference<PluginRow> persisted = new AtomicReference<>();
        RuntimeException commitError = new RuntimeException("commit acknowledgement lost");

        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(any(Path.class))).thenReturn(versionDescriptor());
        when(pluginRepository.findById(PLUGIN_ID)).thenAnswer(invocation ->
            persisted.get() == null ? Optional.of(existing) : Optional.of(persisted.get()));
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(null, wrapper);
        when(pf4jManager.loadPlugin(any(Path.class))).thenReturn(PLUGIN_ID);
        when(pluginRepository.replaceUninstalled(
            any(PluginRow.class), eq(previousSnapshot.toString()), eq(SIGNER_ID)
        )).thenAnswer(invocation -> {
            persisted.set(invocation.getArgument(0));
            throw commitError;
        });

        PluginRow installed = service.install(jarPath, metadata(new byte[]{1, 2, 3}));

        assertEquals(persisted.get().getJarPath(), installed.getJarPath());
        assertTrue(Files.isRegularFile(Path.of(installed.getJarPath())));
        assertFalse(Files.exists(previousSnapshot));
        verify(pf4jManager, never()).unloadPluginWithoutDependents(PLUGIN_ID);
    }

    @Test
    void reinstallShouldNotConfirmUnknownCommitWhenDependenciesDiffer() throws Exception {
        Path snapshotDirectory = tempDir.resolve("plugins").resolve(".snapshots").toAbsolutePath();
        Files.createDirectories(snapshotDirectory);
        Path previousSnapshot = Files.writeString(
            snapshotDirectory.resolve("plugin-dependency-previous.jar"),
            "previous"
        );
        Path jarPath = Files.writeString(tempDir.resolve("dependency-replacement.jar"), "replacement");
        PluginRow existing = storedPlugin(previousSnapshot, "b".repeat(64));
        existing.setStatus(PluginStatus.UNINSTALLED);
        PluginDependencySpec dependency = new PluginDependencySpec("base-plugin", "1.0.0", null);
        PluginVersionService.PluginVersionDescriptor descriptor =
            new PluginVersionService.PluginVersionDescriptor(
                PLUGIN_ID, "1.0.0", "0.1.0", "0.1.0", "0.2.0", "17",
                "FUNCTIONAL", "Workforce Hub", null, List.of(dependency)
            );
        PluginWrapper wrapper = wrapper("1.0.0");
        AtomicReference<PluginRow> readback = new AtomicReference<>();
        RuntimeException commitError = new RuntimeException("commit acknowledgement lost");

        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(any(Path.class))).thenReturn(descriptor);
        when(pluginRepository.findById(PLUGIN_ID)).thenAnswer(invocation ->
            readback.get() == null ? Optional.of(existing) : Optional.of(readback.get()));
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(null, wrapper);
        when(pf4jManager.loadPlugin(any(Path.class))).thenReturn(PLUGIN_ID);
        when(pluginRepository.replaceUninstalled(
            any(PluginRow.class), eq(previousSnapshot.toString()), eq(SIGNER_ID)
        )).thenAnswer(invocation -> {
            PluginRow candidate = invocation.getArgument(0);
            PluginRow stored = new PluginRow();
            stored.setPluginId(candidate.getPluginId());
            stored.setVersion(candidate.getVersion());
            stored.setStatus(PluginStatus.INSTALLED);
            stored.setJarPath(candidate.getJarPath());
            stored.setJarSha256(candidate.getJarSha256());
            stored.setSignerId(candidate.getSignerId());
            stored.setDependencies(List.of());
            readback.set(stored);
            throw commitError;
        });
        when(pf4jManager.unloadPluginWithoutDependents(PLUGIN_ID)).thenReturn(true);

        RuntimeException thrown = assertThrows(
            RuntimeException.class,
            () -> service.install(jarPath, metadata(new byte[]{1, 2, 3}))
        );

        assertSame(commitError, thrown);
        assertTrue(Files.isRegularFile(previousSnapshot));
        assertFalse(Files.exists(Path.of(readback.get().getJarPath())));
        verify(pf4jManager).unloadPluginWithoutDependents(PLUGIN_ID);
    }

    @Test
    void reinstallShouldRetainCandidateWhenPersistedStatusChangedAfterCommit() throws Exception {
        Path snapshotDirectory = tempDir.resolve("plugins").resolve(".snapshots").toAbsolutePath();
        Files.createDirectories(snapshotDirectory);
        Path previousSnapshot = Files.writeString(
            snapshotDirectory.resolve("plugin-changed-previous.jar"),
            "previous"
        );
        Path jarPath = Files.writeString(tempDir.resolve("changed-replacement.jar"), "replacement");
        PluginRow existing = storedPlugin(previousSnapshot, "b".repeat(64));
        existing.setStatus(PluginStatus.UNINSTALLED);
        PluginWrapper wrapper = wrapper("1.0.0");
        AtomicReference<PluginRow> persisted = new AtomicReference<>();
        RuntimeException commitError = new RuntimeException("commit acknowledgement lost");

        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(any(Path.class))).thenReturn(versionDescriptor());
        when(pluginRepository.findById(PLUGIN_ID)).thenAnswer(invocation ->
            persisted.get() == null ? Optional.of(existing) : Optional.of(persisted.get()));
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(null, wrapper);
        when(pf4jManager.loadPlugin(any(Path.class))).thenReturn(PLUGIN_ID);
        when(pluginRepository.replaceUninstalled(
            any(PluginRow.class), eq(previousSnapshot.toString()), eq(SIGNER_ID)
        )).thenAnswer(invocation -> {
            PluginRow candidate = invocation.getArgument(0);
            PluginRow changed = storedPlugin(
                Path.of(candidate.getJarPath()),
                candidate.getJarSha256()
            );
            changed.setStatus(PluginStatus.UNINSTALLED);
            changed.setVersion(candidate.getVersion());
            persisted.set(changed);
            throw commitError;
        });

        RuntimeException thrown = assertThrows(
            RuntimeException.class,
            () -> service.install(jarPath, metadata(new byte[]{1, 2, 3}))
        );

        assertEquals(commitError, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertEquals("插件安装提交后状态已再次变化，需重启", thrown.getSuppressed()[0].getMessage());
        assertTrue(Files.isRegularFile(Path.of(persisted.get().getJarPath())));
        assertTrue(Files.isRegularFile(previousSnapshot));
        verify(pf4jManager, never()).unloadPluginWithoutDependents(PLUGIN_ID);
    }

    @Test
    void reinstallShouldRetainCandidateWhenCommitResultCannotBeReadBack() throws Exception {
        Path snapshotDirectory = tempDir.resolve("plugins").resolve(".snapshots").toAbsolutePath();
        Files.createDirectories(snapshotDirectory);
        Path previousSnapshot = Files.writeString(
            snapshotDirectory.resolve("plugin-unknown-previous.jar"),
            "previous"
        );
        Path jarPath = Files.writeString(tempDir.resolve("unknown-replacement.jar"), "replacement");
        PluginRow existing = storedPlugin(previousSnapshot, "b".repeat(64));
        existing.setStatus(PluginStatus.UNINSTALLED);
        PluginWrapper wrapper = wrapper("1.0.0");
        AtomicReference<Path> candidateSnapshot = new AtomicReference<>();
        RuntimeException commitError = new RuntimeException("commit acknowledgement lost");
        RuntimeException readbackError = new RuntimeException("readback unavailable");

        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenAnswer(invocation -> {
                candidateSnapshot.set(invocation.getArgument(0));
                return VERIFIED_SHA256;
            });
        when(versionService.checkCompatibility(any(Path.class))).thenReturn(versionDescriptor());
        when(pluginRepository.findById(PLUGIN_ID))
            .thenReturn(Optional.of(existing))
            .thenThrow(readbackError);
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(null, wrapper);
        when(pf4jManager.loadPlugin(any(Path.class))).thenReturn(PLUGIN_ID);
        when(pluginRepository.replaceUninstalled(
            any(PluginRow.class), eq(previousSnapshot.toString()), eq(SIGNER_ID)
        )).thenThrow(commitError);

        RuntimeException thrown = assertThrows(
            RuntimeException.class,
            () -> service.install(jarPath, metadata(new byte[]{1, 2, 3}))
        );

        assertEquals(commitError, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertEquals(readbackError, thrown.getSuppressed()[0].getCause());
        assertNotNull(candidateSnapshot.get());
        assertTrue(Files.isRegularFile(candidateSnapshot.get()));
        assertTrue(Files.isRegularFile(previousSnapshot));
        verify(pf4jManager, never()).unloadPluginWithoutDependents(PLUGIN_ID);
    }

    @Test
    void installShouldRejectOversizedMetadataBeforePf4jLoadAndMigration() throws Exception {
        Path jarPath = Files.writeString(tempDir.resolve("oversized-plugin.jar"), "signed-content");
        PluginRow metadata = metadata(new byte[]{1, 2, 3});
        metadata.setName("x".repeat(129));

        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(any(Path.class))).thenReturn(versionDescriptor());

        IllegalArgumentException thrown = assertThrows(
            IllegalArgumentException.class,
            () -> service.install(jarPath, metadata)
        );

        assertEquals("插件字段超长: name", thrown.getMessage());
        verify(pf4jManager, never()).loadPlugin(any(Path.class));
        verify(flywayMigrator, never()).migrate(anyString(), any(Path.class));
        verify(pluginRepository, never()).insert(any(PluginRow.class));
        assertSnapshotDirectoryEmpty();
    }

    @Test
    void installShouldNotLoadWhenSignatureVerificationFails() throws Exception {
        Path jarPath = Files.writeString(tempDir.resolve("rejected-plugin.jar"), "rejected-content");
        PluginRow metadata = metadata(new byte[]{1});
        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenThrow(new PluginSignatureVerifier.PluginSignatureException("插件签名验证失败"));

        assertThrows(
            PluginSignatureVerifier.PluginSignatureException.class,
            () -> service.install(jarPath, metadata)
        );

        verify(pf4jManager, never()).loadPlugin(any(Path.class));
        verify(pf4jManager, never()).unloadPluginWithoutDependents(anyString());
        verify(versionService, never()).checkCompatibility(any(Path.class));
        verify(flywayMigrator, never()).migrate(anyString(), any(Path.class));
        verify(pluginRepository, never()).insert(any(PluginRow.class));
    }

    @Test
    void installShouldNotLoadWhenVersionVerificationFails() throws Exception {
        Path jarPath = Files.writeString(tempDir.resolve("incompatible-plugin.jar"), "incompatible-content");
        PluginRow metadata = metadata(new byte[]{1});
        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(any(Path.class)))
            .thenThrow(new PluginVersionService.PluginVersionException("插件SDK版本不兼容"));

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.install(jarPath, metadata)
        );

        verify(pf4jManager, never()).loadPlugin(any(Path.class));
        verify(flywayMigrator, never()).migrate(anyString(), any(Path.class));
        verify(pluginRepository, never()).insert(any(PluginRow.class));
    }

    @Test
    void installShouldUnloadWhenPf4jIdentityDiffersFromSignedManifest() throws Exception {
        Path jarPath = Files.writeString(tempDir.resolve("identity-mismatch.jar"), "identity-content");
        PluginRow metadata = metadata(new byte[]{1});
        PluginVersionService.PluginVersionDescriptor descriptor = new PluginVersionService.PluginVersionDescriptor(
            "manifest-plugin",
            "1.0.0",
            "0.1.0",
            "0.1.0",
            null,
            "17",
            "FUNCTIONAL",
            "Workforce Hub",
            null,
            List.of()
        );
        PluginWrapper wrapper = org.mockito.Mockito.mock(PluginWrapper.class);
        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(any(Path.class))).thenReturn(descriptor);
        when(pf4jManager.loadPlugin(any(Path.class))).thenReturn(PLUGIN_ID);
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(wrapper);
        when(pf4jManager.unloadPluginWithoutDependents(PLUGIN_ID)).thenReturn(true);

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.install(jarPath, metadata)
        );

        verify(pf4jManager).unloadPluginWithoutDependents(PLUGIN_ID);
        verify(flywayMigrator, never()).migrate(anyString(), any(Path.class));
        verify(pluginRepository, never()).insert(any(PluginRow.class));
    }

    @Test
    void installShouldUnloadWhenPf4jVersionDiffersFromSignedManifest() throws Exception {
        Path jarPath = Files.writeString(tempDir.resolve("version-mismatch.jar"), "version-content");
        PluginRow metadata = metadata(new byte[]{1});
        PluginWrapper wrapper = wrapper("1.0.1");
        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(any(Path.class))).thenReturn(versionDescriptor());
        when(pf4jManager.loadPlugin(any(Path.class))).thenReturn(PLUGIN_ID);
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(wrapper);
        PluginDescriptor pf4jDescriptor = wrapper.getDescriptor();
        PluginDescriptor signedPf4jDescriptor = org.mockito.Mockito.mock(PluginDescriptor.class);
        when(pf4jManager.readPluginDescriptor(any(Path.class))).thenReturn(signedPf4jDescriptor);
        doAnswer(invocation -> {
            if (invocation.getArgument(1) == pf4jDescriptor) {
                throw new PluginVersionService.PluginVersionException(
                    "PF4J 插件描述与 plugin.json 不一致"
                );
            }
            return null;
        }).when(versionService).validatePf4jDescriptor(any(), any());
        when(pf4jManager.unloadPluginWithoutDependents(PLUGIN_ID)).thenReturn(true);

        assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> service.install(jarPath, metadata)
        );

        verify(pf4jManager).unloadPluginWithoutDependents(PLUGIN_ID);
        verify(flywayMigrator, never()).migrate(anyString(), any(Path.class));
        verify(pluginRepository, never()).insert(any(PluginRow.class));
    }

    @Test
    void installShouldExposeCleanupFailureAndRetainSnapshotWhenUnloadReturnsFalse() throws Exception {
        Path jarPath = Files.writeString(tempDir.resolve("cleanup-failure.jar"), "cleanup-content");
        PluginRow metadata = metadata(new byte[]{1});
        PluginWrapper wrapper = wrapper("1.0.0");
        AtomicReference<Path> snapshotPath = new AtomicReference<>();
        PluginFlywayMigrator.PluginMigrationException migrationError =
            new PluginFlywayMigrator.PluginMigrationException("迁移失败", new RuntimeException("sql"));
        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenAnswer(invocation -> {
                snapshotPath.set(invocation.getArgument(0));
                return VERIFIED_SHA256;
            });
        when(versionService.checkCompatibility(any(Path.class))).thenReturn(versionDescriptor());
        when(pf4jManager.loadPlugin(any(Path.class))).thenReturn(PLUGIN_ID);
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(wrapper);
        doThrow(migrationError).when(flywayMigrator).migrate(eq(PLUGIN_ID), any(Path.class));
        when(pf4jManager.unloadPluginWithoutDependents(PLUGIN_ID)).thenReturn(false);

        PluginFlywayMigrator.PluginMigrationException thrown = assertThrows(
            PluginFlywayMigrator.PluginMigrationException.class,
            () -> service.install(jarPath, metadata)
        );

        assertEquals(1, thrown.getSuppressed().length);
        assertEquals("插件清理失败: " + PLUGIN_ID, thrown.getSuppressed()[0].getMessage());
        assertNotNull(snapshotPath.get());
        assertTrue(Files.isRegularFile(snapshotPath.get()));
        verify(pluginRepository, never()).insert(any(PluginRow.class));
    }

    @Test
    void installShouldExposeCleanupFailureAndRetainSnapshotWhenUnloadThrows() throws Exception {
        Path jarPath = Files.writeString(tempDir.resolve("cleanup-exception.jar"), "cleanup-content");
        PluginRow metadata = metadata(new byte[]{1});
        PluginWrapper wrapper = wrapper("1.0.0");
        AtomicReference<Path> snapshotPath = new AtomicReference<>();
        PluginFlywayMigrator.PluginMigrationException migrationError =
            new PluginFlywayMigrator.PluginMigrationException("迁移失败", new RuntimeException("sql"));
        RuntimeException unloadError = new RuntimeException("close failed");
        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenAnswer(invocation -> {
                snapshotPath.set(invocation.getArgument(0));
                return VERIFIED_SHA256;
            });
        when(versionService.checkCompatibility(any(Path.class))).thenReturn(versionDescriptor());
        when(pf4jManager.loadPlugin(any(Path.class))).thenReturn(PLUGIN_ID);
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(wrapper);
        doThrow(migrationError).when(flywayMigrator).migrate(eq(PLUGIN_ID), any(Path.class));
        doThrow(unloadError).when(pf4jManager).unloadPluginWithoutDependents(PLUGIN_ID);

        PluginFlywayMigrator.PluginMigrationException thrown = assertThrows(
            PluginFlywayMigrator.PluginMigrationException.class,
            () -> service.install(jarPath, metadata)
        );

        assertEquals(1, thrown.getSuppressed().length);
        assertEquals("插件清理失败: " + PLUGIN_ID, thrown.getSuppressed()[0].getMessage());
        assertEquals(unloadError, thrown.getSuppressed()[0].getCause());
        assertNotNull(snapshotPath.get());
        assertTrue(Files.isRegularFile(snapshotPath.get()));
        verify(pluginRepository, never()).insert(any(PluginRow.class));
    }

    @Test
    void restoreShouldVerifyStoredSignatureBeforeLoading() throws Exception {
        Path jarPath = Files.writeString(tempDir.resolve("restore-plugin.jar"), "plugin");
        PluginRow stored = storedPlugin(jarPath, VERIFIED_SHA256);
        PluginWrapper wrapper = wrapper("1.0.0");
        when(pluginRepository.findAll()).thenReturn(List.of(stored));
        when(signatureVerifier.verify(eq(jarPath), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(jarPath)).thenReturn(versionDescriptor());
        when(pf4jManager.loadPlugin(jarPath)).thenReturn(PLUGIN_ID);
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(wrapper);
        when(pf4jManager.startPlugin(PLUGIN_ID)).thenReturn(PluginState.STARTED);

        service.restorePlugins();

        InOrder order = inOrder(signatureVerifier, versionService, pf4jManager, lifecycleGuard);
        order.verify(signatureVerifier).verify(eq(jarPath), eq(SIGNER_ID), any(byte[].class));
        order.verify(versionService).checkCompatibility(jarPath);
        order.verify(pf4jManager).loadPlugin(jarPath);
        order.verify(pf4jManager).startPlugin(PLUGIN_ID);
        order.verify(lifecycleGuard).enable(PLUGIN_ID);
    }

    @ParameterizedTest
    @EnumSource(value = PluginStatus.class, names = {"INSTALLED", "STOPPED"})
    void restoreShouldLoadInactivePluginWithoutStarting(PluginStatus status) throws Exception {
        Path jarPath = Files.writeString(tempDir.resolve("restore-" + status.name() + ".jar"), "plugin");
        PluginRow stored = storedPlugin(jarPath, VERIFIED_SHA256);
        PluginWrapper wrapper = wrapper("1.0.0");
        stored.setStatus(status);
        when(pluginRepository.findAll()).thenReturn(List.of(stored));
        when(signatureVerifier.verify(eq(jarPath), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(jarPath)).thenReturn(versionDescriptor());
        when(pf4jManager.loadPlugin(jarPath)).thenReturn(PLUGIN_ID);
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(wrapper);

        service.restorePlugins();

        verify(pf4jManager).loadPlugin(jarPath);
        verify(pf4jManager, never()).startPlugin(anyString());
        verify(lifecycleGuard, never()).enable(anyString());
        verify(pluginRepository, never()).updateStatus(eq(PLUGIN_ID), eq(PluginStatus.ERROR), anyString());
    }

    @Test
    void restoreShouldRejectTrustedDependencyCycleBeforeLoadingAnyPlugin() throws Exception {
        String firstId = "cycle-first";
        String secondId = "cycle-second";
        Path firstJar = Files.writeString(tempDir.resolve("cycle-first.jar"), "first");
        Path secondJar = Files.writeString(tempDir.resolve("cycle-second.jar"), "second");
        PluginDependencySpec firstDependency = new PluginDependencySpec(secondId, null, null);
        PluginDependencySpec secondDependency = new PluginDependencySpec(firstId, null, null);
        PluginRow first = storedPlugin(firstJar, VERIFIED_SHA256);
        first.setPluginId(firstId);
        first.setDependencies(List.of(firstDependency));
        PluginRow second = storedPlugin(secondJar, VERIFIED_SHA256);
        second.setPluginId(secondId);
        second.setDependencies(List.of(secondDependency));
        PluginVersionService.PluginVersionDescriptor firstDescriptor =
            new PluginVersionService.PluginVersionDescriptor(
                firstId, "1.0.0", "0.1.0", "0.1.0", "0.2.0", "17",
                "FUNCTIONAL", "Workforce Hub", null, List.of(firstDependency)
            );
        PluginVersionService.PluginVersionDescriptor secondDescriptor =
            new PluginVersionService.PluginVersionDescriptor(
                secondId, "1.0.0", "0.1.0", "0.1.0", "0.2.0", "17",
                "FUNCTIONAL", "Workforce Hub", null, List.of(secondDependency)
            );
        when(pluginRepository.findAll()).thenReturn(List.of(first, second));
        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(firstJar)).thenReturn(firstDescriptor);
        when(versionService.checkCompatibility(secondJar)).thenReturn(secondDescriptor);
        when(versionService.matchesVersion(any(PluginDependencySpec.class), eq("1.0.0")))
            .thenReturn(true);

        service.restorePlugins();

        verify(pf4jManager, never()).loadPlugin(any(Path.class));
        verify(pluginRepository).updateStatus(firstId, PluginStatus.ERROR, "插件依赖存在循环");
        verify(pluginRepository).updateStatus(secondId, PluginStatus.ERROR, "插件依赖存在循环");
        verify(lifecycleGuard, never()).enable(anyString());
    }

    @Test
    void restoreShouldIsolateInvalidStoredDependencyVersion() throws Exception {
        String dependencyId = "invalid-version-dependency";
        String consumerId = "invalid-version-consumer";
        Path dependencyJar = Files.writeString(tempDir.resolve("invalid-version-dependency.jar"), "dependency");
        Path consumerJar = Files.writeString(tempDir.resolve("invalid-version-consumer.jar"), "consumer");
        Path healthyJar = Files.writeString(tempDir.resolve("healthy-plugin.jar"), "healthy");
        PluginDependencySpec dependencySpec = new PluginDependencySpec(dependencyId, "1.0.0", null);
        PluginRow dependency = storedPlugin(dependencyJar, VERIFIED_SHA256);
        dependency.setPluginId(dependencyId);
        PluginRow consumer = storedPlugin(consumerJar, VERIFIED_SHA256);
        consumer.setPluginId(consumerId);
        consumer.setDependencies(List.of(dependencySpec));
        PluginRow healthy = storedPlugin(healthyJar, VERIFIED_SHA256);
        PluginVersionService.PluginVersionDescriptor dependencyDescriptor =
            new PluginVersionService.PluginVersionDescriptor(
                dependencyId, "1.0.0", "0.1.0", "0.1.0", "0.2.0", "17",
                "FUNCTIONAL", "Workforce Hub", null, List.of()
            );
        PluginVersionService.PluginVersionDescriptor consumerDescriptor =
            new PluginVersionService.PluginVersionDescriptor(
                consumerId, "1.0.0", "0.1.0", "0.1.0", "0.2.0", "17",
                "FUNCTIONAL", "Workforce Hub", null, List.of(dependencySpec)
            );
        PluginWrapper dependencyWrapper = wrapper("1.0.0");
        PluginWrapper healthyWrapper = wrapper("1.0.0");

        when(pluginRepository.findAll()).thenReturn(List.of(dependency, consumer, healthy));
        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(dependencyJar)).thenReturn(dependencyDescriptor);
        when(versionService.checkCompatibility(consumerJar)).thenReturn(consumerDescriptor);
        when(versionService.checkCompatibility(healthyJar)).thenReturn(versionDescriptor());
        when(versionService.matchesVersion(dependencySpec, "1.0.0"))
            .thenThrow(new PluginVersionService.PluginVersionException("依赖插件版本必须是严格 SemVer"));
        when(pf4jManager.loadPlugin(dependencyJar)).thenReturn(dependencyId);
        when(pf4jManager.loadPlugin(healthyJar)).thenReturn(PLUGIN_ID);
        when(pf4jManager.getPlugin(dependencyId)).thenReturn(dependencyWrapper);
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(healthyWrapper);
        when(pf4jManager.startPlugin(dependencyId)).thenReturn(PluginState.STARTED);
        when(pf4jManager.startPlugin(PLUGIN_ID)).thenReturn(PluginState.STARTED);

        assertDoesNotThrow(service::restorePlugins);

        verify(pluginRepository).updateStatus(
            consumerId,
            PluginStatus.ERROR,
            "插件依赖版本记录无效: " + dependencyId
        );
        verify(pf4jManager, never()).loadPlugin(consumerJar);
        verify(pf4jManager).loadPlugin(dependencyJar);
        verify(pf4jManager).loadPlugin(healthyJar);
    }

    @Test
    void restoreShouldRejectIncompatibleDependencyVersionBeforeLoadingConsumer() throws Exception {
        String dependencyId = "version-conflict-dependency";
        String consumerId = "version-conflict-consumer";
        Path dependencyJar = Files.writeString(
            tempDir.resolve("version-conflict-dependency.jar"),
            "dependency"
        );
        Path consumerJar = Files.writeString(
            tempDir.resolve("version-conflict-consumer.jar"),
            "consumer"
        );
        PluginDependencySpec dependencySpec =
            new PluginDependencySpec(dependencyId, "2.0.0", null);
        PluginRow dependency = storedPlugin(dependencyJar, VERIFIED_SHA256);
        dependency.setPluginId(dependencyId);
        PluginRow consumer = storedPlugin(consumerJar, VERIFIED_SHA256);
        consumer.setPluginId(consumerId);
        consumer.setDependencies(List.of(dependencySpec));
        PluginVersionService.PluginVersionDescriptor dependencyDescriptor =
            new PluginVersionService.PluginVersionDescriptor(
                dependencyId, "1.0.0", "0.1.0", "0.1.0", "0.2.0", "17",
                "FUNCTIONAL", "Workforce Hub", null, List.of()
            );
        PluginVersionService.PluginVersionDescriptor consumerDescriptor =
            new PluginVersionService.PluginVersionDescriptor(
                consumerId, "1.0.0", "0.1.0", "0.1.0", "0.2.0", "17",
                "FUNCTIONAL", "Workforce Hub", null, List.of(dependencySpec)
            );
        PluginWrapper dependencyWrapper = wrapper("1.0.0");

        when(pluginRepository.findAll()).thenReturn(List.of(dependency, consumer));
        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(dependencyJar)).thenReturn(dependencyDescriptor);
        when(versionService.checkCompatibility(consumerJar)).thenReturn(consumerDescriptor);
        when(versionService.matchesVersion(dependencySpec, "1.0.0")).thenReturn(false);
        when(pf4jManager.loadPlugin(dependencyJar)).thenReturn(dependencyId);
        when(pf4jManager.getPlugin(dependencyId)).thenReturn(dependencyWrapper);
        when(pf4jManager.startPlugin(dependencyId)).thenReturn(PluginState.STARTED);

        service.restorePlugins();

        verify(pluginRepository).updateStatus(
            consumerId,
            PluginStatus.ERROR,
            "插件依赖版本不兼容: " + dependencyId
        );
        verify(pf4jManager, never()).loadPlugin(consumerJar);
        verify(pf4jManager).loadPlugin(dependencyJar);
        verify(lifecycleGuard).enable(dependencyId);
    }

    @Test
    void restoreShouldRejectActiveConsumerWhenDependencyIsNotActive() throws Exception {
        String dependencyId = "stopped-dependency";
        String consumerId = "active-consumer";
        Path dependencyJar = Files.writeString(
            tempDir.resolve("stopped-dependency.jar"),
            "dependency"
        );
        Path consumerJar = Files.writeString(
            tempDir.resolve("active-consumer.jar"),
            "consumer"
        );
        PluginDependencySpec dependencySpec =
            new PluginDependencySpec(dependencyId, null, null);
        PluginRow dependency = storedPlugin(dependencyJar, VERIFIED_SHA256);
        dependency.setPluginId(dependencyId);
        dependency.setStatus(PluginStatus.STOPPED);
        PluginRow consumer = storedPlugin(consumerJar, VERIFIED_SHA256);
        consumer.setPluginId(consumerId);
        consumer.setDependencies(List.of(dependencySpec));
        PluginVersionService.PluginVersionDescriptor dependencyDescriptor =
            new PluginVersionService.PluginVersionDescriptor(
                dependencyId, "1.0.0", "0.1.0", "0.1.0", "0.2.0", "17",
                "FUNCTIONAL", "Workforce Hub", null, List.of()
            );
        PluginVersionService.PluginVersionDescriptor consumerDescriptor =
            new PluginVersionService.PluginVersionDescriptor(
                consumerId, "1.0.0", "0.1.0", "0.1.0", "0.2.0", "17",
                "FUNCTIONAL", "Workforce Hub", null, List.of(dependencySpec)
            );
        PluginWrapper dependencyWrapper = wrapper("1.0.0");

        when(pluginRepository.findAll()).thenReturn(List.of(dependency, consumer));
        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(dependencyJar)).thenReturn(dependencyDescriptor);
        when(versionService.checkCompatibility(consumerJar)).thenReturn(consumerDescriptor);
        when(versionService.matchesVersion(dependencySpec, "1.0.0")).thenReturn(true);
        when(pf4jManager.loadPlugin(dependencyJar)).thenReturn(dependencyId);
        when(pf4jManager.getPlugin(dependencyId)).thenReturn(dependencyWrapper);

        service.restorePlugins();

        verify(pluginRepository).updateStatus(
            consumerId,
            PluginStatus.ERROR,
            "已启用插件依赖未启用: " + dependencyId
        );
        verify(pf4jManager, never()).loadPlugin(consumerJar);
        verify(pf4jManager).loadPlugin(dependencyJar);
        verify(pf4jManager, never()).startPlugin(anyString());
        verify(lifecycleGuard, never()).enable(anyString());
    }

    @Test
    void restoreShouldSkipDependentAfterRuntimeFailureAndContinueUnrelatedPlugin() throws Exception {
        String dependencyId = "runtime-failure-dependency";
        String consumerId = "runtime-failure-consumer";
        Path dependencyJar = Files.writeString(
            tempDir.resolve("runtime-failure-dependency.jar"),
            "dependency"
        );
        Path consumerJar = Files.writeString(
            tempDir.resolve("runtime-failure-consumer.jar"),
            "consumer"
        );
        Path healthyJar = Files.writeString(tempDir.resolve("runtime-healthy.jar"), "healthy");
        PluginDependencySpec dependencySpec =
            new PluginDependencySpec(dependencyId, null, null);
        PluginRow dependency = storedPlugin(dependencyJar, VERIFIED_SHA256);
        dependency.setPluginId(dependencyId);
        PluginRow consumer = storedPlugin(consumerJar, VERIFIED_SHA256);
        consumer.setPluginId(consumerId);
        consumer.setDependencies(List.of(dependencySpec));
        PluginRow healthy = storedPlugin(healthyJar, VERIFIED_SHA256);
        PluginVersionService.PluginVersionDescriptor dependencyDescriptor =
            new PluginVersionService.PluginVersionDescriptor(
                dependencyId, "1.0.0", "0.1.0", "0.1.0", "0.2.0", "17",
                "FUNCTIONAL", "Workforce Hub", null, List.of()
            );
        PluginVersionService.PluginVersionDescriptor consumerDescriptor =
            new PluginVersionService.PluginVersionDescriptor(
                consumerId, "1.0.0", "0.1.0", "0.1.0", "0.2.0", "17",
                "FUNCTIONAL", "Workforce Hub", null, List.of(dependencySpec)
            );
        PluginWrapper dependencyWrapper = wrapper("1.0.0");
        PluginWrapper healthyWrapper = wrapper("1.0.0");

        when(pluginRepository.findAll()).thenReturn(List.of(dependency, consumer, healthy));
        when(signatureVerifier.verify(any(Path.class), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(dependencyJar)).thenReturn(dependencyDescriptor);
        when(versionService.checkCompatibility(consumerJar)).thenReturn(consumerDescriptor);
        when(versionService.checkCompatibility(healthyJar)).thenReturn(versionDescriptor());
        when(versionService.matchesVersion(dependencySpec, "1.0.0")).thenReturn(true);
        when(pf4jManager.loadPlugin(dependencyJar)).thenReturn(dependencyId);
        when(pf4jManager.loadPlugin(healthyJar)).thenReturn(PLUGIN_ID);
        when(pf4jManager.getPlugin(dependencyId)).thenReturn(dependencyWrapper);
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(healthyWrapper);
        when(pf4jManager.startPlugin(dependencyId)).thenReturn(PluginState.FAILED);
        when(pf4jManager.startPlugin(PLUGIN_ID)).thenReturn(PluginState.STARTED);
        when(pf4jManager.unloadPluginWithoutDependents(dependencyId)).thenReturn(true);

        service.restorePlugins();

        verify(pluginRepository).updateStatus(
            dependencyId,
            PluginStatus.ERROR,
            "启动失败: FAILED"
        );
        verify(pluginRepository).updateStatus(
            consumerId,
            PluginStatus.ERROR,
            "插件依赖恢复失败: " + dependencyId
        );
        verify(pf4jManager, never()).loadPlugin(consumerJar);
        verify(pf4jManager).loadPlugin(healthyJar);
        verify(lifecycleGuard).enable(PLUGIN_ID);
    }

    @ParameterizedTest
    @EnumSource(SignedUiField.class)
    void restoreShouldRejectSignedUiFieldDriftBeforeLoad(SignedUiField field) throws Exception {
        Path jarPath = Files.writeString(
            tempDir.resolve("restore-ui-" + field.name().toLowerCase() + ".jar"),
            "plugin"
        );
        PluginRow stored = storedUiPlugin(jarPath, VERIFIED_SHA256);
        switch (field) {
            case TYPE -> stored.setType("FUNCTIONAL");
            case PROVIDER -> stored.setProvider("forged-provider");
            case FRONTEND_ENTRY -> stored.setFrontendEntry("/plugins/forged/index.js");
            case FRONTEND_SHA384 -> stored.setFrontendSha384("sha384-" + "B".repeat(64));
        }
        when(pluginRepository.findAll()).thenReturn(List.of(stored));
        when(signatureVerifier.verify(eq(jarPath), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(jarPath)).thenReturn(uiVersionDescriptor());

        service.restorePlugins();

        verify(pluginRepository).updateStatus(
            PLUGIN_ID,
            PluginStatus.ERROR,
            "插件清单与安装记录不一致"
        );
        verify(pf4jManager, never()).loadPlugin(any(Path.class));
        verify(pf4jManager, never()).startPlugin(anyString());
        verify(lifecycleGuard, never()).enable(anyString());
    }

    @Test
    void restoreShouldContinueWhenRecordingPreviousPluginFailureAlsoFails() throws Exception {
        String brokenPluginId = "broken-plugin";
        PluginRow broken = storedPlugin(tempDir.resolve("missing-plugin.jar"), VERIFIED_SHA256);
        broken.setPluginId(brokenPluginId);
        Path healthyJar = Files.writeString(tempDir.resolve("healthy-plugin.jar"), "plugin");
        PluginRow healthy = storedPlugin(healthyJar, VERIFIED_SHA256);
        PluginWrapper wrapper = wrapper("1.0.0");

        when(pluginRepository.findAll()).thenReturn(List.of(broken, healthy));
        doThrow(new IllegalStateException("status write failed"))
            .when(pluginRepository)
            .updateStatus(eq(brokenPluginId), eq(PluginStatus.ERROR), anyString());
        when(signatureVerifier.verify(eq(healthyJar), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(healthyJar)).thenReturn(versionDescriptor());
        when(pf4jManager.loadPlugin(healthyJar)).thenReturn(PLUGIN_ID);
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(wrapper);
        when(pf4jManager.startPlugin(PLUGIN_ID)).thenReturn(PluginState.STARTED);

        assertDoesNotThrow(service::restorePlugins);

        verify(signatureVerifier).verify(eq(healthyJar), eq(SIGNER_ID), any(byte[].class));
        verify(pf4jManager).loadPlugin(healthyJar);
        verify(lifecycleGuard).enable(PLUGIN_ID);
    }

    @Test
    void restoreShouldUnloadWhenPf4jIdentityDiffersFromSignedManifest() throws Exception {
        Path jarPath = Files.writeString(tempDir.resolve("restore-identity-mismatch.jar"), "plugin");
        PluginRow stored = storedPlugin(jarPath, VERIFIED_SHA256);
        String loadedPluginId = "different-plugin";
        when(pluginRepository.findAll()).thenReturn(List.of(stored));
        when(signatureVerifier.verify(eq(jarPath), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(jarPath)).thenReturn(versionDescriptor());
        when(pf4jManager.loadPlugin(jarPath)).thenReturn(loadedPluginId);
        when(pf4jManager.unloadPluginWithoutDependents(loadedPluginId)).thenReturn(true);

        service.restorePlugins();

        verify(pf4jManager).unloadPluginWithoutDependents(loadedPluginId);
        verify(pf4jManager, never()).startPlugin(anyString());
        verify(pluginRepository).updateStatus(
            PLUGIN_ID,
            PluginStatus.ERROR,
            "PF4J 插件描述符与 plugin.json 不一致"
        );
        verify(lifecycleGuard, never()).enable(anyString());
    }

    @Test
    void restoreShouldUnloadWhenPf4jVersionDiffersFromSignedManifest() throws Exception {
        Path jarPath = Files.writeString(tempDir.resolve("restore-version-mismatch.jar"), "plugin");
        PluginRow stored = storedPlugin(jarPath, VERIFIED_SHA256);
        PluginWrapper wrapper = wrapper("1.0.1");
        when(pluginRepository.findAll()).thenReturn(List.of(stored));
        when(signatureVerifier.verify(eq(jarPath), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(jarPath)).thenReturn(versionDescriptor());
        when(pf4jManager.loadPlugin(jarPath)).thenReturn(PLUGIN_ID);
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(wrapper);
        PluginDescriptor pf4jDescriptor = wrapper.getDescriptor();
        PluginDescriptor signedPf4jDescriptor = org.mockito.Mockito.mock(PluginDescriptor.class);
        when(pf4jManager.readPluginDescriptor(jarPath)).thenReturn(signedPf4jDescriptor);
        doAnswer(invocation -> {
            if (invocation.getArgument(1) == pf4jDescriptor) {
                throw new PluginVersionService.PluginVersionException(
                    "PF4J 插件描述与 plugin.json 不一致"
                );
            }
            return null;
        }).when(versionService).validatePf4jDescriptor(any(), any());
        when(pf4jManager.unloadPluginWithoutDependents(PLUGIN_ID)).thenReturn(true);

        service.restorePlugins();

        verify(pf4jManager).unloadPluginWithoutDependents(PLUGIN_ID);
        verify(pf4jManager, never()).startPlugin(anyString());
        verify(pluginRepository).updateStatus(
            PLUGIN_ID,
            PluginStatus.ERROR,
            "PF4J 插件描述与 plugin.json 不一致"
        );
        verify(lifecycleGuard, never()).enable(anyString());
    }

    @Test
    void restoreShouldUnloadWhenPluginStartFails() throws Exception {
        Path jarPath = Files.writeString(tempDir.resolve("restore-start-failure.jar"), "plugin");
        PluginRow stored = storedPlugin(jarPath, VERIFIED_SHA256);
        PluginWrapper wrapper = wrapper("1.0.0");
        when(pluginRepository.findAll()).thenReturn(List.of(stored));
        when(signatureVerifier.verify(eq(jarPath), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(jarPath)).thenReturn(versionDescriptor());
        when(pf4jManager.loadPlugin(jarPath)).thenReturn(PLUGIN_ID);
        when(pf4jManager.getPlugin(PLUGIN_ID)).thenReturn(wrapper);
        when(pf4jManager.startPlugin(PLUGIN_ID)).thenReturn(PluginState.FAILED);
        when(pf4jManager.unloadPluginWithoutDependents(PLUGIN_ID)).thenReturn(true);

        service.restorePlugins();

        verify(pf4jManager).unloadPluginWithoutDependents(PLUGIN_ID);
        verify(pluginRepository).updateStatus(PLUGIN_ID, PluginStatus.ERROR, "启动失败: FAILED");
        verify(lifecycleGuard, never()).enable(anyString());
    }

    @Test
    void restoreShouldRecordCleanupFailureWhenUnloadReturnsFalse() throws Exception {
        Path jarPath = Files.writeString(tempDir.resolve("restore-cleanup-failure.jar"), "plugin");
        PluginRow stored = storedPlugin(jarPath, VERIFIED_SHA256);
        String loadedPluginId = "different-plugin";
        when(pluginRepository.findAll()).thenReturn(List.of(stored));
        when(signatureVerifier.verify(eq(jarPath), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(jarPath)).thenReturn(versionDescriptor());
        when(pf4jManager.loadPlugin(jarPath)).thenReturn(loadedPluginId);
        when(pf4jManager.unloadPluginWithoutDependents(loadedPluginId)).thenReturn(false);

        service.restorePlugins();

        verify(pluginRepository).updateStatus(
            PLUGIN_ID,
            PluginStatus.ERROR,
            "PF4J 插件描述符与 plugin.json 不一致；插件清理失败，需重启"
        );
        verify(lifecycleGuard, never()).enable(anyString());
    }

    @Test
    void restoreShouldRejectJarWhoseDigestDiffersFromInstalledRecord() throws Exception {
        Path jarPath = Files.writeString(tempDir.resolve("replaced-plugin.jar"), "plugin");
        PluginRow stored = storedPlugin(jarPath, "b".repeat(64));
        when(pluginRepository.findAll()).thenReturn(List.of(stored));
        when(signatureVerifier.verify(eq(jarPath), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);

        service.restorePlugins();

        verify(pluginRepository).updateStatus(
            PLUGIN_ID,
            PluginStatus.ERROR,
            "插件摘要与已安装记录不一致"
        );
        verify(versionService, never()).checkCompatibility(any(Path.class));
        verify(pf4jManager, never()).loadPlugin(any(Path.class));
    }

    @Test
    void restoreShouldRejectPluginThatIsNoLongerVersionCompatible() throws Exception {
        Path jarPath = Files.writeString(tempDir.resolve("incompatible-restore.jar"), "plugin");
        PluginRow stored = storedPlugin(jarPath, VERIFIED_SHA256);
        when(pluginRepository.findAll()).thenReturn(List.of(stored));
        when(signatureVerifier.verify(eq(jarPath), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(jarPath))
            .thenThrow(new PluginVersionService.PluginVersionException("宿主版本不兼容"));

        service.restorePlugins();

        verify(pluginRepository).updateStatus(PLUGIN_ID, PluginStatus.ERROR, "宿主版本不兼容");
        verify(pf4jManager, never()).loadPlugin(any(Path.class));
    }

    @Test
    void restoreShouldRejectSignedManifestThatDiffersFromInstalledRecord() throws Exception {
        Path jarPath = Files.writeString(tempDir.resolve("manifest-mismatch.jar"), "plugin");
        PluginRow stored = storedPlugin(jarPath, VERIFIED_SHA256);
        PluginVersionService.PluginVersionDescriptor descriptor = new PluginVersionService.PluginVersionDescriptor(
            "different-plugin",
            "1.0.0",
            "0.1.0",
            "0.1.0",
            "0.2.0",
            "17",
            "FUNCTIONAL",
            "Workforce Hub",
            null,
            List.of()
        );
        when(pluginRepository.findAll()).thenReturn(List.of(stored));
        when(signatureVerifier.verify(eq(jarPath), eq(SIGNER_ID), any(byte[].class)))
            .thenReturn(VERIFIED_SHA256);
        when(versionService.checkCompatibility(jarPath)).thenReturn(descriptor);

        service.restorePlugins();

        verify(pluginRepository).updateStatus(PLUGIN_ID, PluginStatus.ERROR, "插件清单与安装记录不一致");
        verify(pf4jManager, never()).loadPlugin(any(Path.class));
    }

    private PluginRow metadata(byte[] signature) {
        PluginRow row = new PluginRow();
        row.setName("Signed Plugin");
        row.setSignerId(SIGNER_ID);
        row.setSignature(signature);
        return row;
    }

    private void assertSnapshotDirectoryEmpty() throws Exception {
        Path snapshotDirectory = tempDir.resolve("plugins").resolve(".snapshots").toAbsolutePath();
        assertTrue(Files.isDirectory(snapshotDirectory));
        try (var snapshots = Files.list(snapshotDirectory)) {
            assertEquals(0, snapshots.count());
        }
    }

    private PluginRow storedPlugin(Path jarPath, String jarSha256) {
        PluginRow row = metadata(new byte[]{1, 2, 3});
        row.setPluginId(PLUGIN_ID);
        row.setStatus(PluginStatus.ACTIVE);
        row.setJarPath(jarPath.toString());
        row.setJarSha256(jarSha256);
        row.setVersion("1.0.0");
        row.setSdkVersion("0.1.0");
        row.setMinHostVersion("0.1.0");
        row.setMaxHostVersion("0.2.0");
        row.setJavaVersion("17");
        row.setType("FUNCTIONAL");
        row.setProvider("Workforce Hub");
        row.setFrontendEntry(null);
        row.setFrontendSha384(null);
        return row;
    }

    private PluginRow storedUiPlugin(Path jarPath, String jarSha256) {
        PluginRow row = storedPlugin(jarPath, jarSha256);
        row.setType("UI");
        row.setFrontendEntry("/plugins/" + PLUGIN_ID + "/index.js");
        row.setFrontendSha384(SIGNED_FRONTEND_SRI);
        return row;
    }

    private PluginVersionService.PluginVersionDescriptor versionDescriptor() {
        return new PluginVersionService.PluginVersionDescriptor(
            PLUGIN_ID,
            "1.0.0",
            "0.1.0",
            "0.1.0",
            "0.2.0",
            "17",
            "FUNCTIONAL",
            "Workforce Hub",
            null,
            List.of()
        );
    }

    private PluginVersionService.PluginVersionDescriptor uiVersionDescriptor() {
        return new PluginVersionService.PluginVersionDescriptor(
            PLUGIN_ID,
            "1.0.0",
            "0.1.0",
            "0.1.0",
            "0.2.0",
            "17",
            "UI",
            "Workforce Hub",
            SIGNED_FRONTEND_SRI,
            List.of()
        );
    }

    private PluginWrapper wrapper(String version) {
        PluginWrapper wrapper = org.mockito.Mockito.mock(PluginWrapper.class);
        PluginDescriptor descriptor = org.mockito.Mockito.mock(PluginDescriptor.class);
        when(wrapper.getDescriptor()).thenReturn(descriptor);
        return wrapper;
    }
}
