package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.repository.PluginRepository;
import com.workforcehub.plugin.repository.PluginRow;
import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
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
    private final PluginFlywayMigrator flywayMigrator;

    public PluginManagerService(
            WorkforcePluginManager pf4jManager,
            PluginRepository pluginRepository,
            PluginFlywayMigrator flywayMigrator) {
        this.pf4jManager = pf4jManager;
        this.pluginRepository = pluginRepository;
        this.flywayMigrator = flywayMigrator;
    }

    /**
     * 启动时恢复已启用的插件。
     */
    public void restorePlugins() {
        List<PluginRow> activePlugins = pluginRepository.findByStatus(PluginStatus.ACTIVE);
        log.info("恢复 {} 个已启用的插件", activePlugins.size());

        for (PluginRow plugin : activePlugins) {
            try {
                Path jarPath = Path.of(plugin.getJarPath());
                if (!jarPath.toFile().exists()) {
                    log.warn("插件 JAR 不存在，标记为错误: {}", plugin.getPluginId());
                    pluginRepository.updateStatus(plugin.getPluginId(), PluginStatus.ERROR, "JAR文件不存在");
                    continue;
                }

                String pluginId = pf4jManager.loadPlugin(jarPath);
                pf4jManager.startPlugin(pluginId);
                log.info("恢复插件成功: {}", pluginId);
            } catch (Exception e) {
                log.error("恢复插件失败: {}", plugin.getPluginId(), e);
                pluginRepository.updateStatus(plugin.getPluginId(), PluginStatus.ERROR, e.getMessage());
            }
        }
    }

    /**
     * 安装插件。
     * 包含：加载 JAR、执行数据库迁移、入库。
     */
    public PluginRow install(Path jarPath, PluginRow metadata) {
        // 1. 加载插件获取 PF4J 分配的 ID
        String pluginId = pf4jManager.loadPlugin(jarPath);
        try {
            PluginWrapper wrapper = pf4jManager.getPlugin(pluginId);
            if (wrapper == null) {
                throw new IllegalStateException("插件加载后未找到: " + pluginId);
            }

            // 2. 执行数据库迁移
            flywayMigrator.migrate(pluginId, jarPath);

            // 3. 构建数据库记录
            PluginRow row = new PluginRow();
            row.setPluginId(pluginId);
            row.setName(metadata.getName() != null ? metadata.getName() : pluginId);
            row.setDescription(metadata.getDescription());
            row.setVersion(wrapper.getDescriptor().getVersion());
            row.setSdkVersion(metadata.getSdkVersion() != null ? metadata.getSdkVersion() : "1.0.0");
            row.setMinHostVersion(metadata.getMinHostVersion());
            row.setMaxHostVersion(metadata.getMaxHostVersion());
            row.setJavaVersion(metadata.getJavaVersion() != null ? metadata.getJavaVersion() : "17");
            row.setType(metadata.getType() != null ? metadata.getType() : "FUNCTIONAL");
            row.setStatus(PluginStatus.INSTALLED);
            row.setJarPath(jarPath.toAbsolutePath().toString());
            row.setJarSha256(metadata.getJarSha256());
            row.setSignature(metadata.getSignature());
            row.setSignerId(metadata.getSignerId());
            row.setFrontendEntry(metadata.getFrontendEntry());
            row.setFrontendSha384(metadata.getFrontendSha384());
            row.setProvider(metadata.getProvider());
            row.setInstalledAt(LocalDateTime.now());
            row.setUpdatedAt(LocalDateTime.now());

            pluginRepository.insert(row);
            return row;
        } catch (RuntimeException installError) {
            try {
                pf4jManager.unloadPlugin(pluginId);
            } catch (RuntimeException unloadError) {
                installError.addSuppressed(unloadError);
            }
            throw installError;
        }
    }

    /**
     * 启用插件。
     */
    public void enable(String pluginId) {
        PluginState state = pf4jManager.startPlugin(pluginId);
        if (state == PluginState.STARTED) {
            pluginRepository.updateStatus(pluginId, PluginStatus.ACTIVE);
        } else {
            throw new RuntimeException("插件启动失败: " + state);
        }
    }

    /**
     * 停用插件。
     */
    public void disable(String pluginId) {
        PluginState state = pf4jManager.stopPlugin(pluginId);
        if (state == PluginState.STOPPED) {
            pluginRepository.updateStatus(pluginId, PluginStatus.STOPPED);
        } else {
            throw new RuntimeException("插件停止失败: " + state);
        }
    }

    /**
     * 卸载插件（保留数据库数据）。
     */
    public void uninstall(String pluginId) {
        // 先停止
        if (pf4jManager.getPlugin(pluginId) != null) {
            pf4jManager.stopPlugin(pluginId);
            pf4jManager.unloadPlugin(pluginId);
        }

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
            .orElseThrow(() -> new RuntimeException("插件不存在: " + pluginId));
    }

    /**
     * 获取运行中的插件（员工端用）。
     */
    public List<PluginRow> getActivePlugins() {
        return pluginRepository.findByStatus(PluginStatus.ACTIVE);
    }

    /**
     * 发现指定插件的动作处理器。
     *
     * @param pluginId   插件ID
     * @param actionCode 动作编码
     * @return 动作处理器
     */
    public Optional<PluginActionHandler> findActionHandler(String pluginId, String actionCode) {
        // 检查插件状态
        PluginRow plugin = pluginRepository.findById(pluginId)
            .orElseThrow(() -> new RuntimeException("插件不存在: " + pluginId));

        if (plugin.getStatus() != PluginStatus.ACTIVE) {
            throw new RuntimeException("插件未启用: " + pluginId + " (状态: " + plugin.getStatus() + ")");
        }

        // 从 PF4J 获取扩展
        List<PluginActionHandler> handlers = pf4jManager.getExtensions(PluginActionHandler.class, pluginId);
        return handlers.stream()
            .filter(h -> h.getActionCode().equals(actionCode))
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
