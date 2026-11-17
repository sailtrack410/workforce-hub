package com.workforcehub.ragflow.service;

import com.workforcehub.agent.repository.AgentRepository;
import com.workforcehub.agent.repository.LlmModelRow;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.file.repository.FileObjectRepository;
import com.workforcehub.file.repository.FileObjectRow;
import com.workforcehub.file.storage.StorageObjectResource;
import com.workforcehub.file.storage.StorageProviderRegistry;
import com.workforcehub.ragflow.repository.RagflowGlobalSettingRow;
import com.workforcehub.ragflow.repository.RagflowRepository;
import com.workforcehub.ragflow.repository.RagflowVectorRepository;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.xml.sax.ContentHandler;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RagflowIngestService {

    private static final Logger log = LoggerFactory.getLogger(RagflowIngestService.class);

    private final RagflowRepository ragflowRepository;
    private final AgentRepository agentRepository;
    private final FileObjectRepository fileObjectRepository;
    private final StorageProviderRegistry storageProviderRegistry;
    private final RagflowEmbeddingClient embeddingClient;
    private final RagflowConfigResolver configResolver;
    private final RagflowVectorRepository vectorRepository;
    private final RestClient restClient;

    public RagflowIngestService(RagflowRepository ragflowRepository,
                                AgentRepository agentRepository,
                                FileObjectRepository fileObjectRepository,
                                StorageProviderRegistry storageProviderRegistry,
                                RagflowEmbeddingClient embeddingClient,
                                RagflowConfigResolver configResolver,
                                RagflowVectorRepository vectorRepository,
                                RestClient.Builder restClientBuilder) {
        this.ragflowRepository = ragflowRepository;
        this.agentRepository = agentRepository;
        this.fileObjectRepository = fileObjectRepository;
        this.storageProviderRegistry = storageProviderRegistry;
        this.embeddingClient = embeddingClient;
        this.configResolver = configResolver;
        this.vectorRepository = vectorRepository;
        this.restClient = restClientBuilder.build();
    }

    // 文档入库主流程：读取文件 → 解析文本 → 分块 → 向量化 → 写入向量库 → 更新状态
    public void ingest(Long spaceId, Long documentId, Long jobId, Long fileObjectId, String title, String mimeType, String parserType) {
        ragflowRepository.markIngestRunning(documentId, jobId);
        try {
            RagflowGlobalSettingRow setting = ragflowRepository.getGlobalSetting();
            RagflowEffectiveConfig config = configResolver.resolve(ragflowRepository.getSpaceOrThrow(spaceId), setting);
            FileObjectRow fileObject = fileObjectRepository.getFileObjectOrThrow(fileObjectId);
            StorageObjectResource object = storageProviderRegistry.require(fileObject.storageProvider())
                .loadObject(fileObject.bucketName(), fileObject.objectKey());
            byte[] bytes = object.resource().getContentAsByteArray();
            String text = parseText(bytes, title, mimeType, parserType);
            List<String> chunks = splitChunks(text, config);
            if (chunks.isEmpty()) {
                throw new BizException(422, "未解析出可入库内容");
            }
            vectorRepository.requireAvailable();
            List<List<Double>> embeddings = embeddingClient.embed(chunks, config.embeddingModelId(), config.embeddingDimension());
            List<Long> vectorIds = vectorRepository.replaceDocumentVectors(spaceId, documentId, jobId, chunks, embeddings);
            ragflowRepository.replaceChunks(spaceId, documentId, jobId, chunks, vectorIds);
            ragflowRepository.markIngestSuccess(documentId, jobId, text, chunks.size());
            ragflowRepository.refreshSpaceCounts(spaceId);
        } catch (Exception | LinkageError e) {
            String message = e instanceof BizException ? e.getMessage() : "文件解析失败";
            // 库内只落友好 message，根因堆栈必须进日志，否则无法排查
            log.warn("文档入库失败 spaceId={} documentId={} jobId={}", spaceId, documentId, jobId, e);
            ragflowRepository.markIngestFailed(documentId, jobId, message);
            ragflowRepository.refreshSpaceCounts(spaceId);
        }
    }

    // 文本提取策略：图片走 OCR，纯文本直接读取，其余用 Tika 自动检测解析
    private String parseText(byte[] bytes, String title, String mimeType, String parserType) {
        String text = ((mimeType == null ? "" : mimeType) + " " + (title == null ? "" : title) + " " + (parserType == null ? "" : parserType)).toLowerCase(Locale.ROOT);
        if (text.contains("image") || text.endsWith(".png") || text.endsWith(".jpg") || text.endsWith(".jpeg") || text.endsWith(".webp") || text.contains("ocr")) {
            return ocr(bytes, mimeType);
        }
        if (text.contains("text") || text.contains("csv") || text.endsWith(".txt") || text.endsWith(".md") || text.endsWith(".csv")) {
            return new String(bytes, StandardCharsets.UTF_8).trim();
        }
        try {
            ContentHandler handler = new BodyContentHandler(-1);
            new AutoDetectParser().parse(new ByteArrayInputStream(bytes), handler, new Metadata());
            String parsed = handler.toString().trim();
            if (StringUtils.hasText(parsed)) {
                return parsed;
            }
        } catch (Exception e) {
            throw new BizException(422, "文件解析失败");
        }
        throw new BizException(422, "未解析出可入库内容");
    }

    private String ocr(byte[] bytes, String mimeType) {
        RagflowGlobalSettingRow setting = ragflowRepository.getGlobalSetting();
        LlmModelRow model = setting.ocrModelId() == null ? null : agentRepository.getLlmModel(setting.ocrModelId());
        String baseUrl = model == null ? setting.ocrBaseUrl() : model.endpoint();
        String modelName = model == null ? setting.ocrModel() : model.modelName();
        String apiKey = model == null ? ocrApiKey() : model.apiKey();
        if (model != null && (!"ACTIVE".equals(model.status()) || !"OCR".equals(model.modelType()))) {
            throw new BizException(422, "OCR模型未配置");
        }
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(modelName)) {
            throw new BizException(422, "OCR模型未配置");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException(422, "OCR Key未配置");
        }
        String dataUrl = "data:" + (StringUtils.hasText(mimeType) ? mimeType : "image/png") + ";base64," + Base64.getEncoder().encodeToString(bytes);
        Map<?, ?> response = restClient.post()
            .uri(chatUrl(baseUrl))
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + apiKey)
            .body(Map.of(
                "model", modelName,
                "messages", List.of(Map.of(
                    "role", "user",
                    "content", List.of(
                        Map.of("type", "text", "text", "请只返回图片中的可读文字。"),
                        Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))
                    )
                ))
            ))
            .retrieve()
            .body(Map.class);
        Object choices = response == null ? null : response.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> choice) {
            Object message = choice.get("message");
            if (message instanceof Map<?, ?> msg && msg.get("content") != null) {
                return String.valueOf(msg.get("content")).trim();
            }
        }
        throw new BizException(422, "OCR未返回文本");
    }

    private String ocrApiKey() {
        RagflowGlobalSettingRow setting = ragflowRepository.getGlobalSetting();
        if (!Boolean.TRUE.equals(setting.ocrApiKeyConfigured())) {
            throw new BizException(422, "OCR Key未配置");
        }
        return ragflowRepository.getRawOcrApiKey();
    }

    private String chatUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/chat/completions")) {
            return trimmed;
        }
        if (trimmed.endsWith("/v1")) {
            return trimmed + "/chat/completions";
        }
        return trimmed + "/v1/chat/completions";
    }

    // 文本分块：按 chunkSize 切分，overlap 保证上下文连续性，避免语义截断
    private List<String> splitChunks(String text, RagflowEffectiveConfig config) {
        String normalized = text == null ? "" : text.trim();
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        int chunkSize = config.chunkSize() == null ? 800 : Math.max(100, config.chunkSize());
        int overlap = config.chunkOverlap() == null ? 100 : Math.max(0, Math.min(config.chunkOverlap(), chunkSize - 1));
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + chunkSize);
            chunks.add(normalized.substring(start, end));
            if (end >= normalized.length()) break;
            start = end - overlap;
        }
        return chunks;
    }
}
