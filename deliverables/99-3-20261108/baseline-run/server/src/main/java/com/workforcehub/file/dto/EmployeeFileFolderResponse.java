package com.workforcehub.file.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EmployeeFileFolderResponse {

    private Long folderId;
    private Long parentFolderId;
    private String folderName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
