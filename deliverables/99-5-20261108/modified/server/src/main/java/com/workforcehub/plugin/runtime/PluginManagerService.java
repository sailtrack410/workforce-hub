package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.repository.PluginRepository;
import com.workforcehub.plugin.repository.PluginDependencySpec;
import com.workforcehub.plugin.repository.PluginRow;
import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * 插件管理服务 - 整合 PF4J 和数据库持久化。
 * 使用单一 WorkforcePluginManager 实例。
 */
@Service
public class PluginManagerService {

    private static final Logger log = LoggerFactory.getLogger(PluginManagerService.class);
    static final long MAX_JAR_BYTES = 32L * 1024 * 1024;

    private final WorkforcePluginManager pf4jManager;
    private final PluginRepository pluginRepository;
    private final PluginSignatureVerifier signatureVerifier;
    private final PluginVersionService versionService;
    private final PluginFlywayMigrator flywayMigrator;
    private final PluginLifecycleGuard lifecycleGuard;

    public PluginManagerService(
            WorkforcePluginManager pf4jManager,
            PluginRepository pluginRepository,
            PluginSignatureVerifier signatureVerifier,
            PluginVersionService versionService,
            PluginFlywayMigrator flywayMigrator,
            PluginLifecycleGuard lifecycleGuard) {
        this.pf4jManager = pf4jManager;
        this.pluginRepository = pluginRepository;
        this.signatureVerifier = signatureVerifier;
        this.versionService = versionService;
        this.flywayMigrator = flywayMigrator;
        this.lifecycleGuard = lifecycleGuard;
    }

    /**
     * 启动时恢复已启用的插件。
     */
    @EventListener(ApplicationStartedEvent.class)
    public synchronized void restorePlugins() {
        List<PluginRow> restorablePlugins = pluginRepository.findAll().stream()
            .filter(plugin -> plugin.getStatus() == PluginStatus.ACTIVE
                || plugin.getStatus() == PluginStatus.INSTALLED
                || plugin.getStatus() == PluginStatus.STOPPED)
            .toList();
        log.info("恢复 {} 个可用插件", restorablePlugins.size());

        Map<String, RestoreCandidate> candidates = new LinkedHashMap<>();
        for (PluginRow plugin : restorablePlugins) {
            try {
                Path jarPath = Path.of(plugin.getJarPath());
                if (!jarPath.toFile().exists()) {
                    log.warn("插件 JAR 不存在，标记为错误: {}", plugin.getPluginId());
                    recordRestoreFailure(plugin.getPluginId(), "JAR文件不存在", null);
                    continue;
                }

                String jarSha256 = signatureVerifier.verify(
                    jarPath,
                    plugin.getSignerId(),
                    plugin.getSignature()
                );
                if (!jarSha256.equals(plugin.getJarSha256())) {
                    throw new PluginSignatureVerifier.PluginSignatureException("插件摘要与已安装记录不一致");
                }

                PluginVersionService.PluginVersionDescriptor versionDescriptor =
                    versionService.checkCompatibility(jarPath);
                if (!matchesStoredManifest(plugin, versionDescriptor)) {
                    throw new PluginVersionService.PluginVersionException("插件清单与安装记录不一致");
                }
                validateSignedPf4jDescriptor(jarPath, versionDescriptor);
                candidates.put(plugin.getPluginId(), new RestoreCandidate(plugin, jarPath, versionDescriptor));
            } catch (Exception e) {
                String errorMessage = e.getMessage() == null ? "插件恢复失败" : e.getMessage();
                log.error("恢复插件预检失败: {}", plugin.getPluginId(), e);
                recordRestoreFailure(plugin.getPluginId(), errorMessage, e);
            }
        }

        List<RestoreCandidate> orderedCandidates = orderRestoreCandidates(candidates);
        Set<String> runtimeFailures = new HashSet<>();
        for (RestoreCandidate candidate : orderedCandidates) {
            PluginRow plugin = candidate.row();
            String failedDependency = plugin.getDependencies().stream()
                .map(PluginDependencySpec::pluginId)
                .filter(runtimeFailures::contains)
                .findFirst()
                .orElse(null);
            if (failedDependency != null) {
                runtimeFailures.add(plugin.getPluginId());
                recordRestoreFailure(
                    plugin.getPluginId(),
                    "插件依赖恢复失败: " + failedDependency,
                    null
                );
                continue;
            }

            String loadedPluginId = null;
            List<String> pluginIdsBeforeLoad = null;
            try {
                pluginIdsBeforeLoad = loadedPluginIds();
                loadedPluginId = loadTrustedPlugin(candidate.jarPath());
                PluginWrapper wrapper = pf4jManager.getPlugin(loadedPluginId);
                if (!Objects.equals(loadedPluginId, candidate.descriptor().pluginId())
                        || wrapper == null) {
                    throw new PluginVersionService.PluginVersionException(
                        "PF4J 插件描述符与 plugin.json 不一致"
                    );
                }
                versionService.validatePf4jDescriptor(candidate.descriptor(), wrapper.getDescriptor());

                if (plugin.getStatus() == PluginStatus.ACTIVE) {
                    PluginState state = pf4jManager.startPlugin(loadedPluginId);
                    if (state != PluginState.STARTED) {
                        throw new IllegalStateException("启动失败: " + state);
                    }
                    lifecycleGuard.enable(loadedPluginId);
                }
                log.info("恢复插件成功: {} ({})", loadedPluginId, plugin.getStatus());
            } catch (Exception e) {
                runtimeFailures.add(plugin.getPluginId());
                String errorMessage = e.getMessage() == null ? "插件恢复失败" : e.getMessage();
                String failedPluginId = findFailedPluginId(
                    loadedPluginId,
                    candidate.jarPath(),
                    pluginIdsBeforeLoad
                );
                if (!unloadFailedPlugin(failedPluginId, e)) {
                    errorMessage += "；插件清理失败，需重启";
                }
                log.error("恢复插件失败: {}", plugin.getPluginId(), e);
                recordRestoreFailure(plugin.getPluginId(), errorMessage, e);
            }
        }
    }

    private List<RestoreCandidate> orderRestoreCandidates(
            Map<String, RestoreCandidate> verifiedCandidates) {
        Map<String, RestoreCandidate> candidates = new LinkedHashMap<>(verifiedCandidates);
        boolean removed;
        do {
            removed = false;
            for (RestoreCandidate candidate : new ArrayList<>(candidates.values())) {
                String conflict = restoreDependencyConflict(candidate, candidates);
                if (conflict == null) {
                    continue;
                }
                candidates.remove(candidate.row().getPluginId());
                recordRestoreFailure(candidate.row().getPluginId(), conflict, null);
                removed = true;
            }
        } while (removed);

        List<RestoreCandidate> ordered = new ArrayList<>();
        Set<String> resolved = new HashSet<>();
        while (ordered.size() < candidates.size()) {
            boolean progressed = false;
            for (RestoreCandidate candidate : candidates.values()) {
                String pluginId = candidate.row().getPluginId();
                if (resolved.contains(pluginId)) {
                    continue;
                }
                boolean ready = candidate.row().getDependencies().stream()
                    .map(PluginDependencySpec::pluginId)
                    .allMatch(resolved::contains);
                if (ready) {
                    ordered.add(candidate);
                    resolved.add(pluginId);
                    progressed = true;
                }
            }
            if (!progressed) {
                for (RestoreCandidate candidate : candidates.values()) {
                    if (!resolved.contains(candidate.row().getPluginId())) {
                        recordRestoreFailure(
                            candidate.row().getPluginId(),
                            "插件依赖存在循环",
                            null
                        );
                    }
                }
                break;
            }
        }
        return ordered;
    }

    private String restoreDependencyConflict(
            RestoreCandidate candidate,
            Map<String, RestoreCandidate> candidates) {
        for (PluginDependencySpec dependency : candidate.row().getDependencies()) {
            RestoreCandidate required = candidates.get(dependency.pluginId());
            if (required == null) {
                return "插件依赖不可用: " + dependency.pluginId();
            }
            try {
                if (!versionService.matchesVersion(dependency, required.row().getVersion())) {
                    return "插件依赖版本不兼容: " + dependency.pluginId();
                }
            } catch (PluginVersionService.PluginVersionException e) {
                return "插件依赖版本记录无效: " + dependency.pluginId();
            }
            if (candidate.row().getStatus() == PluginStatus.ACTIVE
                    && required.row().getStatus() != PluginStatus.ACTIVE) {
                return "已启用插件依赖未启用: " + dependency.pluginId();
            }
        }
        return null;
    }

    private boolean matchesStoredManifest(
            PluginRow plugin,
            PluginVersionService.PluginVersionDescriptor descriptor) {
        return Objects.equals(plugin.getPluginId(), descriptor.pluginId())
            && Objects.equals(plugin.getVersion(), descriptor.pluginVersion())
            && Objects.equals(plugin.getSdkVersion(), descriptor.sdkVersion())
            && Objects.equals(plugin.getMinHostVersion(), descriptor.minHostVersion())
            && Objects.equals(plugin.getMaxHostVersion(), descriptor.maxHostVersion())
            && Objects.equals(plugin.getJavaVersion(), descriptor.javaVersion())
            && Objects.equals(plugin.getType(), descriptor.type())
            && Objects.equals(plugin.getProvider(), descriptor.provider())
            && Objects.equals(
                plugin.getFrontendEntry(),
                "UI".equals(descriptor.type())
                    ? "/plugins/" + descriptor.pluginId() + "/index.js"
                    : null
            )
            && Objects.equals(plugin.getFrontendSha384(), descriptor.frontendSha384())
            && Objects.equals(plugin.getDependencies(), descriptor.dependencies());
    }

    private void recordRestoreFailure(String pluginId, String errorMessage, Exception restoreError) {
        try {
            pluginRepository.updateStatus(pluginId, PluginStatus.ERROR, errorMessage);
        } catch (RuntimeException statusError) {
            if (restoreError != null) {
                restoreError.addSuppressed(statusError);
            }
            log.error("记录插件恢复失败状态失败: {}", pluginId, statusError);
        }
    }

    /**
     * 安装插件。
     * 包含：加载 JAR、执行数据库迁移、入库。
     * 签名校验必须先于 PF4J 加载。
     * 版本信息只信任已签名 JAR 内的 plugin.json。
     */
    public synchronized PluginRow install(Path jarPath, PluginRow metadata) {
        return install(jarPath, metadata, true);
    }

    /**
     * 管理端首次安装入口，不允许借由上传接口替换历史插件 ID。
     */
    public synchronized PluginRow installNew(Path jarPath, PluginRow metadata) {
        return install(jarPath, metadata, false);
    }

    private PluginRow install(Path jarPath, PluginRow metadata, boolean allowReplacement) {
        Path snapshotPath = createJarSnapshot(jarPath);
        String pluginId = null;
        List<String> pluginIdsBeforeLoad = null;
        PluginRow row = null;
        PluginRow previousInstallation = null;
        boolean persistenceAttempted = false;
        try {
            byte[] submittedSignature = metadata.getSignature();
            byte[] verifiedSignature = submittedSignature == null
                ? null
                : submittedSignature.clone();
            String verifiedSignerId = metadata.getSignerId();
            String verifiedJarSha256 = signatureVerifier.verify(
                snapshotPath,
                verifiedSignerId,
                verifiedSignature
            );
            PluginVersionService.PluginVersionDescriptor versionDescriptor =
                versionService.checkCompatibility(snapshotPath);
            validateSignedPf4jDescriptor(snapshotPath, versionDescriptor);

            // 1. 构建数据库记录并完成迁移前预检
            row = new PluginRow();
            row.setPluginId(versionDescriptor.pluginId());
            row.setName(metadata.getName() != null ? metadata.getName() : versionDescriptor.pluginId());
            row.setDescription(metadata.getDescription());
            row.setVersion(versionDescriptor.pluginVersion());
            row.setSdkVersion(versionDescriptor.sdkVersion());
            row.setMinHostVersion(versionDescriptor.minHostVersion());
            row.setMaxHostVersion(versionDescriptor.maxHostVersion());
            row.setJavaVersion(versionDescriptor.javaVersion());
            row.setType(versionDescriptor.type());
            row.setStatus(PluginStatus.INSTALLED);
            row.setJarPath(snapshotPath.toString());
            row.setJarSha256(verifiedJarSha256);
            row.setSignature(verifiedSignature);
            row.setSignerId(verifiedSignerId);
            row.setFrontendEntry("UI".equals(versionDescriptor.type())
                ? "/plugins/" + versionDescriptor.pluginId() + "/index.js"
                : null);
            row.setFrontendSha384(versionDescriptor.frontendSha384());
            row.setProvider(versionDescriptor.provider());
            row.setDependencies(versionDescriptor.dependencies());
            row.setInstalledAt(LocalDateTime.now());
            row.setUpdatedAt(LocalDateTime.now());

            validateInstallRow(row);
            Optional<PluginRow> previousRow = pluginRepository.findById(row.getPluginId());
            if (previousRow.isPresent()) {
                if (!allowReplacement) {
                    throw new PluginAlreadyExistsException(row.getPluginId());
                }
                PluginRow previous = previousRow.get();
                if (previous.getStatus() != PluginStatus.UNINSTALLED) {
                    throw new IllegalStateException("插件已存在: " + row.getPluginId());
                }
                if (!Objects.equals(previous.getSignerId(), row.getSignerId())) {
                    throw new IllegalStateException("插件签名者与原安装记录不一致: " + row.getPluginId());
                }
                if (pf4jManager.getPlugin(row.getPluginId()) != null) {
                    throw new IllegalStateException("已卸载插件仍存在运行时实例: " + row.getPluginId());
                }
                previousInstallation = previous;
            }

            // 2. 加载插件获取 PF4J 分配的 ID
            pluginIdsBeforeLoad = loadedPluginIds();
            pluginId = loadTrustedPlugin(snapshotPath);
            PluginWrapper wrapper = pf4jManager.getPlugin(pluginId);
            if (wrapper == null) {
                throw new IllegalStateException("插件加载后未找到: " + pluginId);
            }
            if (!pluginId.equals(versionDescriptor.pluginId())) {
                throw new PluginVersionService.PluginVersionException(
                    "PF4J 插件描述符与 plugin.json 不一致"
                );
            }
            versionService.validatePf4jDescriptor(versionDescriptor, wrapper.getDescriptor());

            // 3. 执行数据库迁移
            flywayMigrator.migrate(pluginId, snapshotPath);

            // 4. 持久化安装记录
            if (previousRow.isEmpty()) {
                persistenceAttempted = true;
                pluginRepository.insert(row);
            } else {
                PluginRow previous = previousRow.get();
                persistenceAttempted = true;
                if (!pluginRepository.replaceUninstalled(
                        row,
                        previous.getJarPath(),
                        previous.getSignerId())) {
                    throw new IllegalStateException("插件状态已变化，替换安装失败: " + pluginId);
                }
                deletePreviousSnapshot(row.getPluginId(), previous.getJarPath(), snapshotPath);
            }
            return row;
        } catch (RuntimeException installError) {
            if (persistenceAttempted && row != null) {
                Optional<PluginRow> persisted;
                try {
                    persisted = pluginRepository.findById(row.getPluginId());
                } catch (RuntimeException readbackError) {
                    installError.addSuppressed(new IllegalStateException(
                        "插件安装提交结果无法确认，需重启",
                        readbackError
                    ));
                    throw installError;
                }
                if (persisted.isPresent()) {
                    PluginRow stored = persisted.get();
                    boolean candidatePersisted = Objects.equals(stored.getJarPath(), row.getJarPath())
                        && Objects.equals(stored.getJarSha256(), row.getJarSha256())
                        && Objects.equals(stored.getSignerId(), row.getSignerId())
                        && Objects.equals(stored.getVersion(), row.getVersion())
                        && Objects.equals(stored.getDependencies(), row.getDependencies());
                    if (candidatePersisted && stored.getStatus() == PluginStatus.INSTALLED) {
                        if (previousInstallation != null) {
                            deletePreviousSnapshot(
                                row.getPluginId(),
                                previousInstallation.getJarPath(),
                                snapshotPath
                            );
                        }
                        log.warn("插件安装提交异常，但回读确认已持久化: {}", row.getPluginId());
                        return stored;
                    }
                    if (candidatePersisted) {
                        installError.addSuppressed(new IllegalStateException(
                            "插件安装提交后状态已再次变化，需重启"
                        ));
                        throw installError;
                    }
                }
            }
            String failedPluginId = findFailedPluginId(pluginId, snapshotPath, pluginIdsBeforeLoad);
            if (unloadFailedPlugin(failedPluginId, installError)) {
                deleteUnpersistedSnapshot(snapshotPath, installError);
            }
            throw installError;
        }
    }

    private void validateSignedPf4jDescriptor(
            Path jarPath,
            PluginVersionService.PluginVersionDescriptor descriptor) {
        try {
            PluginDescriptor pf4jDescriptor = pf4jManager.readPluginDescriptor(jarPath);
            versionService.validatePf4jDescriptor(descriptor, pf4jDescriptor);
        } catch (PluginVersionService.PluginVersionException e) {
            throw e;
        } catch (PluginRuntimeException | IllegalArgumentException e) {
            throw new PluginVersionService.PluginVersionException(
                "PF4J 插件描述符校验失败",
                e
            );
        }
    }

    private String loadTrustedPlugin(Path jarPath) {
        try {
            return pf4jManager.loadPlugin(jarPath);
        } catch (PluginRuntimeException | IllegalArgumentException e) {
            throw new PluginVersionService.PluginVersionException(
                "插件依赖或描述符校验失败",
                e
            );
        }
    }

    private List<String> loadedPluginIds() {
        return pf4jManager.getPlugins().stream()
            .map(PluginWrapper::getPluginId)
            .toList();
    }

    private String findFailedPluginId(
            String loadedPluginId,
            Path pluginPath,
            List<String> pluginIdsBeforeLoad) {
        if (loadedPluginId != null) {
            return loadedPluginId;
        }
        if (pluginPath == null || pluginIdsBeforeLoad == null) {
            return null;
        }
        Path normalizedPluginPath = pluginPath.toAbsolutePath().normalize();
        return pf4jManager.getPlugins().stream()
            .filter(wrapper -> !pluginIdsBeforeLoad.contains(wrapper.getPluginId()))
            .filter(wrapper -> normalizedPluginPath.equals(
                wrapper.getPluginPath().toAbsolutePath().normalize()
            ))
            .map(PluginWrapper::getPluginId)
            .findFirst()
            .orElse(null);
    }

    private boolean unloadFailedPlugin(String pluginId, Exception failure) {
        if (pluginId == null) {
            return true;
        }
        try {
            if (pf4jManager.unloadPluginWithoutDependents(pluginId)) {
                return true;
            }
            failure.addSuppressed(new IllegalStateException("插件清理失败: " + pluginId));
        } catch (RuntimeException unloadError) {
            failure.addSuppressed(new IllegalStateException(
                "插件清理失败: " + pluginId,
                unloadError
            ));
        }
        return false;
    }

    private void validateInstallRow(PluginRow row) {
        validateMaxLength("pluginId", row.getPluginId(), 64);
        validateMaxLength("name", row.getName(), 128);
        validateMaxLength("description", row.getDescription(), 512);
        validateMaxLength("type", row.getType(), 32);
        validateMaxLength("jarPath", row.getJarPath(), 512);
        validateMaxLength("jarSha256", row.getJarSha256(), 64);
        validateMaxLength("signerId", row.getSignerId(), 128);
        validateMaxLength("frontendEntry", row.getFrontendEntry(), 512);
        validateMaxLength("frontendSha384", row.getFrontendSha384(), 96);
        validateMaxLength("provider", row.getProvider(), 128);
        if (row.getSignature() != null && row.getSignature().length > 65535) {
            throw new IllegalArgumentException("插件字段超长: signature");
        }
    }

    private void validateMaxLength(String field, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException("插件字段超长: " + field);
        }
    }

    private Path createJarSnapshot(Path sourceJar) {
        Path snapshotPath = null;
        try {
            Path snapshotDirectory = pf4jManager.getPluginsRoot()
                .toAbsolutePath()
                .normalize()
                .resolve(".snapshots");
            Files.createDirectories(snapshotDirectory);
            if (!Files.isRegularFile(sourceJar)) {
                throw new IllegalArgumentException("插件 JAR 不存在或不是普通文件");
            }
            if (Files.size(sourceJar) > MAX_JAR_BYTES) {
                throw new PluginPackageTooLargeException();
            }
            snapshotPath = Files.createTempFile(snapshotDirectory, "plugin-", ".jar");
            try (InputStream input = Files.newInputStream(sourceJar);
                 OutputStream output = Files.newOutputStream(snapshotPath)) {
                byte[] buffer = new byte[8192];
                long copied = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    copied += read;
                    if (copied > MAX_JAR_BYTES) {
                        throw new PluginPackageTooLargeException();
                    }
                    output.write(buffer, 0, read);
                }
            }
            return snapshotPath;
        } catch (IOException | RuntimeException e) {
            if (snapshotPath != null) {
                try {
                    Files.deleteIfExists(snapshotPath);
                } catch (IOException | RuntimeException cleanupError) {
                    e.addSuppressed(cleanupError);
                }
            }
            if (e instanceof PluginPackageTooLargeException tooLarge) {
                throw tooLarge;
            }
            throw new IllegalStateException("创建插件 JAR 快照失败", e);
        }
    }

    private void deleteUnpersistedSnapshot(Path snapshotPath, RuntimeException installError) {
        try {
            Files.deleteIfExists(snapshotPath);
        } catch (IOException | RuntimeException cleanupError) {
            installError.addSuppressed(new IllegalStateException("删除未持久化插件 JAR 快照失败", cleanupError));
        }
    }

    private void deletePreviousSnapshot(
            String pluginId,
            String previousJarPath,
            Path currentSnapshotPath) {
        try {
            Path snapshotDirectory = pf4jManager.getPluginsRoot()
                .toAbsolutePath()
                .normalize()
                .resolve(".snapshots");
            Path previousSnapshotPath = Path.of(previousJarPath).toAbsolutePath().normalize();
            if (!previousSnapshotPath.equals(currentSnapshotPath.toAbsolutePath().normalize())
                    && snapshotDirectory.equals(previousSnapshotPath.getParent())
                    && previousSnapshotPath.getFileName().toString().startsWith("plugin-")
                    && previousSnapshotPath.getFileName().toString().endsWith(".jar")
                    && Files.isRegularFile(previousSnapshotPath)
                    && !pluginRepository.isJarPathReferencedByAnotherPlugin(
                        previousJarPath,
                        pluginId
                    )) {
                Files.deleteIfExists(previousSnapshotPath);
            }
        } catch (IOException | RuntimeException cleanupError) {
            log.warn("删除旧插件 JAR 快照失败，替换安装结果保持有效", cleanupError);
        }
    }

    /**
     * 启用插件。
     * PF4J 真正 STARTED 且状态更新成功后才开门。
     */
    public synchronized LocalDateTime enable(String pluginId) {
        PluginRow plugin = pluginRepository.findById(pluginId)
            .orElseThrow(() -> new NoSuchElementException("插件不存在: " + pluginId));
        validateDependenciesForEnable(plugin);

        // 1. 启动插件
        PluginWrapper wrapper = pf4jManager.getPlugin(pluginId);
        boolean alreadyStarted = wrapper != null
            && wrapper.getPluginState() == PluginState.STARTED;
        PluginState state = pf4jManager.startPlugin(pluginId);
        if (state != PluginState.STARTED) {
            RuntimeException startError = new RuntimeException("插件启动失败: " + state);
            try {
                pluginRepository.updateStatus(pluginId, PluginStatus.ERROR, startError.getMessage());
            } catch (RuntimeException statusError) {
                startError.addSuppressed(statusError);
            }
            throw startError;
        }

        // 2. 更新数据库状态
        LocalDateTime updatedAt;
        try {
            updatedAt = pluginRepository.updateStatus(pluginId, PluginStatus.ACTIVE);
        } catch (RuntimeException updateError) {
            if (!alreadyStarted) {
                try {
                    PluginState rollbackState = pf4jManager.stopPlugin(pluginId);
                    if (rollbackState != PluginState.STOPPED) {
                        updateError.addSuppressed(new IllegalStateException(
                            "插件启动回滚失败: " + rollbackState
                        ));
                    }
                } catch (RuntimeException stopError) {
                    updateError.addSuppressed(new IllegalStateException("插件启动回滚失败", stopError));
                }
            }
            throw updateError;
        }

        // 3. 开门接受请求
        lifecycleGuard.enable(pluginId);
        return updatedAt;
    }

    private void validateDependenciesForEnable(PluginRow plugin) {
        for (PluginDependencySpec dependency : plugin.getDependencies()) {
            PluginRow required = pluginRepository.findById(dependency.pluginId())
                .orElseThrow(() -> new PluginDependencyConflictException(
                    "插件依赖不存在: " + dependency.pluginId()
                ));
            PluginWrapper requiredWrapper = pf4jManager.getPlugin(dependency.pluginId());
            boolean compatible;
            try {
                compatible = versionService.matchesVersion(dependency, required.getVersion());
            } catch (PluginVersionService.PluginVersionException e) {
                throw new PluginDependencyConflictException(
                    "插件依赖版本记录无效: " + dependency.pluginId()
                );
            }
            if (required.getStatus() != PluginStatus.ACTIVE
                    || requiredWrapper == null
                    || requiredWrapper.getPluginState() != PluginState.STARTED
                    || !compatible) {
                throw new PluginDependencyConflictException(
                    "插件依赖未启用或版本不兼容: " + dependency.pluginId()
                );
            }
        }
    }

    /**
     * 停用插件。
     * 关门 → 等排空 → stop → 更新状态。
     * 排空超时或中断时，绝不能继续 stop；入口保持关闭，抛出异常，标记 ERROR。
     */
    public synchronized LocalDateTime disable(String pluginId) {
        rejectLoadedDependents(
            pluginId,
            "停用",
            pf4jManager.getPlugin(pluginId) != null
        );

        // 1. 关门，拒绝新请求
        lifecycleGuard.beginDisable(pluginId);

        try {
            pluginRepository.updateStatus(pluginId, PluginStatus.DISABLING);
            // 2. 等待在途请求排空
            if (!lifecycleGuard.awaitDrain(pluginId)) {
                // 排空失败，不继续 stop，标记 ERROR
                pluginRepository.updateStatus(pluginId, PluginStatus.ERROR, "排空超时或中断，需重启");
                throw new RuntimeException("插件排空超时或中断，需重启: " + pluginId);
            }

            // 3. PF4J stop
            PluginState state = pf4jManager.stopPlugin(pluginId);
            if (state != PluginState.STOPPED) {
                pluginRepository.updateStatus(pluginId, PluginStatus.ERROR, "停止失败: " + state);
                throw new RuntimeException("插件停止失败: " + state);
            }

            // 4. 更新状态
            return pluginRepository.updateStatus(pluginId, PluginStatus.STOPPED);
        } catch (RuntimeException e) {
            // 入口保持关闭，不重新开门
            if (!pluginRepository.findById(pluginId)
                    .map(p -> p.getStatus() == PluginStatus.ERROR)
                    .orElse(false)) {
                pluginRepository.updateStatus(pluginId, PluginStatus.ERROR, e.getMessage());
            }
            throw e;
        }
    }

    /**
     * 卸载插件（保留数据库数据）。
     * 关门 → 等排空 → stop → unload → 清理守卫。
     * 排空超时或中断时，绝不能继续 unload；入口保持关闭，抛出异常，标记 ERROR。
     */
    public synchronized LocalDateTime uninstall(String pluginId) {
        PluginWrapper wrapper = pf4jManager.getPlugin(pluginId);
        rejectLoadedDependents(pluginId, "卸载", wrapper != null);

        // 先停止
        if (wrapper != null) {
            // 1. 关门，拒绝新请求
            lifecycleGuard.beginDisable(pluginId);

            try {
                pluginRepository.updateStatus(pluginId, PluginStatus.DISABLING);

                // 2. 等待在途请求排空
                if (!lifecycleGuard.awaitDrain(pluginId)) {
                    pluginRepository.updateStatus(pluginId, PluginStatus.ERROR, "排空超时或中断，需重启");
                    throw new RuntimeException("插件排空超时或中断，需重启: " + pluginId);
                }

                // 3. PF4J stop — 校验返回状态，抛异常也标记 ERROR
                PluginState currentState = wrapper.getPluginState();
                if (currentState == PluginState.STARTED || currentState == PluginState.FAILED) {
                    PluginState stopState;
                    try {
                        stopState = pf4jManager.stopPlugin(pluginId);
                    } catch (RuntimeException e) {
                        pluginRepository.updateStatus(pluginId, PluginStatus.ERROR, "停止异常: " + e.getMessage());
                        throw e;
                    }
                    if (stopState != PluginState.STOPPED) {
                        pluginRepository.updateStatus(pluginId, PluginStatus.ERROR, "停止失败: " + stopState);
                        throw new RuntimeException("插件停止失败: " + stopState);
                    }
                }

                // 4. PF4J unload — 校验返回值，抛异常也标记 ERROR
                boolean unloaded;
                try {
                    unloaded = pf4jManager.unloadPlugin(pluginId);
                } catch (RuntimeException e) {
                    pluginRepository.updateStatus(pluginId, PluginStatus.ERROR, "卸载异常: " + e.getMessage());
                    throw e;
                }
                if (!unloaded) {
                    pluginRepository.updateStatus(pluginId, PluginStatus.ERROR, "卸载失败");
                    throw new RuntimeException("插件卸载失败: " + pluginId);
                }
            } catch (RuntimeException e) {
                if (!pluginRepository.findById(pluginId)
                        .map(p -> p.getStatus() == PluginStatus.ERROR)
                        .orElse(false)) {
                    pluginRepository.updateStatus(pluginId, PluginStatus.ERROR, e.getMessage());
                }
                throw e;
            }
        }

        // 5. 清理守卫并标记已卸载
        lifecycleGuard.remove(pluginId);
        // 更新状态为 UNINSTALLED，不删除数据库记录
        return pluginRepository.updateStatus(pluginId, PluginStatus.UNINSTALLED, "已卸载");
    }

    private void rejectLoadedDependents(
            String pluginId,
            String operation,
            boolean targetLoaded) {
        Set<String> dependents = new TreeSet<>();
        if (targetLoaded) {
            dependents.addAll(pf4jManager.getDependentPluginIds(pluginId));
        }
        List<String> persistedDependents = pluginRepository.findDependentPluginIds(pluginId);
        if (persistedDependents != null) {
            dependents.addAll(persistedDependents);
        }
        if (!dependents.isEmpty()) {
            throw new PluginDependencyConflictException(
                "插件仍被其他插件依赖，不能" + operation + ": "
                    + pluginId + " <- " + String.join(",", dependents)
            );
        }
    }

    static final class PluginDependencyConflictException extends IllegalStateException {
        PluginDependencyConflictException(String message) {
            super(message);
        }
    }

    static final class PluginAlreadyExistsException extends IllegalStateException {
        private final String pluginId;

        PluginAlreadyExistsException(String pluginId) {
            super("插件已存在: " + pluginId);
            this.pluginId = pluginId;
        }

        String getPluginId() {
            return pluginId;
        }
    }

    static final class PluginPackageTooLargeException extends IllegalArgumentException {
        PluginPackageTooLargeException() {
            super("插件 JAR 超过 32 MiB");
        }
    }

    /**
     * 获取所有插件。
     */
    public List<PluginRow> listPlugins() {
        return pluginRepository.findAll();
    }

    /**
     * 获取单个插件。
     */
    public PluginRow getPlugin(String pluginId) {
        return pluginRepository.findById(pluginId)
            .orElseThrow(() -> new NoSuchElementException("插件不存在: " + pluginId));
    }

    /**
     * 获取运行中的插件（员工端用）。
     */
    public List<PluginRow> getActivePlugins() {
        return pluginRepository.findByStatus(PluginStatus.ACTIVE);
    }

    /**
     * 发现指定插件的动作处理器。
     * 不再二次检查数据库状态——网关 acquire 成功后 guard 是权威。
     *
     * @param pluginId   插件ID
     * @param actionCode 动作编码
     * @return 动作处理器
     */
    public Optional<PluginActionHandler> findActionHandler(String pluginId, String actionCode) {
        // 检查插件状态
        // 状态已由网关在 acquire 前检查，acquire 后 guard 是权威
        // 从 PF4J 获取扩展
        List<PluginActionHandler> handlers = pf4jManager.getExtensions(PluginActionHandler.class, pluginId);
        return handlers.stream()
            .filter(h -> h.getDescriptor().actionCode().equals(actionCode))
            .findFirst();
    }

    /**
     * 发现所有活跃插件的动作处理器。
     */
    public List<PluginActionHandler> findAllActionHandlers() {
        return pf4jManager.getExtensions(PluginActionHandler.class);
    }

    /**
     * 获取 PF4J 管理器（用于扩展查询）。
     */
    public org.pf4j.PluginManager getPf4jManager() {
        return pf4jManager;
    }

    private record RestoreCandidate(
        PluginRow row,
        Path jarPath,
        PluginVersionService.PluginVersionDescriptor descriptor
    ) {
    }
}
