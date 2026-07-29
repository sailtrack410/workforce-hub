package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.repository.PluginRepository;
import com.workforcehub.plugin.repository.PluginRow;
import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * 插件管理服务 - 整合 PF4J 和数据库持久化。
 * 使用单一 WorkforcePluginManager 实例。
 */
@Service
public class PluginManagerService {

    private static final Logger log = LoggerFactory.getLogger(PluginManagerService.class);

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

        for (PluginRow plugin : restorablePlugins) {
            String loadedPluginId = null;
            Path loadingJarPath = null;
            List<String> pluginIdsBeforeLoad = null;
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
                if (!Objects.equals(plugin.getPluginId(), versionDescriptor.pluginId())
                        || !Objects.equals(plugin.getVersion(), versionDescriptor.pluginVersion())
                        || !Objects.equals(plugin.getSdkVersion(), versionDescriptor.sdkVersion())
                        || !Objects.equals(plugin.getMinHostVersion(), versionDescriptor.minHostVersion())
                        || !Objects.equals(plugin.getMaxHostVersion(), versionDescriptor.maxHostVersion())
                        || !Objects.equals(plugin.getJavaVersion(), versionDescriptor.javaVersion())
                        || !Objects.equals(plugin.getType(), versionDescriptor.type())
                        || !Objects.equals(plugin.getProvider(), versionDescriptor.provider())
                        || !Objects.equals(
                            plugin.getFrontendEntry(),
                            "UI".equals(versionDescriptor.type())
                                ? "/plugins/" + versionDescriptor.pluginId() + "/index.js"
                                : null
                        )
                        || !Objects.equals(
                            plugin.getFrontendSha384(),
                            versionDescriptor.frontendSha384()
                        )) {
                    throw new PluginVersionService.PluginVersionException("插件清单与安装记录不一致");
                }

                pluginIdsBeforeLoad = loadedPluginIds();
                loadingJarPath = jarPath;
                loadedPluginId = pf4jManager.loadPlugin(jarPath);
                if (!Objects.equals(loadedPluginId, versionDescriptor.pluginId())) {
                    throw new IllegalStateException("PF4J 插件标识与 plugin.json 不一致");
                }
                PluginWrapper wrapper = pf4jManager.getPlugin(loadedPluginId);
                if (wrapper == null
                        || !Objects.equals(
                            wrapper.getDescriptor().getVersion(),
                            versionDescriptor.pluginVersion()
                        )) {
                    throw new IllegalStateException("PF4J 插件标识与 plugin.json 不一致");
                }

                if (plugin.getStatus() == PluginStatus.ACTIVE) {
                    PluginState state = pf4jManager.startPlugin(loadedPluginId);
                    if (state != PluginState.STARTED) {
                        throw new IllegalStateException("启动失败: " + state);
                    }
                    lifecycleGuard.enable(loadedPluginId);
                }
                log.info("恢复插件成功: {} ({})", loadedPluginId, plugin.getStatus());
            } catch (Exception e) {
                String errorMessage = e.getMessage() == null ? "插件恢复失败" : e.getMessage();
                String failedPluginId = findFailedPluginId(
                    loadedPluginId,
                    loadingJarPath,
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
        Path snapshotPath = createJarSnapshot(jarPath);
        String pluginId = null;
        List<String> pluginIdsBeforeLoad = null;
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

            // 1. 构建数据库记录并完成迁移前预检
            PluginRow row = new PluginRow();
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
            row.setInstalledAt(LocalDateTime.now());
            row.setUpdatedAt(LocalDateTime.now());

            validateInstallRow(row);
            if (pluginRepository.exists(row.getPluginId())) {
                throw new IllegalStateException("插件已存在: " + row.getPluginId());
            }

            // 2. 加载插件获取 PF4J 分配的 ID
            pluginIdsBeforeLoad = loadedPluginIds();
            pluginId = pf4jManager.loadPlugin(snapshotPath);
            PluginWrapper wrapper = pf4jManager.getPlugin(pluginId);
            if (wrapper == null) {
                throw new IllegalStateException("插件加载后未找到: " + pluginId);
            }
            if (!pluginId.equals(versionDescriptor.pluginId())
                    || !wrapper.getDescriptor().getVersion().equals(versionDescriptor.pluginVersion())) {
                throw new IllegalStateException("PF4J 插件标识与 plugin.json 不一致");
            }

            // 3. 执行数据库迁移
            flywayMigrator.migrate(pluginId, snapshotPath);

            // 4. 持久化安装记录
            pluginRepository.insert(row);
            return row;
        } catch (RuntimeException installError) {
            String failedPluginId = findFailedPluginId(pluginId, snapshotPath, pluginIdsBeforeLoad);
            if (unloadFailedPlugin(failedPluginId, installError)) {
                deleteUnpersistedSnapshot(snapshotPath, installError);
            }
            throw installError;
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
            snapshotPath = Files.createTempFile(snapshotDirectory, "plugin-", ".jar");
            Files.copy(sourceJar, snapshotPath, StandardCopyOption.REPLACE_EXISTING);
            return snapshotPath;
        } catch (IOException | RuntimeException e) {
            if (snapshotPath != null) {
                try {
                    Files.deleteIfExists(snapshotPath);
                } catch (IOException | RuntimeException cleanupError) {
                    e.addSuppressed(cleanupError);
                }
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

    /**
     * 启用插件。
     * PF4J 真正 STARTED 且状态更新成功后才开门。
     */
    public synchronized void enable(String pluginId) {
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
        try {
            pluginRepository.updateStatus(pluginId, PluginStatus.ACTIVE);
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
    }

    /**
     * 停用插件。
     * 关门 → 等排空 → stop → 更新状态。
     * 排空超时或中断时，绝不能继续 stop；入口保持关闭，抛出异常，标记 ERROR。
     */
    public synchronized void disable(String pluginId) {
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
            pluginRepository.updateStatus(pluginId, PluginStatus.STOPPED);
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
    public synchronized void uninstall(String pluginId) {
        // 先停止
        if (pf4jManager.getPlugin(pluginId) != null) {
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
        pluginRepository.updateStatus(pluginId, PluginStatus.UNINSTALLED, "已卸载");
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
}
