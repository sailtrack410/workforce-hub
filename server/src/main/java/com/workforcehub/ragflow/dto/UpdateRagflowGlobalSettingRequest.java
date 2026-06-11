package com.workforcehub.ragflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateRagflowGlobalSettingRequest {
    @Size(max = 512)
    private String ocrBaseUrl;

    @Size(max = 1024)
    private String ocrApiKey;

    @Size(max = 128)
    private String ocrModel;

    @Size(max = 512)
    private String embeddingBaseUrl;

    @Size(max = 1024)
    private String embeddingApiKey;

    @Size(max = 128)
    private String embeddingModel;

    @Min(1)
    private Integer embeddingDimension;

    private Boolean rerankEnabled;

    @Size(max = 512)
    private String rerankBaseUrl;

    @Size(max = 1024)
    private String rerankApiKey;

    @Size(max = 128)
    private String rerankModel;

    @Min(100)
    @Max(8000)
    private Integer chunkSize;

    @Min(0)
    @Max(2000)
    private Integer chunkOverlap;

    @Min(1)
    @Max(50)
    private Integer retrievalTopK;

    private BigDecimal scoreThreshold;
}
