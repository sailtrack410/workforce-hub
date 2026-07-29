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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final int MAX_SIGNATURE_BYTES = 65_535;

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

    public AdminPluginResponse upload(
            MultipartFile jar,
            MultipartFile signature,
            String signerId,
            String name,
            String description) {
        long operatorId = CurrentUserHolder.requireUserId();
        String pluginId = null;
        Path uploadPath = null;
        try {
            validateUpload(jar, signature, signerId, name, description);
            uploadPath = Files.createTempFile("workforce-plugin-upload-", ".jar");
            jar.transferTo(uploadPath);

            PluginRow metadata = new PluginRow();
            metadata.setName(normalizeOptional(name));
            metadata.setDescription(normalizeOptional(description));
            metadata.setSignerId(signerId.trim());
            metadata.setSignature(signature.getBytes());

            PluginRow installed = pluginManagerService.installNew(uploadPath, metadata);
            pluginId = installed.getPluginId();
            writeAudit(operatorId, "PLUGIN_UPLOADED", pluginId,
                null, PluginStatus.INSTALLED, "SUCCESS");
            return AdminPluginResponse.from(installed);
        } catch (PluginManagerService.PluginPackageTooLargeException e) {
            throwIfCleanupFailed(operatorId, pluginId, e);
            writeAudit(operatorId, "PLUGIN_UPLOADED", pluginId, null, null, "BLOCKED");
            throw new BizException(BusinessErrorCode.PAYLOAD_TOO_LARGE, "插件 JAR 不能超过 32 MiB");
        } catch (PluginManagerService.PluginAlreadyExistsException e) {
            throwIfCleanupFailed(operatorId, e.getPluginId(), e);
            writeAudit(operatorId, "PLUGIN_UPLOADED", e.getPluginId(), null, null, "BLOCKED");
            throw new BizException(BusinessErrorCode.CONFLICT, "插件 ID 已存在，上传接口不支持替换安装");
        } catch (PluginSignatureVerifier.PluginSignatureException e) {
            throwIfCleanupFailed(operatorId, pluginId, e);
            writeAudit(operatorId, "PLUGIN_UPLOADED", pluginId, null, null, "BLOCKED");
            throw new BizException(BusinessErrorCode.UNPROCESSABLE_ENTITY, "插件签名校验失败");
        } catch (PluginVersionService.PluginVersionException | IllegalArgumentException e) {
            throwIfCleanupFailed(operatorId, pluginId, e);
            writeAudit(operatorId, "PLUGIN_UPLOADED", pluginId, null, null, "BLOCKED");
            throw new BizException(BusinessErrorCode.UNPROCESSABLE_ENTITY, "插件包格式或兼容性校验失败");
        } catch (BizException e) {
            writeAudit(operatorId, "PLUGIN_UPLOADED", pluginId, null, null, "BLOCKED");
            throw e;
        } catch (IOException e) {
            writeAudit(operatorId, "PLUGIN_UPLOADED", pluginId, null, null, "FAIL");
            throw new IllegalStateException("处理插件上传文件失败", e);
        } catch (RuntimeException e) {
            writeAudit(operatorId, "PLUGIN_UPLOADED", pluginId, null, null, "FAIL");
            throw e;
        } finally {
            if (uploadPath != null) {
                try {
                    Files.deleteIfExists(uploadPath);
                } catch (IOException | RuntimeException e) {
                    log.warn("删除插件上传临时文件失败", e);
                }
            }
        }
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

    private void validateUpload(
            MultipartFile jar,
            MultipartFile signature,
            String signerId,
            String name,
            String description) {
        if (jar == null || jar.isEmpty()) {
            throw new BizException(BusinessErrorCode.BAD_REQUEST, "插件 JAR 不能为空");
        }
        if (jar.getSize() > PluginManagerService.MAX_JAR_BYTES) {
            throw new BizException(BusinessErrorCode.PAYLOAD_TOO_LARGE, "插件 JAR 不能超过 32 MiB");
        }
        if (signature == null || signature.isEmpty()) {
            throw new BizException(BusinessErrorCode.BAD_REQUEST, "插件签名不能为空");
        }
        if (signature.getSize() > MAX_SIGNATURE_BYTES) {
            throw new BizException(BusinessErrorCode.BAD_REQUEST, "插件签名格式不正确");
        }
        if (signerId == null || signerId.isBlank() || signerId.trim().length() > 128) {
            throw new BizException(BusinessErrorCode.BAD_REQUEST, "插件签名者格式不正确");
        }
        String normalizedName = normalizeOptional(name);
        String normalizedDescription = normalizeOptional(description);
        if (normalizedName != null && normalizedName.length() > 128) {
            throw new BizException(BusinessErrorCode.BAD_REQUEST, "插件名称不能超过 128 个字符");
        }
        if (normalizedDescription != null && normalizedDescription.length() > 512) {
            throw new BizException(BusinessErrorCode.BAD_REQUEST, "插件描述不能超过 512 个字符");
        }
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void throwIfCleanupFailed(long operatorId, String pluginId, RuntimeException failure) {
        if (failure.getSuppressed().length > 0) {
            writeAudit(operatorId, "PLUGIN_UPLOADED", pluginId, null, null, "FAIL");
            throw failure;
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
