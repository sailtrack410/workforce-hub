package com.workforcehub.ragflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RagflowDocumentResponse {
    private Long documentId;
    private Long spaceId;
    private Long attachmentRefId;
    private Long sourceFileObjectId;
    private String title;
    private String mimeType;
    private Long fileSize;
    private String contentHash;
    private String parserType;
    private String parserTypeName;
    private String ingestStatus;
    private String ingestStatusName;
    private Long latestIngestJobId;
    private String ingestJobStatus;
    private String ingestJobStatusName;
    private String vectorStatus;
    private String vectorStatusName;
    private Integer chunkCount;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
