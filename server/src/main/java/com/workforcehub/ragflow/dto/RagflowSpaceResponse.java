package com.workforcehub.ragflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RagflowSpaceResponse {
    private Long spaceId;
    private String spaceName;
    private String spaceCode;
    private String description;
    private String visibility;
    private String visibilityName;
    private Integer documentCount;
    private Integer chunkCount;
    private String embeddingProvider;
    private String embeddingModel;
    private Long embeddingModelId;
    private Integer embeddingDimension;
    private Boolean rerankEnabled;
    private String rerankProvider;
    private String rerankModel;
    private Long rerankModelId;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Integer retrievalTopK;
    private java.math.BigDecimal scoreThreshold;
    private String status;
    private String statusName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
