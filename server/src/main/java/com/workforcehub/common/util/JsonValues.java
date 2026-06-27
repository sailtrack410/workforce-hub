package com.workforcehub.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.exception.BizException;
import org.springframework.util.StringUtils;

public final class JsonValues {

    private JsonValues() {
    }

    public static String normalize(ObjectMapper objectMapper, String value, String errorMessage) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(value));
        } catch (Exception e) {
            throw new BizException(400, errorMessage);
        }
    }

    public static String write(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(500, "JSON序列化失败");
        }
    }
}
