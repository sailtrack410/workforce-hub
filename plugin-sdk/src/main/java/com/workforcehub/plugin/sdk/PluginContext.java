package com.workforcehub.plugin.sdk;

/**
 * 宿主在创建插件时注入的运行上下文。
 * 动作执行使用 PluginActionHandler.execute(ctx, ...) 收到的宿主上下文；
 * 当前 PoC 不向插件暴露可长期持有的启动上下文。
 */
public interface PluginContext {

    String getPluginId();

    <T extends HostService> T getService(Class<T> type);
}
