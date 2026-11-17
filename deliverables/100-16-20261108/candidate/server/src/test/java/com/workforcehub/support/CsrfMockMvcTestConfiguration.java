package com.workforcehub.support;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@TestConfiguration(proxyBeanMethods = false)
public class CsrfMockMvcTestConfiguration {

    @Bean
    MockMvcBuilderCustomizer csrfMockMvcBuilderCustomizer() {
        return builder -> builder.defaultRequest(get("/").with(csrf().asHeader()));
    }
}
