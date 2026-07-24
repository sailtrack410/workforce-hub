package com.workforcehub.plugin.sdk;

/**
 * 宿主在创建插件时注入的运行上下文。
 */
public interface PluginContext {

    String getPluginId();

    InvocationContext getInvocationContext();

    <T extends HostService> T getService(Class<T> type);
}
