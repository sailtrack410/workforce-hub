package com.workforcehub.auth.dto;

import com.workforcehub.common.enums.UserType;
import lombok.Data;

@Data
public class LoginResponse {

    private Long userId;
    private UserType userType;
    private String displayName;
    private String loginName;
}
