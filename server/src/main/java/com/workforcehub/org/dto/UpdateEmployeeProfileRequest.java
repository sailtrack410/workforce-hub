package com.workforcehub.org.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 员工自助更新个人资料请求
 * 仅允许员工修改可自管字段：手机号、头像。
 */
@Data
public class UpdateEmployeeProfileRequest {

    /**
     * 新手机号，传 null / 空串视为清空，长度不超过 32。
     */
    @Size(max = 32, message = "mobile 长度不能超过 32")
    private String mobile;

    /**
     * 新头像附件引用ID，传 null 视为清空头像。
     */
    private Long avatarAttachmentRefId;
}
