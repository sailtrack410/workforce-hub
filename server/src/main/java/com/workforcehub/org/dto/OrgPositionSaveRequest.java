package com.workforcehub.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 岗位新增/编辑请求
 */
@Data
public class OrgPositionSaveRequest {

    @NotBlank(message = "positionCode 不能为空")
    @Size(max = 64, message = "positionCode 长度不能超过 64")
    private String positionCode;

    @NotBlank(message = "positionName 不能为空")
    @Size(max = 128, message = "positionName 长度不能超过 128")
    private String positionName;

    @Size(max = 32, message = "positionCategory 长度不能超过 32")
    private String positionCategory;

    @Size(max = 32, message = "status 长度不能超过 32")
    private String status;
}
