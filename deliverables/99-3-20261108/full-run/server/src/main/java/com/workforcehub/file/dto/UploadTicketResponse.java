package com.workforcehub.file.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class UploadTicketResponse {

    private Long uploadTicketId;
    private String ticketNo;
    private Boolean deduplicated;
    private Long attachmentRefId;
    private String storageProvider;
    private String bucketName;
    private String objectKey;
    private String uploadUrl;
    private Map<String, String> headers;
    private LocalDateTime expireAt;
}
