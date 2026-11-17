package com.workforcehub.file.repository;

public record FileObjectRow(
    Long id,
    String storageProvider,
    String bucketName,
    String objectKey,
    Long fileSize,
    String contentSha256
) {}
