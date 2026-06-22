package com.workforcehub.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.response.R;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonLongIdSerializationTest {

    @Test
    @DisplayName("Long ID 应序列化为字符串，避免前端精度丢失")
    void longIdShouldSerializeAsString() throws Exception {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new com.workforcehub.common.config.JacksonConfig().jsSafeLongSerializer().customize(builder);
        ObjectMapper objectMapper = builder.build();
        String json = objectMapper.writeValueAsString(R.ok(new IdPayload(2068909335178776600L)));

        assertTrue(json.contains("\"id\":\"2068909335178776600\""), json);
    }

    @Test
    @DisplayName("JS 安全范围内的 Long 应保留为数字")
    void jsSafeLongShouldRemainNumber() throws Exception {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new com.workforcehub.common.config.JacksonConfig().jsSafeLongSerializer().customize(builder);
        ObjectMapper objectMapper = builder.build();
        String json = objectMapper.writeValueAsString(R.ok(new IdPayload(2003L)));

        assertTrue(json.contains("\"id\":2003"), json);
    }

    @Test
    @DisplayName("Long 指标值应保留为数字")
    void longMetricShouldRemainNumber() throws Exception {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new com.workforcehub.common.config.JacksonConfig().jsSafeLongSerializer().customize(builder);
        ObjectMapper objectMapper = builder.build();
        String json = objectMapper.writeValueAsString(R.ok(new MetricPayload(0L)));

        assertTrue(json.contains("\"averageFinishedDurationMillis\":0"), json);
    }

    record IdPayload(Long id) {}

    record MetricPayload(Long averageFinishedDurationMillis) {}
}
