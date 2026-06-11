package com.workforcehub.ragflow.service;

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

    private final RagflowRepository ragflowRepository;
    private final FileObjectRepository fileObjectRepository;
    private final StorageProviderRegistry storageProviderRegistry;
    private final RagflowEmbeddingClient embeddingClient;
    private final RagflowVectorRepository vectorRepository;
    private final RestClient restClient;

    public RagflowIngestService(RagflowRepository ragflowRepository,
                                FileObjectRepository fileObjectRepository,
                                StorageProviderRegistry storageProviderRegistry,
                                RagflowEmbeddingClient embeddingClient,
                                RagflowVectorRepository vectorRepository,
                                RestClient.Builder restClientBuilder) {
        this.ragflowRepository = ragflowRepository;
        this.fileObjectRepository = fileObjectRepository;
        this.storageProviderRegistry = storageProviderRegistry;
        this.embeddingClient = embeddingClient;
        this.vectorRepository = vectorRepository;
        this.restClient = restClientBuilder.build();
    }

    public void ingest(Long spaceId, Long documentId, Long jobId, Long fileObjectId, String title, String mimeType, String parserType) {
        ragflowRepository.markIngestRunning(documentId, jobId);
        try {
            RagflowGlobalSettingRow setting = ragflowRepository.getGlobalSetting();
            FileObjectRow fileObject = fileObjectRepository.getFileObjectOrThrow(fileObjectId);
            StorageObjectResource object = storageProviderRegistry.require(fileObject.storageProvider())
                .loadObject(fileObject.bucketName(), fileObject.objectKey());
            byte[] bytes = object.resource().getContentAsByteArray();
            String text = parseText(bytes, title, mimeType, parserType);
            List<String> chunks = splitChunks(text, setting);
            if (chunks.isEmpty()) {
                throw new BizException(422, "未解析出可入库内容");
            }
            vectorRepository.requireAvailable();
            List<List<Double>> embeddings = embeddingClient.embed(chunks);
            List<Long> vectorIds = vectorRepository.replaceDocumentVectors(spaceId, documentId, jobId, chunks, embeddings);
            ragflowRepository.replaceChunks(spaceId, documentId, jobId, chunks, vectorIds);
            ragflowRepository.markIngestSuccess(documentId, jobId, text, chunks.size());
            ragflowRepository.refreshSpaceCounts(spaceId);
        } catch (Exception | LinkageError e) {
            String message = e instanceof BizException ? e.getMessage() : "文件解析失败";
            ragflowRepository.markIngestFailed(documentId, jobId, message);
            ragflowRepository.refreshSpaceCounts(spaceId);
        }
    }

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
        if (!StringUtils.hasText(setting.ocrBaseUrl()) || !StringUtils.hasText(setting.ocrModel())) {
            throw new BizException(422, "OCR模型未配置");
        }
        String dataUrl = "data:" + (StringUtils.hasText(mimeType) ? mimeType : "image/png") + ";base64," + Base64.getEncoder().encodeToString(bytes);
        Map<?, ?> response = restClient.post()
            .uri(setting.ocrBaseUrl())
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + ocrApiKey())
            .body(Map.of(
                "model", setting.ocrModel(),
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

    private List<String> splitChunks(String text, RagflowGlobalSettingRow setting) {
        String normalized = text == null ? "" : text.trim();
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        int chunkSize = setting.chunkSize() == null ? 800 : Math.max(100, setting.chunkSize());
        int overlap = setting.chunkOverlap() == null ? 100 : Math.max(0, Math.min(setting.chunkOverlap(), chunkSize - 1));
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
