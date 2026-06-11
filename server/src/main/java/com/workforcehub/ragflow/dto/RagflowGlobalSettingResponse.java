package com.workforcehub.ragflow.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RagflowGlobalSettingResponse {
    private Long settingId;
    private String ocrBaseUrl;
    private Boolean ocrApiKeyConfigured;
    private String ocrModel;
    private String embeddingBaseUrl;
    private Boolean embeddingApiKeyConfigured;
    private String embeddingModel;
    private Integer embeddingDimension;
    private Boolean rerankEnabled;
    private String rerankBaseUrl;
    private Boolean rerankApiKeyConfigured;
    private String rerankModel;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Integer retrievalTopK;
    private BigDecimal scoreThreshold;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
