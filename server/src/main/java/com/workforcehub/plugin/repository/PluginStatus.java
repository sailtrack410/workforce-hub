package com.workforcehub.plugin.repository;

/**
 * 插件状态枚举。
 */
public enum PluginStatus {
    INSTALLED,   // 已安装，未启用
    ACTIVE,      // 运行中
    STOPPED,     // 已停用
    ERROR,       // 启动失败或运行异常
    DISABLING    // 停用中（等待请求排空）
}
