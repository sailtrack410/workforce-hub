package com.workforcehub.file.dto;

import lombok.Data;

@Data
public class CopyEmployeeFileRequest {

    private Long targetFolderId;
    private String displayName;
}
