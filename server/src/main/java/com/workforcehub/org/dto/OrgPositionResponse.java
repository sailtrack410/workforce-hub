package com.workforcehub.org.dto;

import lombok.Data;

/**
 * 岗位摘要
 */
@Data
public class OrgPositionResponse {

    private Long id;
    private String positionCode;
    private String positionName;
    private String positionCategory;
    private String positionCategoryName;
    private String status;
    private String statusName;
}
