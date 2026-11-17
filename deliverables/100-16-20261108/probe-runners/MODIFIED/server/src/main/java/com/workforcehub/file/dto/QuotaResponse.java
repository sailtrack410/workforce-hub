package com.workforcehub.file.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class QuotaResponse {

    private Long personalQuotaBytes;
    private Long personalUsedBytes;
    private BigDecimal personalUsagePercent;
    private Long orgPoolQuotaBytes;
    private Long orgPoolUsedBytes;
    private Long cacheQuotaBytes;
    private Long cacheUsedBytes;
    private Boolean uploadWritable;
    private String blockReason;
}
