package com.workforcehub.file.controller;

import com.workforcehub.file.dto.DownloadFileResult;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 统一文件下载响应头，避免员工端和后台端下载行为分叉。
 */
// 下载响应工厂：统一 Content-Disposition、Content-Type 处理，支持内联和附件两种模式
@Component
public class FileDownloadResponseFactory {

    public ResponseEntity<Resource> attachment(DownloadFileResult result) {
        return ResponseEntity.ok()
            .contentType(resolveMediaType(result.mimeType()))
            .contentLength(result.fileSize())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(result.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
            .body(result.resource());
    }

    private MediaType resolveMediaType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (IllegalArgumentException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
