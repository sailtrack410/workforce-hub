package com.workforcehub.plugin.repository;

/**
 * 已签名清单中的必需插件依赖范围。
 */
public record PluginDependencySpec(
    String pluginId,
    String minVersion,
    String maxVersion
) {
}
