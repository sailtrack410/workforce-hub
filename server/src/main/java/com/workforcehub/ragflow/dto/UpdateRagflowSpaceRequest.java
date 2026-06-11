package com.workforcehub.ragflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateRagflowSpaceRequest {
    @NotBlank
    @Size(max = 128)
    private String spaceName;

    @Size(max = 512)
    private String description;

    private String visibility;
    private String status;

    @Size(max = 64)
    private String embeddingProvider;

    @Size(max = 128)
    private String embeddingModel;

    @Min(1)
    private Integer embeddingDimension;

    private Boolean rerankEnabled;

    @Size(max = 64)
    private String rerankProvider;

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
