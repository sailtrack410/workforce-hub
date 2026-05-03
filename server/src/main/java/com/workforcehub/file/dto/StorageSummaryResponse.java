package com.workforcehub.file.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StorageSummaryResponse {

    private Long orgPoolQuotaBytes;
    private Long orgPoolUsedBytes;
    private BigDecimal orgPoolWarningThreshold;
    private BigDecimal orgPoolBlockThreshold;
    private Long cacheQuotaBytes;
    private Long cacheUsedBytes;
    private Long backendFreeBytes;
    private Boolean backendWritable;
    private String writeProtectReason;
    private Long fileObjectCount;
    private Long attachmentRefCount;
    private Long recycleBinCount;
    private Long pendingCleanupObjectCount;
}
