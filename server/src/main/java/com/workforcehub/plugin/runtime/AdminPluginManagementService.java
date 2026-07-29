package com.workforcehub.plugin.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.audit.AuditLogWriter;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.exception.BusinessErrorCode;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.plugin.dto.AdminPluginResponse;
import com.workforcehub.plugin.repository.PluginRow;
import com.workforcehub.plugin.repository.PluginStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

import static com.workforcehub.common.util.JsonValues.write;

@Service
public class AdminPluginManagementService {

    private static final Logger log = LoggerFactory.getLogger(AdminPluginManagementService.class);
    private static final Pattern PLUGIN_ID_PATTERN = Pattern.compile("^[a-z0-9-]{1,64}$");

    private final PluginManagerService pluginManagerService;
    private final AuditLogWriter auditLogWriter;
    private final ObjectMapper objectMapper;

    public AdminPluginManagementService(
            PluginManagerService pluginManagerService,
            AuditLogWriter auditLogWriter,
            ObjectMapper objectMapper) {
        this.pluginManagerService = pluginManagerService;
        this.auditLogWriter = auditLogWriter;
        this.objectMapper = objectMapper;
    }

    public List<AdminPluginResponse> list() {
        return pluginManagerService.listPlugins().stream()
            .map(AdminPluginResponse::from)
            .toList();
    }

    public AdminPluginResponse enable(String pluginId) {
        validatePluginId(pluginId);
        long operatorId = CurrentUserHolder.requireUserId();
        synchronized (pluginManagerService) {
            PluginRow before = requirePlugin(pluginId, operatorId, "PLUGIN_ENABLED");
            if (before.getStatus() == PluginStatus.ACTIVE) {
                writeAudit(operatorId, "PLUGIN_ENABLED", pluginId,
                    before.getStatus(), before.getStatus(), "SUCCESS");
                return AdminPluginResponse.from(before);
            }
            if (before.getStatus() != PluginStatus.INSTALLED
                    && before.getStatus() != PluginStatus.STOPPED) {
                writeAudit(operatorId, "PLUGIN_ENABLED", pluginId,
                    before.getStatus(), before.getStatus(), "BLOCKED");
                throw new BizException(BusinessErrorCode.CONFLICT, "当前插件状态不允许启用");
            }

            try {
                var updatedAt = pluginManagerService.enable(pluginId);
                writeAudit(operatorId, "PLUGIN_ENABLED", pluginId,
                    before.getStatus(), PluginStatus.ACTIVE, "SUCCESS");
                return AdminPluginResponse.from(before, PluginStatus.ACTIVE, updatedAt);
            } catch (RuntimeException e) {
                writeAudit(operatorId, "PLUGIN_ENABLED", pluginId,
                    before.getStatus(), currentStatus(pluginId, before.getStatus()), "FAIL");
                throw e;
            }
        }
    }

    public AdminPluginResponse disable(String pluginId) {
        validatePluginId(pluginId);
        long operatorId = CurrentUserHolder.requireUserId();
        synchronized (pluginManagerService) {
            PluginRow before = requirePlugin(pluginId, operatorId, "PLUGIN_DISABLED");
            if (before.getStatus() == PluginStatus.STOPPED) {
                writeAudit(operatorId, "PLUGIN_DISABLED", pluginId,
                    before.getStatus(), before.getStatus(), "SUCCESS");
                return AdminPluginResponse.from(before);
            }
            if (before.getStatus() != PluginStatus.ACTIVE) {
                writeAudit(operatorId, "PLUGIN_DISABLED", pluginId,
                    before.getStatus(), before.getStatus(), "BLOCKED");
                throw new BizException(BusinessErrorCode.CONFLICT, "当前插件状态不允许停用");
            }

            try {
                var updatedAt = pluginManagerService.disable(pluginId);
                writeAudit(operatorId, "PLUGIN_DISABLED", pluginId,
                    before.getStatus(), PluginStatus.STOPPED, "SUCCESS");
                return AdminPluginResponse.from(before, PluginStatus.STOPPED, updatedAt);
            } catch (PluginManagerService.PluginDependencyConflictException e) {
                writeAudit(operatorId, "PLUGIN_DISABLED", pluginId,
                    before.getStatus(), before.getStatus(), "BLOCKED");
                throw new BizException(BusinessErrorCode.CONFLICT, "插件仍被其他插件依赖，不能停用");
            } catch (RuntimeException e) {
                writeAudit(operatorId, "PLUGIN_DISABLED", pluginId,
                    before.getStatus(), currentStatus(pluginId, before.getStatus()), "FAIL");
                throw e;
            }
        }
    }

    public AdminPluginResponse uninstall(String pluginId) {
        validatePluginId(pluginId);
        long operatorId = CurrentUserHolder.requireUserId();
        synchronized (pluginManagerService) {
            PluginRow before = requirePlugin(pluginId, operatorId, "PLUGIN_UNINSTALLED");
            if (before.getStatus() == PluginStatus.UNINSTALLED) {
                writeAudit(operatorId, "PLUGIN_UNINSTALLED", pluginId,
                    before.getStatus(), before.getStatus(), "SUCCESS");
                return AdminPluginResponse.from(before);
            }
            try {
                var updatedAt = pluginManagerService.uninstall(pluginId);
                writeAudit(operatorId, "PLUGIN_UNINSTALLED", pluginId,
                    before.getStatus(), PluginStatus.UNINSTALLED, "SUCCESS");
                return AdminPluginResponse.from(before, PluginStatus.UNINSTALLED, updatedAt);
            } catch (PluginManagerService.PluginDependencyConflictException e) {
                writeAudit(operatorId, "PLUGIN_UNINSTALLED", pluginId,
                    before.getStatus(), before.getStatus(), "BLOCKED");
                throw new BizException(BusinessErrorCode.CONFLICT, "插件仍被其他插件依赖，不能卸载");
            } catch (RuntimeException e) {
                writeAudit(operatorId, "PLUGIN_UNINSTALLED", pluginId,
                    before.getStatus(), currentStatus(pluginId, before.getStatus()), "FAIL");
                throw e;
            }
        }
    }

    private PluginRow requirePlugin(String pluginId, long operatorId, String action) {
        try {
            return pluginManagerService.getPlugin(pluginId);
        } catch (NoSuchElementException e) {
            writeAudit(operatorId, action, pluginId, null, null, "BLOCKED");
            throw new BizException(BusinessErrorCode.NOT_FOUND, "插件不存在");
        }
    }

    private PluginStatus currentStatus(String pluginId, PluginStatus fallback) {
        try {
            return pluginManagerService.getPlugin(pluginId).getStatus();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private void validatePluginId(String pluginId) {
        if (pluginId == null || !PLUGIN_ID_PATTERN.matcher(pluginId).matches()) {
            throw new BizException(BusinessErrorCode.BAD_REQUEST, "插件 ID 格式不正确");
        }
    }

    private void writeAudit(
            long operatorId,
            String action,
            String pluginId,
            PluginStatus beforeStatus,
            PluginStatus afterStatus,
            String result) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("pluginId", pluginId);
        detail.put("beforeStatus", beforeStatus == null ? null : beforeStatus.name());
        detail.put("afterStatus", afterStatus == null ? null : afterStatus.name());
        try {
            auditLogWriter.write(
                "ADMIN",
                operatorId,
                action,
                "PLUGIN",
                null,
                result,
                write(objectMapper, detail)
            );
        } catch (RuntimeException e) {
            // PF4J 生命周期与数据库审计无法原子提交，不能因审计失败诱发重复操作。
            log.error("插件管理审计写入失败: pluginId={}, action={}", pluginId, action, e);
        }
    }
}
