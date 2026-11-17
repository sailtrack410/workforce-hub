package com.workforcehub.ragflow.service;

import com.workforcehub.agent.repository.AgentRepository;
import com.workforcehub.agent.repository.LlmModelRow;
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
    private final AgentRepository agentRepository;
    private final RestClient restClient;

    public RagflowEmbeddingClient(RagflowRepository ragflowRepository,
                                  AgentRepository agentRepository,
                                  RestClient.Builder restClientBuilder) {
        this.ragflowRepository = ragflowRepository;
        this.agentRepository = agentRepository;
        this.restClient = restClientBuilder.build();
    }

    // 批量向量化：调用 Embedding API，校验返回数量和维度一致性
    public List<List<Double>> embed(List<String> inputs) {
        return embed(inputs, null);
    }

    public List<List<Double>> embed(List<String> inputs, Long modelId) {
        RagflowGlobalSettingRow setting = ragflowRepository.getGlobalSetting();
        return embed(inputs, modelId, setting.embeddingDimension(), setting);
    }

    public List<List<Double>> embed(List<String> inputs, Long modelId, Integer expectedDimension) {
        RagflowGlobalSettingRow setting = ragflowRepository.getGlobalSetting();
        return embed(inputs, modelId, expectedDimension, setting);
    }

    private List<List<Double>> embed(List<String> inputs, Long modelId, Integer expectedDimension, RagflowGlobalSettingRow setting) {
        Long resolvedModelId = modelId == null ? setting.embeddingModelId() : modelId;
        LlmModelRow model = resolvedModelId == null ? null : agentRepository.getLlmModel(resolvedModelId);
        String baseUrl = model == null ? setting.embeddingBaseUrl() : model.endpoint();
        String modelName = model == null ? setting.embeddingModel() : model.modelName();
        String apiKey = model == null
            ? (Boolean.TRUE.equals(setting.embeddingApiKeyConfigured()) ? ragflowRepository.getRawEmbeddingApiKey() : null)
            : model.apiKey();
        if (model != null && (!"ACTIVE".equals(model.status()) || !"EMBEDDING".equals(model.modelType()))) {
            throw new BizException(422, "Embedding模型未配置");
        }
        return embedWithConfig(inputs, baseUrl, apiKey, modelName, expectedDimension);
    }

    private List<List<Double>> embedWithConfig(List<String> inputs,
                                               String baseUrl,
                                               String apiKey,
                                               String modelName,
                                               Integer expectedDimension) {
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(modelName)) {
            throw new BizException(422, "Embedding模型未配置");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException(422, "Embedding Key未配置");
        }
        try {
            Map<?, ?> response = restClient.post()
                .uri(embeddingUrl(baseUrl))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(Map.of(
                    "model", modelName,
                    "input", inputs
                ))
                .retrieve()
                .body(Map.class);
            List<List<Double>> vectors = parseVectors(response);
            if (vectors.size() != inputs.size()) {
                throw new BizException(502, "Embedding返回数量不匹配");
            }
            if (expectedDimension != null) {
                for (List<Double> vector : vectors) {
                    if (vector.size() != expectedDimension) {
                        throw new BizException(502, "Embedding维度不匹配：期望" + expectedDimension + "维，实际" + vector.size() + "维");
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

    public List<Double> embedOne(String input, Long modelId) {
        return embed(List.of(input), modelId).get(0);
    }

    public List<Double> embedOne(String input, Long modelId, Integer expectedDimension) {
        return embed(List.of(input), modelId, expectedDimension).get(0);
    }

    public int detectDimension(Long modelId) {
        return embedOne("dimension probe", modelId, null).size();
    }

    public int detectDimension(String baseUrl, String apiKey, String modelName) {
        return embedWithConfig(List.of("dimension probe"), baseUrl, apiKey, modelName, null).get(0).size();
    }

    // 自动补全 Embedding API 路径：兼容 /v1、/v1/embeddings、裸地址等格式
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
