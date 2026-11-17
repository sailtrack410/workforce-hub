package com.workforcehub.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.workforcehub.agent.model.AgentModelProperties;
import com.workforcehub.agent.model.AgentModelRequest;
import com.workforcehub.agent.model.OpenAiCompatibleAgentModelClient;
import com.workforcehub.agent.model.SpringAiOpenAiChatClientFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 模型客户端的 Spring AI OpenAI 兼容接入测试。
 */
class AgentModelClientSpringAiTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static HttpServer modelServer;
    private static String modelEndpoint;
    private static final AtomicReference<JsonNode> lastRequestPayload = new AtomicReference<>();

    @BeforeAll
    static void startModelServer() throws IOException {
        modelServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        modelServer.createContext("/v1/chat/completions", exchange -> {
            JsonNode payload = OBJECT_MAPPER.readTree(exchange.getRequestBody());
            lastRequestPayload.set(payload);
            byte[] response = """
                {
                  "id": "chatcmpl-spring-ai-test",
                  "object": "chat.completion",
                  "created": 1710000000,
                  "model": "stub-chat",
                  "usage": {
                    "prompt_tokens": 8,
                    "completion_tokens": 4,
                    "total_tokens": 12
                  },
                  "choices": [
                    {
                      "index": 0,
                      "finish_reason": "stop",
                      "message": {
                        "role": "assistant",
                        "content": "Spring AI 模型回复"
                      }
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        modelServer.start();
        modelEndpoint = "http://127.0.0.1:" + modelServer.getAddress().getPort() + "/v1/chat/completions";
    }

    @AfterAll
    static void stopModelServer() {
        if (modelServer != null) {
            modelServer.stop(0);
        }
    }

    @Test
    @DisplayName("OpenAI兼容客户端应通过Spring AI调用模型服务")
    void openAiCompatibleClientShouldUseSpringAi() {
        AgentModelProperties properties = new AgentModelProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(modelEndpoint);
        properties.setApiKey("test-key");
        properties.setModel("stub-chat");
        properties.setTimeout(Duration.ofSeconds(5));
        properties.setMaxTokens(64);
        properties.setTemperature(0.1D);

        OpenAiCompatibleAgentModelClient client = new OpenAiCompatibleAgentModelClient(
            properties,
            OBJECT_MAPPER,
            new SpringAiOpenAiChatClientFactory()
        );

        String reply = client.generateReply(new AgentModelRequest(
            1L,
            2L,
            "测试助手",
            "你是测试助手",
            "请回答一句话",
            null
        )).text();

        assertEquals("Spring AI 模型回复", reply);
        JsonNode payload = lastRequestPayload.get();
        assertNotNull(payload);
        assertEquals("stub-chat", payload.path("model").asText());
        assertEquals(64, payload.path("max_tokens").asInt());
        assertEquals(0.1D, payload.path("temperature").asDouble(), 0.0001D);
        assertEquals("system", payload.path("messages").path(0).path("role").asText());
        assertEquals("你是测试助手", payload.path("messages").path(0).path("content").asText());
        assertEquals("user", payload.path("messages").path(1).path("role").asText());
        assertEquals("请回答一句话", payload.path("messages").path(1).path("content").asText());
        assertTrue(usesSpringAiFactory(OpenAiCompatibleAgentModelClient.class));
    }

    private boolean usesSpringAiFactory(Class<?> targetClass) {
        for (java.lang.reflect.Field field : targetClass.getDeclaredFields()) {
            if (field.getType().equals(SpringAiOpenAiChatClientFactory.class)) {
                return true;
            }
        }
        return false;
    }
}
