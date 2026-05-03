package com.workforcehub.file.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FilePurgeResponse {

    private Long attachmentRefId;
    private LocalDateTime purgedAt;
    private Boolean physicalCleanupScheduled;
}
