package com.workforcehub.plugin.sdk.service;

import com.workforcehub.plugin.sdk.HostService;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.RequiresCapability;
import com.workforcehub.plugin.sdk.ServiceResult;

/**
 * 领域事件订阅服务（插件 → 宿主）。
 * 插件通过此接口订阅/退订宿主发布的领域事件。
 * 能力码 EVENT_SUBSCRIBE 以宿主数据库 sys_plugin_capability 为准。
 */
public interface EventService extends HostService {

    /**
     * 订阅指定事件类型。
     * @param ctx          调用上下文（宿主绑定）
     * @param eventType    事件类型（非 blank，不限于 EventTypes 常量）
     * @param actionCode   事件触发时调用的 PluginActionHandler 编码
     * @param idempotencyKey 幂等键（非 blank）
     */
    @RequiresCapability("EVENT_SUBSCRIBE")
    ServiceResult<Void> subscribe(InvocationContext ctx, String eventType, String actionCode, String idempotencyKey);

    /**
     * 取消订阅指定事件类型。
     * @param ctx            调用上下文（宿主绑定）
     * @param eventType      事件类型（非 blank）
     * @param idempotencyKey 幂等键（非 blank）
     */
    @RequiresCapability("EVENT_SUBSCRIBE")
    ServiceResult<Void> unsubscribe(InvocationContext ctx, String eventType, String idempotencyKey);
}
