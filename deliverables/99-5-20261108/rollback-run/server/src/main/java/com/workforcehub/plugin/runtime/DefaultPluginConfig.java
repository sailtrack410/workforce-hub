package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.repository.PluginConfigRepository;
import com.workforcehub.plugin.repository.PluginConfigRow;
import com.workforcehub.plugin.sdk.PluginConfig;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 绑定单一插件 ID 的只读配置实现。
 */
final class DefaultPluginConfig implements PluginConfig {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,127}$");

    private final String pluginId;
    private final PluginConfigRepository repository;
    private final PluginConfigCipher cipher;

    DefaultPluginConfig(
            String pluginId,
            PluginConfigRepository repository,
            PluginConfigCipher cipher) {
        this.pluginId = pluginId;
        this.repository = repository;
        this.cipher = cipher;
    }

    @Override
    public Optional<String> get(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("配置键格式不正确");
        }
        return repository.find(pluginId, key).flatMap(this::resolveValue);
    }

    private Optional<String> resolveValue(PluginConfigRow row) {
        boolean secret = row.secret() || "SECRET".equals(row.valueType());
        String value = row.configValue() != null ? row.configValue() : row.defaultValue();
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(secret ? cipher.decrypt(pluginId, row.configKey(), value) : value);
    }
}
