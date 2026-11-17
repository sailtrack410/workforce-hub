package com.workforcehub.plugin.sdk;

import java.time.Duration;
import java.util.Map;

/**
 * 动作描述符 - 插件声明其支持的动作。
 */
public record ActionDescriptor(
    String actionCode,                    // 动作编码（唯一）
    String name,                          // 动作名称
    String description,                   // 动作描述
    Map<String, ParamSchema> inputSchema, // 输入参数 Schema
    Map<String, ParamSchema> outputSchema,// 输出参数 Schema
    String requiredCapability,            // 所需能力码
    Duration defaultTimeout,              // 默认超时
    boolean idempotent                    // 是否幂等
) {
    private static final Duration MAX_TIMEOUT = Duration.ofMinutes(5);

    public ActionDescriptor {
        if (actionCode == null || actionCode.isBlank()) {
            throw new IllegalArgumentException("actionCode 不能为空");
        }
        if (defaultTimeout == null || defaultTimeout.isZero() || defaultTimeout.isNegative()) {
            throw new IllegalArgumentException("defaultTimeout 必须大于 0");
        }
        if (defaultTimeout.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException("defaultTimeout 不能超过 5 分钟");
        }
    }

    /**
     * 简化构造器。
     */
    public ActionDescriptor(
            String actionCode,
            String name,
            String description,
            String requiredCapability) {
        this(actionCode, name, description, Map.of(), Map.of(),
            requiredCapability, Duration.ofSeconds(30), true);
    }
}
