package com.workforcehub.plugin.sdk;

/**
 * 参数 Schema 描述。
 */
public record ParamSchema(
    String type,          // string/number/boolean/object/array
    String description,   // 参数描述
    boolean required,     // 是否必填
    Object defaultValue   // 默认值
) {
    public static ParamSchema string(String description, boolean required) {
        return new ParamSchema("string", description, required, null);
    }

    public static ParamSchema number(String description, boolean required) {
        return new ParamSchema("number", description, required, null);
    }

    public static ParamSchema bool(String description, boolean required) {
        return new ParamSchema("boolean", description, required, null);
    }

    public static ParamSchema object(String description, boolean required) {
        return new ParamSchema("object", description, required, null);
    }

    public static ParamSchema array(String description, boolean required) {
        return new ParamSchema("array", description, required, null);
    }
}
