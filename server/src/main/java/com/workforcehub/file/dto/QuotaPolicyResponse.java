package com.workforcehub.file.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class QuotaPolicyResponse {

    private Long policyId;
    private String policyCode;
    private String policyName;
    private String scopeType;
    private Long scopeId;
    private Long quotaBytes;
    private Long cacheQuotaBytes;
    private BigDecimal warningThreshold;
    private BigDecimal blockThreshold;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
