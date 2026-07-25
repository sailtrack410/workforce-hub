package com.workforcehub.plugin.sdk;

import org.pf4j.ExtensionPoint;

import java.util.Map;

/**
 * 插件动作处理器 - 宿主通过通用网关调用。
 * 插件实现此接口来暴露可调用的动作。
 */
public interface PluginActionHandler extends ExtensionPoint {

    /**
     * 动作编码（唯一标识）。
     */
    String getActionCode();

    /**
     * 动作描述（用于能力发现和文档生成）。
     */
    ActionDescriptor getDescriptor();

    /**
     * 执行动作。
     *
     * @param ctx    调用上下文（由宿主绑定，不可伪造）
     * @param params 动作参数
     * @return 执行结果
     */
    ServiceResult<Object> execute(InvocationContext ctx, Map<String, Object> params);
}
