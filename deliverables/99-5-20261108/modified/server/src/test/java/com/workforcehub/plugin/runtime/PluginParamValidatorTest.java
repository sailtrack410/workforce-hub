package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.sdk.ActionDescriptor;
import com.workforcehub.plugin.sdk.ParamSchema;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginParamValidatorTest {

    private final PluginParamValidator validator = new PluginParamValidator();

    @Test
    void shouldValidateSupportedTypes() {
        ActionDescriptor descriptor = descriptor(Map.of(
            "text", ParamSchema.string("文本", true),
            "count", ParamSchema.number("数量", true),
            "enabled", ParamSchema.bool("启用", true),
            "options", ParamSchema.object("配置", true),
            "items", ParamSchema.array("列表", true)
        ));

        assertTrue(validator.validate(descriptor, Map.of(
            "text", "value",
            "count", 1,
            "enabled", true,
            "options", Map.of("key", "value"),
            "items", List.of("value")
        )).isEmpty());
    }

    @Test
    void shouldRejectUnknownSchemaType() {
        ActionDescriptor descriptor = descriptor(Map.of(
            "value", new ParamSchema("unknown", "未知", true, null)
        ));

        assertEquals(
            List.of("参数 Schema 类型不支持: value"),
            validator.validate(descriptor, Map.of("value", "test"))
        );
    }

    @Test
    void shouldRejectUndeclaredParam() {
        ActionDescriptor descriptor = descriptor(Map.of(
            "name", ParamSchema.string("姓名", true)
        ));

        assertEquals(
            List.of("未声明参数: admin"),
            validator.validate(descriptor, Map.of("name", "test", "admin", true))
        );
    }

    private ActionDescriptor descriptor(Map<String, ParamSchema> inputSchema) {
        return new ActionDescriptor(
            "test.action",
            "测试",
            "测试动作",
            inputSchema,
            Map.of(),
            "TEST",
            Duration.ofSeconds(5),
            true
        );
    }
}
