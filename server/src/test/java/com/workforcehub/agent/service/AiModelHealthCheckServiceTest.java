package com.workforcehub.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.workforcehub.agent.dto.ModelTestResponse;
import com.workforcehub.agent.dto.ProviderModelOptionResponse;
import com.workforcehub.agent.model.AgentModelRequest;
import com.workforcehub.agent.model.AgentModelResponse;
import com.workforcehub.agent.model.OpenAiCompatibleAgentModelClient;
import com.workforcehub.agent.repository.AiProviderRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiModelHealthCheckServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer server;
    private String baseUrl;
    private final OpenAiCompatibleAgentModelClient agentModelClient = mock(OpenAiCompatibleAgentModelClient.class);
    private AiModelHealthCheckService healthCheckService;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        healthCheckService = new AiModelHealthCheckService(OBJECT_MAPPER, agentModelClient, RestClient.builder());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void providerCheckShouldCallProviderModelsEndpoint() {
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/v1/models", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            sendJson(exchange, 200, "{\"data\":[]}");
        });

        ModelTestResponse response = healthCheckService.testProvider(new AiProviderRow(
            1001L,
            "openai",
            "OpenAI",
            baseUrl + "/v1",
            "provider-key",
            true,
            null,
            null,
            null,
            LocalDateTime.now(),
            LocalDateTime.now()
        ));

        assertTrue(response.success());
        assertEquals("供应商连通", response.message());
        assertEquals("Bearer provider-key", authorization.get());
    }

    @Test
    void providerModelListShouldParseModelOptions() {
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/v1/models", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            sendJson(exchange, 200, """
                {"data":[
                  {"id":"Qwen/Qwen2.5-7B-Instruct"},
                  {"id":"BAAI/bge-m3","display_name":"BGE M3"},
                  {"id":"Qwen/Qwen2.5-7B-Instruct"}
                ]}
                """);
        });

        List<ProviderModelOptionResponse> options = healthCheckService.listProviderModels(new AiProviderRow(
            1001L,
            "siliconflow",
            "硅基流动",
            baseUrl + "/v1",
            "provider-key",
            true,
            null,
            null,
            null,
            LocalDateTime.now(),
            LocalDateTime.now()
        ));

        assertEquals("Bearer provider-key", authorization.get());
        assertEquals(2, options.size());
        assertEquals("Qwen/Qwen2.5-7B-Instruct", options.get(0).modelName());
        assertEquals("TEXT", options.get(0).modelType());
        assertEquals("BAAI/bge-m3", options.get(1).modelName());
        assertEquals("BGE M3", options.get(1).displayName());
        assertEquals("EMBEDDING", options.get(1).modelType());
    }

    @Test
    void textModelCheckShouldSendHiToSpecificModel() {
        when(agentModelClient.generateReply(any(AgentModelRequest.class))).thenReturn(new AgentModelResponse("ok"));

        ModelTestResponse response = healthCheckService.testModel(
            "openai",
            "TEXT",
            "gpt-4o-mini",
            baseUrl + "/v1",
            "model-key"
        );

        ArgumentCaptor<AgentModelRequest> request = ArgumentCaptor.forClass(AgentModelRequest.class);
        verify(agentModelClient).generateReply(request.capture());
        assertTrue(response.success());
        assertEquals("模型可用", response.message());
        assertEquals("hi", request.getValue().userMessage());
        JsonNode config = readJson(request.getValue().modelConfigJson());
        assertEquals("gpt-4o-mini", config.path("model").asText());
        assertEquals(baseUrl + "/v1", config.path("baseUrl").asText());
        assertEquals("model-key", config.path("apiKey").asText());
    }

    @Test
    void embeddingModelCheckShouldCallEmbeddingEndpoint() {
        AtomicReference<JsonNode> payload = new AtomicReference<>();
        server.createContext("/v1/embeddings", exchange -> {
            payload.set(OBJECT_MAPPER.readTree(exchange.getRequestBody()));
            sendJson(exchange, 200, "{\"data\":[{\"embedding\":[0.1,0.2]}]}");
        });

        ModelTestResponse response = healthCheckService.testModel(
            "openai",
            "EMBEDDING",
            "text-embedding-3-small",
            baseUrl + "/v1",
            "model-key"
        );

        assertTrue(response.success());
        assertEquals("模型可用", response.message());
        assertEquals("text-embedding-3-small", payload.get().path("model").asText());
        assertEquals("hi", payload.get().path("input").path(0).asText());
    }

    @Test
    void siliconflowTtsModelCheckShouldUseProviderVoice() {
        AtomicReference<JsonNode> payload = new AtomicReference<>();
        server.createContext("/v1/audio/speech", exchange -> {
            payload.set(OBJECT_MAPPER.readTree(exchange.getRequestBody()));
            sendBytes(exchange, 200, new byte[] {1, 2, 3});
        });

        ModelTestResponse response = healthCheckService.testModel(
            "siliconflow",
            "TTS",
            "FunAudioLLM/CosyVoice2-0.5B",
            baseUrl + "/v1",
            "model-key"
        );

        assertTrue(response.success());
        assertEquals("模型可用", response.message());
        assertEquals("FunAudioLLM/CosyVoice2-0.5B", payload.get().path("model").asText());
        assertEquals("FunAudioLLM/CosyVoice2-0.5B:alex", payload.get().path("voice").asText());
        assertEquals("你好", payload.get().path("input").asText());
        assertEquals("mp3", payload.get().path("response_format").asText());
        assertEquals(false, payload.get().path("stream").asBoolean());
    }

    @Test
    void openAiTtsModelCheckShouldKeepAlloyVoice() {
        AtomicReference<JsonNode> payload = new AtomicReference<>();
        server.createContext("/v1/audio/speech", exchange -> {
            payload.set(OBJECT_MAPPER.readTree(exchange.getRequestBody()));
            sendBytes(exchange, 200, new byte[] {1, 2, 3});
        });

        ModelTestResponse response = healthCheckService.testModel(
            "openai",
            "TTS",
            "gpt-4o-mini-tts",
            baseUrl + "/v1",
            "model-key"
        );

        assertTrue(response.success());
        assertEquals("模型可用", response.message());
        assertEquals("gpt-4o-mini-tts", payload.get().path("model").asText());
        assertEquals("alloy", payload.get().path("voice").asText());
        assertEquals("hi", payload.get().path("input").asText());
        assertEquals("mp3", payload.get().path("response_format").asText());
    }

    @Test
    void mimoTtsModelCheckShouldCallChatCompletions() {
        AtomicReference<String> apiKey = new AtomicReference<>();
        AtomicReference<JsonNode> payload = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            payload.set(OBJECT_MAPPER.readTree(exchange.getRequestBody()));
            sendJson(exchange, 200, "{\"choices\":[{\"message\":{\"audio\":{\"data\":\"AQID\"}}}]}");
        });

        ModelTestResponse response = healthCheckService.testModel(
            "openai",
            "TTS",
            "mimo-v2.5-tts",
            baseUrl + "/v1",
            "mimo-key"
        );

        assertTrue(response.success());
        assertEquals("模型可用", response.message());
        assertEquals("mimo-key", apiKey.get());
        assertEquals("mimo-v2.5-tts", payload.get().path("model").asText());
        assertEquals("user", payload.get().path("messages").path(0).path("role").asText());
        assertEquals("assistant", payload.get().path("messages").path(1).path("role").asText());
        assertEquals("你好", payload.get().path("messages").path(1).path("content").asText());
        assertEquals("wav", payload.get().path("audio").path("format").asText());
        assertEquals("mimo_default", payload.get().path("audio").path("voice").asText());
        assertEquals(false, payload.get().path("stream").asBoolean());
    }

    @Test
    void mimoTtsModelCheckShouldRequireAudioData() {
        server.createContext("/v1/chat/completions", exchange -> {
            sendJson(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");
        });

        ModelTestResponse response = healthCheckService.testModel(
            "mimo",
            "TTS",
            "mimo-v2.5-tts",
            baseUrl + "/v1",
            "mimo-key"
        );

        assertEquals(false, response.success());
        assertEquals("模型返回格式错误", response.message());
    }

    @Test
    void visionModelNameShouldUseVisionProbeEvenWhenSavedAsText() {
        AtomicReference<JsonNode> payload = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            payload.set(OBJECT_MAPPER.readTree(exchange.getRequestBody()));
            sendJson(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"hi\"}}]}");
        });

        ModelTestResponse response = healthCheckService.testModel(
            "siliconflow",
            "TEXT",
            "Qwen/Qwen3-VL-8B-Instruct",
            baseUrl + "/v1",
            "model-key"
        );

        assertTrue(response.success());
        assertEquals("模型可用", response.message());
        assertEquals("Qwen/Qwen3-VL-8B-Instruct", payload.get().path("model").asText());
        JsonNode content = payload.get().path("messages").path(0).path("content");
        assertEquals("text", content.path(0).path("type").asText());
        assertEquals("image_url", content.path(1).path("type").asText());
        assertTrue(content.path(1).path("image_url").path("url").asText().startsWith("data:image/png;base64,"));
        assertTrue(content.path(1).path("image_url").path("url").asText().contains("AEAAAABA"));
        verify(agentModelClient, never()).generateReply(any(AgentModelRequest.class));
    }

    @Test
    void responseErrorShouldExposeProviderMessage() {
        server.createContext("/v1/chat/completions", exchange -> {
            sendJson(exchange, 400, "{\"error\":{\"message\":\"image input is invalid\"}}");
        });

        ModelTestResponse response = healthCheckService.testModel(
            "siliconflow",
            "VISION",
            "Qwen/Qwen3-VL-8B-Instruct",
            baseUrl + "/v1",
            "model-key"
        );

        assertEquals(false, response.success());
        assertTrue(response.message().contains("400"));
        assertTrue(response.message().contains("image input is invalid"));
    }

    @Test
    void anthropicTextModelCheckShouldCallMessagesEndpoint() {
        AtomicReference<String> apiKey = new AtomicReference<>();
        AtomicReference<String> version = new AtomicReference<>();
        AtomicReference<JsonNode> payload = new AtomicReference<>();
        server.createContext("/v1/messages", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            version.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
            payload.set(OBJECT_MAPPER.readTree(exchange.getRequestBody()));
            sendJson(exchange, 200, "{\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}");
        });

        ModelTestResponse response = healthCheckService.testModel(
            "anthropic",
            "TEXT",
            "claude-3-5-sonnet-latest",
            baseUrl + "/v1",
            "anthropic-key"
        );

        assertTrue(response.success());
        assertEquals("模型可用", response.message());
        assertEquals("anthropic-key", apiKey.get());
        assertEquals("2023-06-01", version.get());
        assertEquals("claude-3-5-sonnet-latest", payload.get().path("model").asText());
        assertEquals("hi", payload.get().path("messages").path(0).path("content").asText());
        verify(agentModelClient, never()).generateReply(any(AgentModelRequest.class));
    }

    private JsonNode readJson(String value) {
        try {
            return OBJECT_MAPPER.readTree(value);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private void sendJson(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private void sendBytes(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] response) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "audio/mpeg");
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
}
