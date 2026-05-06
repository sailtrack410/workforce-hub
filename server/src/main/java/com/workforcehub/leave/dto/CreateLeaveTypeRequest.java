package com.workforcehub.leave.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台创建假种请求。
 */
@Data
public class CreateLeaveTypeRequest {

    @NotBlank(message = "假种编码不能为空")
    @Size(max = 64, message = "假种编码不能超过64个字符")
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "假种编码只能包含大写字母、数字和下划线")
    private String leaveCode;

    @NotBlank(message = "假种名称不能为空")
    @Size(max = 128, message = "假种名称不能超过128个字符")
    private String leaveName;

    @NotBlank(message = "余额模式不能为空")
    @Size(max = 32, message = "余额模式不能超过32个字符")
    private String balanceMode = "DEDUCT";

    @NotBlank(message = "假种状态不能为空")
    @Size(max = 32, message = "假种状态不能超过32个字符")
    private String status = "ACTIVE";
}
