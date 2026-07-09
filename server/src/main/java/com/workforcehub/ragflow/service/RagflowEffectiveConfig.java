package com.workforcehub.ragflow.service;

import java.math.BigDecimal;

record RagflowEffectiveConfig(
    Long embeddingModelId,
    Integer embeddingDimension,
    Integer chunkSize,
    Integer chunkOverlap,
    Integer retrievalTopK,
    BigDecimal scoreThreshold
) {
}
