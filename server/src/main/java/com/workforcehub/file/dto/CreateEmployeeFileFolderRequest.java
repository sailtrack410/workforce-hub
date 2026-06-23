package com.workforcehub.file.dto;

import lombok.Data;

@Data
public class CreateEmployeeFileFolderRequest {

    private Long parentFolderId;
    private String folderName;
}
