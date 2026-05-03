package com.workforcehub.file.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DownloadTicketResponse {

    private Long downloadTicketId;
    private String ticketNo;
    private String downloadUrl;
    private LocalDateTime expireAt;
    private String fileName;
    private Long fileSize;
}
