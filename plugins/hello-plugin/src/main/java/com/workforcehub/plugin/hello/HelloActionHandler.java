package com.workforcehub.plugin.hello;

import com.workforcehub.plugin.sdk.ActionDescriptor;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.ParamSchema;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import com.workforcehub.plugin.sdk.ServiceResult;
import org.pf4j.Extension;

import java.util.Map;

/**
 * 示例动作处理器 - 验证宿主网关调用链路。
 */
@Extension
public final class HelloActionHandler implements PluginActionHandler {

    public static final String ACTION_CODE = "hello.greet";
    public static final String CAPABILITY = "HELLO_GREET";

    @Override
    public String getActionCode() {
        return ACTION_CODE;
    }

    @Override
    public ActionDescriptor getDescriptor() {
        return new ActionDescriptor(
            ACTION_CODE,
            "问候",
            "返回问候语",
            Map.of("name", ParamSchema.string("姓名", true)),
            Map.of("message", ParamSchema.string("问候语", false)),
            CAPABILITY,
            java.time.Duration.ofSeconds(5),
            true
        );
    }

    @Override
    public ServiceResult<Object> execute(InvocationContext ctx, Map<String, Object> params) {
        String name = (String) params.getOrDefault("name", "World");
        String message = "Hello, " + name + "! (from plugin: " + ctx.getPluginId() + ")";
        return ServiceResult.ok(Map.of("message", message));
    }
}
