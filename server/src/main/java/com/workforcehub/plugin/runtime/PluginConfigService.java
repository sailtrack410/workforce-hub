package com.workforcehub.plugin.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.audit.AuditLogWriter;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.exception.BusinessErrorCode;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.plugin.dto.PluginConfigResponse;
import com.workforcehub.plugin.dto.PluginConfigSaveRequest;
import com.workforcehub.plugin.repository.PluginConfigRepository;
import com.workforcehub.plugin.repository.PluginConfigRow;
import com.workforcehub.plugin.repository.PluginStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.workforcehub.common.util.JsonValues.write;

/**
 * 插件配置管理，plugin_id 是唯一命名空间边界。
 */
@Service
public class PluginConfigService {

    private static final Pattern PLUGIN_ID_PATTERN = Pattern.compile("^[a-z0-9-]{1,64}$");
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,127}$");
    private static final Set<String> VALUE_TYPES = Set.of("STRING", "INT", "BOOL", "SECRET");
    private static final int MAX_VALUE_BYTES = 16 * 1024;
    private static final int MAX_KEYS_PER_PLUGIN = 128;

    private final PluginConfigRepository repository;
    private final PluginConfigCipher cipher;
    private final AuditLogWriter auditLogWriter;
    private final ObjectMapper objectMapper;

    public PluginConfigService(
            PluginConfigRepository repository,
            PluginConfigCipher cipher,
            AuditLogWriter auditLogWriter,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.cipher = cipher;
        this.auditLogWriter = auditLogWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PluginConfigResponse> list(String pluginId) {
        validatePluginId(pluginId);
        requirePlugin(repository.findPluginStatus(pluginId).orElse(null));
        return repository.findByPluginId(pluginId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public PluginConfigResponse save(
            String pluginId,
            String configKey,
            PluginConfigSaveRequest request) {
        validatePluginId(pluginId);
        validateKey(configKey);
        ValidatedInput input = validateRequest(request);

        PluginStatus status = repository.lockPluginStatus(pluginId).orElse(null);
        requirePlugin(status);
        if (status == PluginStatus.DISABLING || status == PluginStatus.UNINSTALLED) {
            throw new BizException(BusinessErrorCode.CONFLICT, "当前插件状态不允许修改配置");
        }

        PluginConfigRow existing = repository.findIncludingDeleted(pluginId, configKey).orElse(null);
        long expectedVersion = request.expectedVersion();
        PluginConfigRow saved;
        String action;

        if (existing == null || existing.deleted()) {
            if (existing == null ? expectedVersion != 0 : expectedVersion != existing.version()) {
                throw versionConflict();
            }
            if (existing == null && repository.countByPluginId(pluginId) >= MAX_KEYS_PER_PLUGIN) {
                throw new BizException(BusinessErrorCode.UNPROCESSABLE_ENTITY, "单个插件最多允许 128 个配置键命名空间");
            }
            if (existing != null && input.secret() && input.value() == null) {
                throw new BizException(BusinessErrorCode.BAD_REQUEST, "重建敏感配置必须提供值");
            }
            String storedValue = encodeValue(pluginId, configKey, input, null);
            PluginConfigRow created = new PluginConfigRow(
                pluginId,
                configKey,
                storedValue,
                input.valueType(),
                input.defaultValue(),
                input.description(),
                request.requiresRestart(),
                input.secret(),
                false,
                existing == null ? 1 : existing.version() + 1,
                LocalDateTime.now()
            );
            if (existing == null) {
                try {
                    repository.insert(created);
                } catch (DuplicateKeyException e) {
                    throw versionConflict();
                }
            } else if (repository.restore(created, existing.version()) != 1) {
                throw versionConflict();
            }
            saved = repository.find(pluginId, configKey).orElseThrow();
            action = "PLUGIN_CONFIG_CREATED";
        } else {
            if (expectedVersion == 0 || existing.version() != expectedVersion) {
                throw versionConflict();
            }
            String existingType = isSecret(existing) ? "SECRET" : existing.valueType();
            if (!existingType.equals(input.valueType())) {
                throw new BizException(BusinessErrorCode.CONFLICT, "配置类型不可直接变更，请删除后重建");
            }
            String storedValue = encodeValue(pluginId, configKey, input, existing);
            PluginConfigRow updated = new PluginConfigRow(
                pluginId,
                configKey,
                storedValue,
                input.valueType(),
                input.defaultValue(),
                input.description(),
                request.requiresRestart(),
                input.secret(),
                false,
                existing.version() + 1,
                LocalDateTime.now()
            );
            if (repository.update(updated, expectedVersion) != 1) {
                throw versionConflict();
            }
            saved = repository.find(pluginId, configKey).orElseThrow();
            action = "PLUGIN_CONFIG_UPDATED";
        }

        writeAudit(action, saved, existing == null ? 0 : existing.version(), saved.version());
        return toResponse(saved);
    }

    @Transactional
    public long delete(String pluginId, String configKey, Long expectedVersion) {
        validatePluginId(pluginId);
        validateKey(configKey);
        if (expectedVersion == null || expectedVersion <= 0) {
            throw new BizException(BusinessErrorCode.BAD_REQUEST, "expectedVersion 必须大于 0");
        }

        PluginStatus status = repository.lockPluginStatus(pluginId).orElse(null);
        requirePlugin(status);
        if (status == PluginStatus.DISABLING) {
            throw new BizException(BusinessErrorCode.CONFLICT, "插件正在停用，暂不能删除配置");
        }

        PluginConfigRow existing = repository.findIncludingDeleted(pluginId, configKey)
            .orElseThrow(() -> new BizException(BusinessErrorCode.NOT_FOUND, "配置项不存在"));
        if (existing.deleted()) {
            if (expectedVersion == existing.version() - 1) {
                return existing.version();
            }
            throw versionConflict();
        }
        if (existing.version() != expectedVersion
                || repository.delete(pluginId, configKey, expectedVersion) != 1) {
            throw versionConflict();
        }
        long deletedVersion = existing.version() + 1;
        writeAudit("PLUGIN_CONFIG_DELETED", existing, existing.version(), deletedVersion);
        return deletedVersion;
    }

    private ValidatedInput validateRequest(PluginConfigSaveRequest request) {
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw new BizException(BusinessErrorCode.BAD_REQUEST, "expectedVersion 不能小于 0");
        }
        String valueType = request.valueType();
        if (valueType == null || !VALUE_TYPES.contains(valueType)) {
            throw new BizException(BusinessErrorCode.BAD_REQUEST, "配置类型仅支持 STRING、INT、BOOL、SECRET");
        }
        if (request.description() != null && request.description().length() > 256) {
            throw new BizException(BusinessErrorCode.BAD_REQUEST, "配置说明不能超过 256 个字符");
        }

        String description = request.description() == null || request.description().isBlank()
            ? null
            : request.description().trim();
        boolean secret = "SECRET".equals(valueType);
        if (secret) {
            if (request.defaultValue() != null) {
                throw new BizException(BusinessErrorCode.BAD_REQUEST, "敏感配置不允许默认值");
            }
            if (request.value() != null) {
                validateSecretValue(request.value());
            } else if (request.expectedVersion() == 0) {
                throw new BizException(BusinessErrorCode.BAD_REQUEST, "新建敏感配置必须提供值");
            }
            return new ValidatedInput(valueType, request.value(), null, description, true);
        }

        if (request.value() == null && request.defaultValue() == null) {
            throw new BizException(BusinessErrorCode.BAD_REQUEST, "配置值和默认值不能同时为空");
        }
        String value = request.value() == null
            ? null
            : normalizeValue(valueType, request.value(), "配置值");
        String defaultValue = request.defaultValue() == null
            ? null
            : normalizeValue(valueType, request.defaultValue(), "默认值");
        return new ValidatedInput(valueType, value, defaultValue, description, false);
    }

    private String encodeValue(
            String pluginId,
            String configKey,
            ValidatedInput input,
            PluginConfigRow existing) {
        if (!input.secret()) {
            return input.value();
        }
        if (!cipher.isAvailable()) {
            throw new BizException(BusinessErrorCode.UNPROCESSABLE_ENTITY, "敏感配置加密密钥未配置");
        }
        if (input.value() == null) {
            if (existing == null
                    || existing.configValue() == null
                    || !cipher.isEncrypted(existing.configValue())) {
                throw new BizException(BusinessErrorCode.UNPROCESSABLE_ENTITY, "敏感配置需要重新设置");
            }
            try {
                cipher.decrypt(pluginId, configKey, existing.configValue());
            } catch (IllegalStateException e) {
                throw new BizException(BusinessErrorCode.UNPROCESSABLE_ENTITY, "敏感配置需要重新设置");
            }
            return existing.configValue();
        }
        return cipher.encrypt(pluginId, configKey, input.value());
    }

    private String normalizeValue(String valueType, String value, String fieldName) {
        validateValueSize(value, fieldName);
        if ("STRING".equals(valueType)) {
            return value;
        }
        if ("INT".equals(valueType)) {
            try {
                return Long.toString(Long.parseLong(value));
            } catch (NumberFormatException e) {
                throw new BizException(BusinessErrorCode.BAD_REQUEST, fieldName + "必须是 64 位整数");
            }
        }
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new BizException(BusinessErrorCode.BAD_REQUEST, fieldName + "必须是 true 或 false");
        }
        return value;
    }

    private void validateSecretValue(String value) {
        if (value.isBlank()) {
            throw new BizException(BusinessErrorCode.BAD_REQUEST, "敏感配置值不能为空");
        }
        validateValueSize(value, "敏感配置值");
    }

    private void validateValueSize(String value, String fieldName) {
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_VALUE_BYTES) {
            throw new BizException(BusinessErrorCode.PAYLOAD_TOO_LARGE, fieldName + "不能超过 16 KiB");
        }
    }

    private PluginConfigResponse toResponse(PluginConfigRow row) {
        boolean secret = isSecret(row);
        return new PluginConfigResponse(
            row.configKey(),
            secret ? "SECRET" : row.valueType(),
            secret ? null : row.configValue(),
            secret ? null : row.defaultValue(),
            row.description(),
            row.requiresRestart(),
            secret,
            row.configValue() != null,
            row.version(),
            row.updatedAt()
        );
    }

    private void writeAudit(
            String action,
            PluginConfigRow row,
            long beforeVersion,
            long afterVersion) {
        auditLogWriter.write(
            "ADMIN",
            CurrentUserHolder.requireUserId(),
            action,
            "PLUGIN_CONFIG",
            null,
            "SUCCESS",
            write(objectMapper, Map.of(
                "pluginId", row.pluginId(),
                "configKey", row.configKey(),
                "valueType", isSecret(row) ? "SECRET" : row.valueType(),
                "secret", isSecret(row),
                "requiresRestart", row.requiresRestart(),
                "beforeVersion", beforeVersion,
                "afterVersion", afterVersion
            ))
        );
    }

    private boolean isSecret(PluginConfigRow row) {
        return row.secret() || "SECRET".equals(row.valueType());
    }

    private void validatePluginId(String pluginId) {
        if (pluginId == null || !PLUGIN_ID_PATTERN.matcher(pluginId).matches()) {
            throw new BizException(BusinessErrorCode.BAD_REQUEST, "插件 ID 格式不正确");
        }
    }

    private void validateKey(String configKey) {
        if (configKey == null || !KEY_PATTERN.matcher(configKey).matches()) {
            throw new BizException(BusinessErrorCode.BAD_REQUEST, "配置键格式不正确");
        }
    }

    private void requirePlugin(PluginStatus status) {
        if (status == null) {
            throw new BizException(BusinessErrorCode.NOT_FOUND, "插件不存在");
        }
    }

    private BizException versionConflict() {
        return new BizException(BusinessErrorCode.CONFLICT, "配置已被其他请求修改，请刷新后重试");
    }

    private record ValidatedInput(
        String valueType,
        String value,
        String defaultValue,
        String description,
        boolean secret
    ) {}
}
