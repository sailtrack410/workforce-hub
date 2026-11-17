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
    private String scopeTypeName;
    private Long scopeId;
    private String scopeName;
    private Long quotaBytes;
    private Long cacheQuotaBytes;
    private BigDecimal warningThreshold;
    private BigDecimal blockThreshold;
    private String status;
    private String statusName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
