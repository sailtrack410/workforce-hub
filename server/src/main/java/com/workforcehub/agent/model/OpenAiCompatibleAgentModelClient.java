package com.workforcehub.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring AI OpenAI 兼容 Chat Completions 模型客户端。
 */
@Service
@EnableConfigurationProperties(AgentModelProperties.class)
public class OpenAiCompatibleAgentModelClient {

    private static final String CHAT_COMPLETIONS_SUFFIX = "/chat/completions";
    private static final String VERSIONED_CHAT_COMPLETIONS_SUFFIX = "/v1/chat/completions";
    private static final Pattern SPRING_AI_STATUS_MESSAGE = Pattern.compile("^(\\d{3})\\s+-\\s+.*", Pattern.DOTALL);

    private final AgentModelProperties properties;
    private final ObjectMapper objectMapper;
    private final SpringAiOpenAiChatClientFactory chatClientFactory;

    public OpenAiCompatibleAgentModelClient(AgentModelProperties properties,
                                            ObjectMapper objectMapper,
                                            SpringAiOpenAiChatClientFactory chatClientFactory) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.chatClientFactory = chatClientFactory;
    }

    public AgentModelResponse generateReply(AgentModelRequest request) {
        SpringAiOpenAiChatClientFactory.ResolvedAgentModelConfig config = resolveConfig(request.modelConfigJson());
        try {
            OpenAiChatClient chatClient = chatClientFactory.create(config);
            ChatResponse response = chatClient.call(buildPrompt(request));
            return parseResponse(response);
        } catch (AgentModelException e) {
            throw e;
        } catch (NonTransientAiException e) {
            throw mapSpringAiStatusException(e);
        } catch (TransientAiException e) {
            throw new AgentModelException("PROVIDER_ERROR", "模型服务调用失败", e);
        } catch (RestClientResponseException e) {
            throw mapResponseException(e);
        } catch (RestClientException e) {
            if (e.contains(java.net.http.HttpTimeoutException.class)) {
                throw new AgentModelException("TIMEOUT", "模型服务调用超时", e);
            }
            throw new AgentModelException("PROVIDER_ERROR", "模型服务调用失败", e);
        }
    }

    private SpringAiOpenAiChatClientFactory.ResolvedAgentModelConfig resolveConfig(String modelConfigJson) {
        if (!properties.isEnabled()) {
            throw new AgentModelException("CONFIG_MISSING", "Agent模型服务未启用");
        }
        JsonNode node = readConfig(modelConfigJson);
        String baseUrl = normalizeBaseUrl(stringValue(node, "baseUrl", properties.getBaseUrl()));
        String apiKey = stringValue(node, "apiKey", properties.getApiKey());
        String model = stringValue(node, "model", properties.getModel());
        Integer maxTokens = intValue(node, "maxTokens", properties.getMaxTokens());
        Double temperature = doubleValue(node, "temperature", properties.getTemperature());
        Duration timeout = durationValue(node, "timeout", properties.getTimeout());
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(apiKey) || !StringUtils.hasText(model)) {
            throw new AgentModelException("CONFIG_MISSING", "Agent模型配置缺失");
        }
        return new SpringAiOpenAiChatClientFactory.ResolvedAgentModelConfig(
            baseUrl,
            apiKey.trim(),
            model.trim(),
            maxTokens,
            temperature,
            timeout
        );
    }

    private Prompt buildPrompt(AgentModelRequest request) {
        List<Message> messages = new ArrayList<>();
        if (StringUtils.hasText(request.systemPrompt())) {
            messages.add(new SystemMessage(request.systemPrompt().trim()));
        }
        messages.add(new UserMessage(request.userMessage() == null ? "" : request.userMessage()));
        return new Prompt(messages);
    }

    private AgentModelResponse parseResponse(ChatResponse response) {
        String text = response == null || response.getResult() == null
            || response.getResult().getOutput() == null
            ? ""
            : response.getResult().getOutput().getContent();
        if (!StringUtils.hasText(text)) {
            throw new AgentModelException("INVALID_RESPONSE", "模型服务返回内容为空");
        }
        return new AgentModelResponse(text.trim());
    }

    private AgentModelException mapSpringAiStatusException(NonTransientAiException e) {
        Integer statusCode = extractSpringAiStatusCode(e);
        if (statusCode == null) {
            return new AgentModelException("PROVIDER_ERROR", "模型服务请求失败", e);
        }
        if (statusCode == 401 || statusCode == 403) {
            return new AgentModelException("AUTH_FAILED", "模型服务认证失败", e);
        }
        if (statusCode == 429) {
            return new AgentModelException("RATE_LIMITED", "模型服务限流", e);
        }
        return new AgentModelException("PROVIDER_ERROR", "模型服务请求失败", e);
    }

    private Integer extractSpringAiStatusCode(NonTransientAiException e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) {
            return null;
        }
        Matcher matcher = SPRING_AI_STATUS_MESSAGE.matcher(message);
        if (!matcher.matches()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private AgentModelException mapResponseException(RestClientResponseException e) {
        int statusCode = e.getRawStatusCode();
        if (statusCode == 401 || statusCode == 403) {
            return new AgentModelException("AUTH_FAILED", "模型服务认证失败", e);
        }
        if (statusCode == 429) {
            return new AgentModelException("RATE_LIMITED", "模型服务限流", e);
        }
        if (statusCode >= 500) {
            return new AgentModelException("PROVIDER_ERROR", "模型服务返回错误", e);
        }
        return new AgentModelException("PROVIDER_ERROR", "模型服务请求失败", e);
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
            return DurationStyle.detectAndParse(valueNode.asText().trim());
        } catch (Exception e) {
            throw new AgentModelException("CONFIG_MISSING", "Agent模型超时配置不合法", e);
        }
    }

    private String normalizeBaseUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = trimTrailingSlash(value.trim());
        if (trimmed.endsWith(VERSIONED_CHAT_COMPLETIONS_SUFFIX)) {
            return trimmed.substring(0, trimmed.length() - VERSIONED_CHAT_COMPLETIONS_SUFFIX.length());
        }
        if (trimmed.endsWith(CHAT_COMPLETIONS_SUFFIX)) {
            return trimmed.substring(0, trimmed.length() - CHAT_COMPLETIONS_SUFFIX.length());
        }
        if (trimmed.endsWith("/v1")) {
            return trimmed.substring(0, trimmed.length() - "/v1".length());
        }
        return trimmed;
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
