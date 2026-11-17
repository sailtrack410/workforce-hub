package com.workforcehub.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台授予 Agent ACL 请求。
 */
@Data
public class GrantAgentAclRequest {

    @NotBlank(message = "授权主体类型不能为空")
    private String subjectType;

    @NotNull(message = "授权主体ID不能为空")
    private Long subjectId;

    @NotBlank(message = "权限编码不能为空")
    @Size(max = 32, message = "权限编码不能超过32个字符")
    private String permCode;

    @Size(max = 32, message = "状态不能超过32个字符")
    private String status;
}
