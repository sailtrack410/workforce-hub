package com.workforcehub.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Chat Completions 模型客户端。
 */
@Service
@EnableConfigurationProperties(AgentModelProperties.class)
public class OpenAiCompatibleAgentModelClient implements AgentModelClient {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private final AgentModelProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleAgentModelClient(AgentModelProperties properties,
                                            ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.getTimeout())
            .build();
    }

    @Override
    public AgentModelResponse generateReply(AgentModelRequest request) {
        ResolvedModelConfig config = resolveConfig(request.modelConfigJson());
        HttpRequest httpRequest = buildHttpRequest(config, request);
        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new AgentModelException("TIMEOUT", "模型服务调用超时", e);
        } catch (IOException e) {
            throw new AgentModelException("PROVIDER_ERROR", "模型服务调用失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentModelException("TIMEOUT", "模型服务调用被中断", e);
        }
        return parseResponse(response);
    }

    private ResolvedModelConfig resolveConfig(String modelConfigJson) {
        if (!properties.isEnabled()) {
            throw new AgentModelException("CONFIG_MISSING", "Agent模型服务未启用");
        }
        JsonNode node = readConfig(modelConfigJson);
        String baseUrl = stringValue(node, "baseUrl", properties.getBaseUrl());
        String apiKey = stringValue(node, "apiKey", properties.getApiKey());
        String model = stringValue(node, "model", properties.getModel());
        Integer maxTokens = intValue(node, "maxTokens", properties.getMaxTokens());
        Double temperature = doubleValue(node, "temperature", properties.getTemperature());
        Duration timeout = durationValue(node, "timeout", properties.getTimeout());
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(apiKey) || !StringUtils.hasText(model)) {
            throw new AgentModelException("CONFIG_MISSING", "Agent模型配置缺失");
        }
        return new ResolvedModelConfig(
            trimTrailingSlash(baseUrl.trim()),
            apiKey.trim(),
            model.trim(),
            maxTokens,
            temperature,
            timeout
        );
    }

    private HttpRequest buildHttpRequest(ResolvedModelConfig config, AgentModelRequest request) {
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            if (StringUtils.hasText(request.systemPrompt())) {
                messages.add(Map.of(
                    "role", "system",
                    "content", request.systemPrompt().trim()
                ));
            }
            messages.add(Map.of(
                "role", "user",
                "content", request.userMessage() == null ? "" : request.userMessage()
            ));
            Map<String, Object> payload = Map.of(
                "model", config.model(),
                "messages", messages,
                "max_tokens", config.maxTokens(),
                "temperature", config.temperature()
            );
            return HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + CHAT_COMPLETIONS_PATH))
                .timeout(config.timeout())
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        } catch (Exception e) {
            throw new AgentModelException("CONFIG_MISSING", "Agent模型请求构造失败", e);
        }
    }

    private AgentModelResponse parseResponse(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        if (statusCode == 401 || statusCode == 403) {
            throw new AgentModelException("AUTH_FAILED", "模型服务认证失败");
        }
        if (statusCode == 429) {
            throw new AgentModelException("RATE_LIMITED", "模型服务限流");
        }
        if (statusCode >= 500) {
            throw new AgentModelException("PROVIDER_ERROR", "模型服务返回错误");
        }
        if (statusCode < 200 || statusCode >= 300) {
            throw new AgentModelException("PROVIDER_ERROR", "模型服务请求失败");
        }
        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            String text = contentNode.isMissingNode() || contentNode.isNull() ? "" : contentNode.asText("");
            if (!StringUtils.hasText(text)) {
                throw new AgentModelException("INVALID_RESPONSE", "模型服务返回内容为空");
            }
            return new AgentModelResponse(text.trim());
        } catch (AgentModelException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentModelException("INVALID_RESPONSE", "模型服务响应不可解析", e);
        }
    }

    private JsonNode readConfig(String modelConfigJson) {
        if (!StringUtils.hasText(modelConfigJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(modelConfigJson);
        } catch (Exception e) {
            throw new AgentModelException("CONFIG_MISSING", "Agent模型配置不是合法JSON", e);
        }
    }

    private String stringValue(JsonNode node, String fieldName, String fallback) {
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull() || !StringUtils.hasText(valueNode.asText())) {
            return fallback;
        }
        return valueNode.asText();
    }

    private Integer intValue(JsonNode node, String fieldName, Integer fallback) {
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull()) {
            return fallback;
        }
        return valueNode.asInt(fallback == null ? 1024 : fallback);
    }

    private Double doubleValue(JsonNode node, String fieldName, Double fallback) {
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull()) {
            return fallback;
        }
        return valueNode.asDouble(fallback == null ? 0.2D : fallback);
    }

    private Duration durationValue(JsonNode node, String fieldName, Duration fallback) {
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull() || !StringUtils.hasText(valueNode.asText())) {
            return fallback;
        }
        try {
            return Duration.parse(valueNode.asText());
        } catch (Exception e) {
            throw new AgentModelException("CONFIG_MISSING", "Agent模型超时配置不合法", e);
        }
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private record ResolvedModelConfig(
        String baseUrl,
        String apiKey,
        String model,
        Integer maxTokens,
        Double temperature,
        Duration timeout
    ) {
    }
}
