package com.workforcehub.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsSafeLongSerializer() {
        JsSafeLongSerializer serializer = new JsSafeLongSerializer();
        return builder -> builder.serializerByType(Long.class, serializer)
            .serializerByType(Long.TYPE, serializer);
    }

    static class JsSafeLongSerializer extends JsonSerializer<Long> {
        private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
        private static final long MIN_SAFE_INTEGER = -9_007_199_254_740_991L;

        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value >= MIN_SAFE_INTEGER && value <= MAX_SAFE_INTEGER) {
                gen.writeNumber(value);
                return;
            }
            gen.writeString(value.toString());
        }
    }
}
