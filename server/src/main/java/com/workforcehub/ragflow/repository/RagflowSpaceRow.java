package com.workforcehub.ragflow.repository;

import java.time.LocalDateTime;

public record RagflowSpaceRow(
    Long id,
    String spaceName,
    String spaceCode,
    String description,
    String visibility,
    Integer documentCount,
    Integer chunkCount,
    String embeddingProvider,
    String embeddingModel,
    Integer embeddingDimension,
    Boolean rerankEnabled,
    String rerankProvider,
    String rerankModel,
    Integer chunkSize,
    Integer chunkOverlap,
    Integer retrievalTopK,
    java.math.BigDecimal scoreThreshold,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
