package com.workforcehub.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 部门新增/编辑请求
 */
@Data
public class OrgDepartmentSaveRequest {

    private Long parentId;

    @NotBlank(message = "deptCode 不能为空")
    @Size(max = 64, message = "deptCode 长度不能超过 64")
    private String deptCode;

    @NotBlank(message = "deptName 不能为空")
    @Size(max = 128, message = "deptName 长度不能超过 128")
    private String deptName;

    @NotBlank(message = "deptType 不能为空")
    @Size(max = 32, message = "deptType 长度不能超过 32")
    private String deptType;

    private Long leaderUserId;

    @Size(max = 32, message = "status 长度不能超过 32")
    private String status;
}
