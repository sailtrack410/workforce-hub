package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.sdk.ActionDescriptor;
import com.workforcehub.plugin.sdk.ParamSchema;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通用参数校验器 - 根据 ActionDescriptor.inputSchema 校验参数。
 */
@Component
public class PluginParamValidator {

    /**
     * 校验参数。
     *
     * @param descriptor 动作描述
     * @param params     实际参数
     * @return 校验错误列表（空表示通过）
     */
    public List<String> validate(ActionDescriptor descriptor, Map<String, Object> params) {
        List<String> errors = new ArrayList<>();

        if (descriptor.inputSchema() == null) {
            return errors;
        }

        for (var entry : descriptor.inputSchema().entrySet()) {
            String key = entry.getKey();
            ParamSchema schema = entry.getValue();
            Object value = params.get(key);

            // 必填检查
            if (schema.required() && (value == null || (value instanceof String s && s.isBlank()))) {
                errors.add("缺少必填参数: " + key);
                continue;
            }

            // 跳过空值（非必填）
            if (value == null) {
                continue;
            }

            // 类型检查
            String typeError = checkType(key, value, schema.type());
            if (typeError != null) {
                errors.add(typeError);
            }
        }

        return errors;
    }

    private String checkType(String key, Object value, String expectedType) {
        return switch (expectedType) {
            case "string" -> value instanceof String ? null : "参数类型错误: " + key + " 应为字符串";
            case "number" -> value instanceof Number ? null : "参数类型错误: " + key + " 应为数字";
            case "boolean" -> value instanceof Boolean ? null : "参数类型错误: " + key + " 应为布尔值";
            default -> null;
        };
    }
}
