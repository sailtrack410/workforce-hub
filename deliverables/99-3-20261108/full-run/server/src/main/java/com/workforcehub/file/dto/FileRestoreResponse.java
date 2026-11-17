package com.workforcehub.file.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileRestoreResponse {

    private Long attachmentRefId;
    private LocalDateTime restoredAt;
}
