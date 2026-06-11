package com.workforcehub.ragflow.service;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.ragflow.repository.RagflowGlobalSettingRow;
import com.workforcehub.ragflow.repository.RagflowRepository;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class RagflowEmbeddingClient {

    private final RagflowRepository ragflowRepository;
    private final RestClient restClient;

    public RagflowEmbeddingClient(RagflowRepository ragflowRepository,
                                  RestClient.Builder restClientBuilder) {
        this.ragflowRepository = ragflowRepository;
        this.restClient = restClientBuilder.build();
    }

    public List<List<Double>> embed(List<String> inputs) {
        RagflowGlobalSettingRow setting = ragflowRepository.getGlobalSetting();
        if (!StringUtils.hasText(setting.embeddingBaseUrl()) || !StringUtils.hasText(setting.embeddingModel())) {
            throw new BizException(422, "Embedding模型未配置");
        }
        if (!Boolean.TRUE.equals(setting.embeddingApiKeyConfigured())) {
            throw new BizException(422, "Embedding Key未配置");
        }
        try {
            Map<?, ?> response = restClient.post()
                .uri(embeddingUrl(setting.embeddingBaseUrl()))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + ragflowRepository.getRawEmbeddingApiKey())
                .body(Map.of(
                    "model", setting.embeddingModel(),
                    "input", inputs
                ))
                .retrieve()
                .body(Map.class);
            List<List<Double>> vectors = parseVectors(response);
            if (vectors.size() != inputs.size()) {
                throw new BizException(502, "Embedding返回数量不匹配");
            }
            if (setting.embeddingDimension() != null) {
                for (List<Double> vector : vectors) {
                    if (vector.size() != setting.embeddingDimension()) {
                        throw new BizException(502, "Embedding维度不匹配");
                    }
                }
            }
            return vectors;
        } catch (BizException e) {
            throw e;
        } catch (HttpStatusCodeException e) {
            throw new BizException(502, "Embedding调用失败：" + compact(e.getResponseBodyAsString()));
        } catch (Exception e) {
            throw new BizException(502, "Embedding调用失败");
        }
    }

    public List<Double> embedOne(String input) {
        return embed(List.of(input)).get(0);
    }

    private String embeddingUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/embeddings")) {
            return trimmed;
        }
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/v1")) {
            return trimmed + "/embeddings";
        }
        return trimmed + "/v1/embeddings";
    }

    private List<List<Double>> parseVectors(Map<?, ?> response) {
        Object data = response == null ? null : response.get("data");
        if (!(data instanceof List<?> list)) {
            throw new BizException(502, "Embedding返回格式错误");
        }
        List<List<Double>> vectors = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row) || !(row.get("embedding") instanceof List<?> embedding)) {
                throw new BizException(502, "Embedding返回格式错误");
            }
            List<Double> vector = new ArrayList<>();
            for (Object value : embedding) {
                if (!(value instanceof Number number)) {
                    throw new BizException(502, "Embedding返回格式错误");
                }
                vector.add(number.doubleValue());
            }
            vectors.add(vector);
        }
        return vectors;
    }

    private String compact(String value) {
        if (!StringUtils.hasText(value)) {
            return "模型服务无响应内容";
        }
        String text = value.replaceAll("\\s+", " ").trim();
        return text.length() > 160 ? text.substring(0, 160) : text;
    }
}
