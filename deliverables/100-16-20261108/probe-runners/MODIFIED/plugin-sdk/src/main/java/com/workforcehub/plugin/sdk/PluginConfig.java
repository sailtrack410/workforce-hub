package com.workforcehub.plugin.sdk;

import java.util.Optional;

/**
 * 只读插件配置，宿主自动限定到当前插件命名空间。
 */
@FunctionalInterface
public interface PluginConfig {

    Optional<String> get(String key);
}
