package com.workforcehub.ragflow.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RagflowGlobalSettingRow(
    Long id,
    String ocrBaseUrl,
    Boolean ocrApiKeyConfigured,
    String ocrModel,
    String embeddingBaseUrl,
    Boolean embeddingApiKeyConfigured,
    String embeddingModel,
    Integer embeddingDimension,
    Boolean rerankEnabled,
    String rerankBaseUrl,
    Boolean rerankApiKeyConfigured,
    String rerankModel,
    Integer chunkSize,
    Integer chunkOverlap,
    Integer retrievalTopK,
    BigDecimal scoreThreshold,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
