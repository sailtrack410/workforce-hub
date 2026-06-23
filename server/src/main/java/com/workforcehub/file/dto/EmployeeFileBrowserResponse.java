package com.workforcehub.file.dto;

import lombok.Data;

import java.util.List;

@Data
public class EmployeeFileBrowserResponse {

    private Long currentFolderId;
    private List<EmployeeFileFolderResponse> breadcrumbs;
    private List<EmployeeFileFolderResponse> folders;
    private List<EmployeeFileResponse> files;
}
