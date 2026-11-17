package com.workforcehub.org.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 岗位定义
 */
@Data
public class OrgPosition {

    private Long id;

    private String positionCode;
    private String positionName;
    private String positionCategory;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;

    private Integer isDeleted;
}
