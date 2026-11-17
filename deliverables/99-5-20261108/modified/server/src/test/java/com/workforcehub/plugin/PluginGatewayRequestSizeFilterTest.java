package com.workforcehub.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.plugin.controller.PluginGatewayRequestSizeFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PluginGatewayRequestSizeFilterTest {

    private static final int MAX_BYTES = 256 * 1024;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PluginGatewayRequestSizeFilter filter = new PluginGatewayRequestSizeFilter(objectMapper);

    @Test
    void shouldRejectOversizedBodyWithoutContentLength() throws Exception {
        MockHttpServletRequest request = new UnknownLengthRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/plugin-gateway/test/actions/run");
        request.setContent(new byte[MAX_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        assertEquals(1000, objectMapper.readTree(response.getContentAsString()).path("code").asInt());
        assertNull(chain.getRequest());
    }

    @Test
    void shouldPassBodyAtLimit() throws Exception {
        MockHttpServletRequest request = new UnknownLengthRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/plugin-gateway/test/actions/run");
        request.setContent("a".repeat(MAX_BYTES).getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
    }

    private static final class UnknownLengthRequest extends MockHttpServletRequest {

        @Override
        public int getContentLength() {
            return -1;
        }

        @Override
        public long getContentLengthLong() {
            return -1;
        }
    }
}
