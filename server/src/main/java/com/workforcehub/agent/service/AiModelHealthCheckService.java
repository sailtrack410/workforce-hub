package com.workforcehub.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.agent.dto.ModelTestResponse;
import com.workforcehub.agent.dto.ProviderModelOptionResponse;
import com.workforcehub.agent.model.AgentModelRequest;
import com.workforcehub.agent.model.AgentModelResponse;
import com.workforcehub.agent.model.OpenAiCompatibleAgentModelClient;
import com.workforcehub.agent.repository.AiProviderRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AI 基座连通性检查：供应商检查验证端点和凭据，模型检查验证具体模型能力。
 */
@Service
public class AiModelHealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(AiModelHealthCheckService.class);

    private static final String TEST_IMAGE_BASE64 =
        "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAAAT0lEQVR42u3PQQkAAAgEsItqICMaxgi+hcEKLNXzWgQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQELgu7kIGlFIyTZgAAAABJRU5ErkJggg==";
    private static final String TEST_IMAGE_DATA_URL = "data:image/png;base64," + TEST_IMAGE_BASE64;

    private final ObjectMapper objectMapper;
    private final OpenAiCompatibleAgentModelClient agentModelClient;
    private final RestClient restClient;

    public AiModelHealthCheckService(ObjectMapper objectMapper,
                                     OpenAiCompatibleAgentModelClient agentModelClient,
                                     RestClient.Builder restClientBuilder) {
        this.objectMapper = objectMapper;
        this.agentModelClient = agentModelClient;
        this.restClient = restClientBuilder.build();
    }

    public ModelTestResponse testProvider(AiProviderRow provider) {
        long start = System.currentTimeMillis();
        if (provider == null) {
            return response(false, "供应商不存在", null, start);
        }
        if (!StringUtils.hasText(provider.baseUrl())) {
            return response(false, "未配置端点", null, start);
        }
        if (!StringUtils.hasText(provider.apiKey())) {
            return response(false, "未配置密钥", null, start);
        }
        try {
            var request = restClient.get().uri(endpointUrl(provider.baseUrl(), "models"));
            addAuthHeaders(request, provider.providerType(), provider.apiKey());
            request.retrieve().toBodilessEntity();
            return response(true, "供应商连通", null, start);
        } catch (Exception e) {
            log.warn("AI供应商连通检测失败 baseUrl={}", provider.baseUrl(), e);
            return response(false, friendlyError(e, "供应商连通失败"), null, start);
        }
    }

    public List<ProviderModelOptionResponse> listProviderModels(AiProviderRow provider) {
        if (provider == null) {
            throw new IllegalStateException("供应商不存在");
        }
        if (!StringUtils.hasText(provider.baseUrl())) {
            throw new IllegalStateException("未配置端点");
        }
        if (!StringUtils.hasText(provider.apiKey())) {
            throw new IllegalStateException("未配置密钥");
        }
        try {
            var request = restClient.get().uri(endpointUrl(provider.baseUrl(), "models"));
            addAuthHeaders(request, provider.providerType(), provider.apiKey());
            Map<?, ?> body = request.retrieve().body(Map.class);
            return extractProviderModelOptions(body);
        } catch (Exception e) {
            throw new IllegalStateException(friendlyError(e, "获取模型列表失败"), e);
        }
    }

    public ModelTestResponse testModel(String provider, String modelType, String modelName, String endpoint, String apiKey) {
        return testModel(provider, modelType, modelName, endpoint, apiKey, null);
    }

    public ModelTestResponse testModel(String provider, String modelType, String modelName, String endpoint, String apiKey, Boolean visionFlag) {
        long start = System.currentTimeMillis();
        if (!StringUtils.hasText(modelName)) {
            return response(false, "未配置模型 ID", null, start);
        }
        if (!StringUtils.hasText(endpoint)) {
            return response(false, "未配置端点", null, start);
        }
        if (!StringUtils.hasText(apiKey)) {
            return response(false, "未配置密钥", null, start);
        }
        try {
            String normalizedType = effectiveModelType(modelType, modelName, visionFlag);
            return switch (normalizedType) {
                case "TEXT" -> isAnthropic(provider)
                    ? testAnthropicTextModel(provider, modelName, endpoint, apiKey, start)
                    : testChatModel(provider, modelName, endpoint, apiKey, "hi", start);
                case "VISION", "OCR" -> isAnthropic(provider)
                    ? testAnthropicVisionModel(provider, modelName, endpoint, apiKey, start)
                    : testVisionModel(provider, modelName, endpoint, apiKey, start);
                case "EMBEDDING" -> testEmbeddingModel(provider, modelName, endpoint, apiKey, start);
                case "RERANK" -> testRerankModel(provider, modelName, endpoint, apiKey, start);
                case "TTS" -> isMimoTtsModel(provider, modelName, endpoint)
                    ? testMimoTtsModel(modelName, endpoint, apiKey, start)
                    : testTtsModel(provider, modelName, endpoint, apiKey, start);
                default -> response(false, "模型类型不支持", null, start);
            };
        } catch (Exception e) {
            log.warn("AI模型连通检测失败 provider={} model={}", provider, modelName, e);
            return response(false, friendlyError(e, "模型调用失败"), null, start);
        }
    }

    private ModelTestResponse testChatModel(String provider, String modelName, String endpoint, String apiKey, String userMessage, long start) throws Exception {
        String modelConfigJson = objectMapper.writeValueAsString(Map.of(
            "baseUrl", endpoint,
            "apiKey", apiKey,
            "model", modelName
        ));
        AgentModelResponse reply = agentModelClient.generateReply(new AgentModelRequest(
            0L, 0L, "模型健康检查", "你是一个测试助手。", userMessage, modelConfigJson
        ));
        if (reply == null || !StringUtils.hasText(reply.text())) {
            return response(false, "模型返回内容为空", null, start);
        }
        return response(true, "模型可用", reply.text(), start);
    }

    private ModelTestResponse testAnthropicTextModel(String provider, String modelName, String endpoint, String apiKey, long start) {
        Map<?, ?> body = postJson(provider, endpointUrl(endpoint, "messages"), apiKey, Map.of(
            "model", modelName,
            "max_tokens", 8,
            "messages", List.of(Map.of("role", "user", "content", "hi"))
        ));
        if (!hasAnthropicContent(body)) {
            return response(false, "模型返回格式错误", null, start);
        }
        return response(true, "模型可用", null, start);
    }

    private ModelTestResponse testAnthropicVisionModel(String provider, String modelName, String endpoint, String apiKey, long start) {
        Map<?, ?> body = postJson(provider, endpointUrl(endpoint, "messages"), apiKey, Map.of(
            "model", modelName,
            "max_tokens", 8,
            "messages", List.of(Map.of(
                "role", "user",
                "content", List.of(
                    Map.of("type", "text", "text", "请回复 hi"),
                    Map.of("type", "image", "source", Map.of(
                        "type", "base64",
                        "media_type", "image/png",
                        "data", TEST_IMAGE_BASE64
                    ))
                )
            ))
        ));
        if (!hasAnthropicContent(body)) {
            return response(false, "模型返回格式错误", null, start);
        }
        return response(true, "模型可用", null, start);
    }

    private ModelTestResponse testVisionModel(String provider, String modelName, String endpoint, String apiKey, long start) {
        Map<?, ?> body = postJson(provider, endpointUrl(endpoint, "chat/completions"), apiKey, Map.of(
            "model", modelName,
            "messages", List.of(Map.of(
                "role", "user",
                "content", List.of(
                    Map.of("type", "text", "text", "请回复 hi"),
                    Map.of("type", "image_url", "image_url", Map.of("url", TEST_IMAGE_DATA_URL))
                )
            ))
        ));
        Object choices = body == null ? null : body.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) {
            return response(false, "模型返回格式错误", null, start);
        }
        return response(true, "模型可用", null, start);
    }

    private ModelTestResponse testEmbeddingModel(String provider, String modelName, String endpoint, String apiKey, long start) {
        Map<?, ?> body = postJson(provider, endpointUrl(endpoint, "embeddings"), apiKey, Map.of(
            "model", modelName,
            "input", List.of("hi")
        ));
        Object data = body == null ? null : body.get("data");
        if (!(data instanceof List<?> list) || list.isEmpty()) {
            return response(false, "模型返回格式错误", null, start);
        }
        return response(true, "模型可用", null, start);
    }

    private ModelTestResponse testRerankModel(String provider, String modelName, String endpoint, String apiKey, long start) {
        postJson(provider, endpointUrl(endpoint, "rerank"), apiKey, Map.of(
            "model", modelName,
            "query", "hi",
            "documents", List.of("hi", "hello")
        ));
        return response(true, "模型可用", null, start);
    }

    private ModelTestResponse testTtsModel(String provider, String modelName, String endpoint, String apiKey, long start) {
        byte[] bytes = postBytes(provider, endpointUrl(endpoint, "audio/speech"), apiKey, ttsProbeBody(provider, modelName));
        if (bytes == null || bytes.length == 0) {
            return response(false, "模型返回内容为空", null, start);
        }
        return response(true, "模型可用", null, start);
    }

    private ModelTestResponse testMimoTtsModel(String modelName, String endpoint, String apiKey, long start) {
        Map<?, ?> body = postJson("mimo", endpointUrl(endpoint, "chat/completions"), apiKey, Map.of(
            "model", modelName,
            "messages", List.of(
                Map.of("role", "user", "content", "自然清晰，语速正常。"),
                Map.of("role", "assistant", "content", "你好")
            ),
            "audio", Map.of("format", "wav", "voice", "mimo_default"),
            "stream", false
        ));
        if (!hasOpenAiAudioContent(body)) {
            return response(false, "模型返回格式错误", null, start);
        }
        return response(true, "模型可用", null, start);
    }

    private Map<String, ?> ttsProbeBody(String provider, String modelName) {
        if ("siliconflow".equalsIgnoreCase(provider)) {
            return Map.of(
                "model", modelName,
                "input", ttsProbeInput(modelName),
                "voice", modelName + ":alex",
                "response_format", "mp3",
                "stream", false
            );
        }
        return Map.of(
            "model", modelName,
            "input", "hi",
            "voice", "alloy",
            "response_format", "mp3"
        );
    }

    private String ttsProbeInput(String modelName) {
        String normalized = modelName == null ? "" : modelName.toLowerCase(Locale.ROOT);
        if (normalized.contains("moss-ttsd")) {
            return "[S1]你好";
        }
        return "你好";
    }

    private List<ProviderModelOptionResponse> extractProviderModelOptions(Map<?, ?> body) {
        Object rawModels = body == null ? null : body.get("data");
        if (!(rawModels instanceof List<?> list)) {
            rawModels = body == null ? null : body.get("models");
        }
        if (!(rawModels instanceof List<?> list)) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<ProviderModelOptionResponse> result = new ArrayList<>();
        for (Object item : list) {
            String modelName = modelNameValue(item);
            if (!StringUtils.hasText(modelName) || !seen.add(modelName)) {
                continue;
            }
            String displayName = modelDisplayName(item, modelName);
            result.add(new ProviderModelOptionResponse(modelName, displayName, guessModelType(modelName)));
        }
        return result;
    }

    private String modelNameValue(Object item) {
        if (item instanceof String value) {
            return value.trim();
        }
        if (item instanceof Map<?, ?> map) {
            for (String key : List.of("id", "model", "name")) {
                String value = stringValue(map.get(key));
                if (StringUtils.hasText(value)) return value;
            }
        }
        return null;
    }

    private String modelDisplayName(Object item, String fallback) {
        if (item instanceof Map<?, ?> map) {
            for (String key : List.of("display_name", "displayName", "name")) {
                String value = stringValue(map.get(key));
                if (StringUtils.hasText(value)) return value;
            }
        }
        return fallback;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String guessModelType(String modelName) {
        String value = modelName.toLowerCase(Locale.ROOT);
        if (value.contains("rerank")) return "RERANK";
        if (value.contains("embed") || value.contains("embedding") || value.contains("bge") || value.contains("e5-")) {
            return "EMBEDDING";
        }
        if (value.contains("tts") || value.contains("speech")) return "TTS";
        if (value.contains("ocr")) return "OCR";
        if (value.contains("vision") || value.contains("-vl") || value.contains("/vl") || value.contains("qwen-vl")) {
            return "VISION";
        }
        return "TEXT";
    }

    private String effectiveModelType(String modelType, String modelName, Boolean visionFlag) {
        String normalizedType = StringUtils.hasText(modelType) ? modelType.trim().toUpperCase(Locale.ROOT) : "TEXT";
        if (("TEXT".equals(normalizedType) || !StringUtils.hasText(normalizedType))
            && (Boolean.TRUE.equals(visionFlag) || "VISION".equals(guessModelType(modelName)))) {
            return "VISION";
        }
        return normalizedType;
    }

    private boolean isMimoTtsModel(String provider, String modelName, String endpoint) {
        String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        String normalizedModel = modelName == null ? "" : modelName.trim().toLowerCase(Locale.ROOT);
        String normalizedEndpoint = endpoint == null ? "" : endpoint.trim().toLowerCase(Locale.ROOT);
        return "mimo".equals(normalizedProvider)
            || normalizedModel.startsWith("mimo-v") && normalizedModel.contains("-tts")
            || normalizedEndpoint.contains("xiaomimimo.com")
            || normalizedEndpoint.contains("mimo.mi.com");
    }

    private Map<?, ?> postJson(String provider, String url, String apiKey, Map<String, ?> body) {
        var request = restClient.post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body);
        addAuthHeaders(request, provider, apiKey);
        return request.retrieve().body(Map.class);
    }

    private byte[] postBytes(String provider, String url, String apiKey, Map<String, ?> body) {
        var request = restClient.post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body);
        addAuthHeaders(request, provider, apiKey);
        return request.retrieve().body(byte[].class);
    }

    private void addAuthHeaders(RestClient.RequestHeadersSpec<?> request, String provider, String apiKey) {
        if ("anthropic".equalsIgnoreCase(provider)) {
            request.header("x-api-key", apiKey.trim());
            request.header("anthropic-version", "2023-06-01");
            return;
        }
        if ("mimo".equalsIgnoreCase(provider)) {
            request.header("api-key", apiKey.trim());
            return;
        }
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim());
    }

    private boolean isAnthropic(String provider) {
        return "anthropic".equalsIgnoreCase(provider);
    }

    private boolean hasAnthropicContent(Map<?, ?> body) {
        Object content = body == null ? null : body.get("content");
        return content instanceof List<?> list && !list.isEmpty();
    }

    private boolean hasOpenAiAudioContent(Map<?, ?> body) {
        Object choices = body == null ? null : body.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) {
            return false;
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> choice)) {
            return false;
        }
        Object message = choice.get("message");
        if (!(message instanceof Map<?, ?> messageMap)) {
            return false;
        }
        Object audio = messageMap.get("audio");
        if (!(audio instanceof Map<?, ?> audioMap)) {
            return false;
        }
        return StringUtils.hasText(stringValue(audioMap.get("data")));
    }

    private String endpointUrl(String baseUrl, String path) {
        String trimmed = trimTrailingSlash(baseUrl.trim());
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        trimmed = stripKnownEndpoint(trimmed);
        if (trimmed.endsWith("/" + normalizedPath)) {
            return trimmed;
        }
        if (trimmed.endsWith("/v1")) {
            return trimmed + "/" + normalizedPath;
        }
        return trimmed + "/v1/" + normalizedPath;
    }

    private String stripKnownEndpoint(String value) {
        for (String suffix : List.of("/chat/completions", "/embeddings", "/rerank", "/audio/speech", "/models", "/messages")) {
            if (value.endsWith(suffix)) {
                return trimTrailingSlash(value.substring(0, value.length() - suffix.length()));
            }
        }
        return value;
    }

    private String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private ModelTestResponse response(boolean success, String message, String sampleReply, long start) {
        return new ModelTestResponse(success, message, sampleReply, System.currentTimeMillis() - start);
    }

    private String friendlyError(Exception e, String fallback) {
        if (e instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            if (status == 401 || status == 403) return "认证失败";
            if (status == 404) return "端点不可用";
            if (status == 429) return "调用限流";
            String detail = responseErrorMessage(responseException);
            return StringUtils.hasText(detail) ? fallback + "：" + status + "，" + detail : fallback + "：" + status;
        }
        if (e instanceof RestClientException) {
            return fallback;
        }
        if (StringUtils.hasText(e.getMessage())) {
            String message = e.getMessage().replaceAll("\\s+", " ").trim();
            return message.length() > 160 ? message.substring(0, 160) : message;
        }
        return fallback;
    }

    private String responseErrorMessage(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        if (!StringUtils.hasText(body)) return null;
        try {
            Object value = objectMapper.readValue(body, Object.class);
            String message = responseErrorMessage(value);
            return compactMessage(message);
        } catch (Exception ignored) {
            return compactMessage(body);
        }
    }

    private String responseErrorMessage(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of("message", "msg", "detail", "error")) {
                Object nested = map.get(key);
                String message = responseErrorMessage(nested);
                if (StringUtils.hasText(message)) return message;
            }
            return null;
        }
        if (value instanceof List<?> list && !list.isEmpty()) {
            return responseErrorMessage(list.get(0));
        }
        return value == null ? null : String.valueOf(value);
    }

    private String compactMessage(String message) {
        if (!StringUtils.hasText(message)) return null;
        String compact = message.replaceAll("\\s+", " ").trim();
        return compact.length() > 120 ? compact.substring(0, 120) : compact;
    }
}
