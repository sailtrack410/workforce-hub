package com.workforcehub.org.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 员工列表查询参数
 */
@Data
public class OrgUserListQuery {

    @Min(value = 1, message = "pageNo 最小为 1")
    private long pageNo = 1;

    @Min(value = 1, message = "pageSize 最小为 1")
    @Max(value = 200, message = "pageSize 最大为 200")
    private long pageSize = 20;

    private String keyword;
    private Long deptId;
    private String employmentStatus;
    private Long managerUserId;
}
