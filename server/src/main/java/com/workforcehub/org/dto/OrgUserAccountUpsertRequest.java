package com.workforcehub.org.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 员工登录账号创建/更新请求
 */
@Data
public class OrgUserAccountUpsertRequest {

    @Size(max = 128, message = "loginName 长度不能超过 128")
    private String loginName;

    @Size(min = 8, max = 64, message = "newPassword 长度需在 8 到 64 之间")
    private String newPassword;

    @Email(message = "email 格式不正确")
    @Size(max = 128, message = "email 长度不能超过 128")
    private String email;

    @Size(max = 32, message = "accountStatus 长度不能超过 32")
    private String accountStatus;
}
