package com.workforcehub.ragflow.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RagflowGlobalSettingRow(
    Long id,
    String ocrBaseUrl,
    Boolean ocrApiKeyConfigured,
    String ocrModel,
    Long ocrModelId,
    String embeddingBaseUrl,
    Boolean embeddingApiKeyConfigured,
    String embeddingModel,
    Long embeddingModelId,
    Integer embeddingDimension,
    Boolean rerankEnabled,
    String rerankBaseUrl,
    Boolean rerankApiKeyConfigured,
    String rerankModel,
    Long rerankModelId,
    Integer chunkSize,
    Integer chunkOverlap,
    Integer retrievalTopK,
    BigDecimal scoreThreshold,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
