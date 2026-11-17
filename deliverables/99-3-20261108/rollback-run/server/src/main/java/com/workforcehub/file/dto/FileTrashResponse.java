package com.workforcehub.file.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileTrashResponse {

    private Long attachmentRefId;
    private LocalDateTime deletedAt;
    private LocalDateTime purgeAfter;
}
