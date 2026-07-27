package com.workforcehub.plugin.controller.employee;

/**
 * 员工端插件清单响应。
 */
public record EmployeePluginResponse(
    String id,
    String name,
    String frontendEntry,
    String integrity
) {
}
