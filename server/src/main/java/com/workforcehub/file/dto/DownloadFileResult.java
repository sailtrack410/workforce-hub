package com.workforcehub.file.dto;

import org.springframework.core.io.Resource;

public record DownloadFileResult(
    Resource resource,
    String fileName,
    String mimeType,
    Long fileSize
) {}
