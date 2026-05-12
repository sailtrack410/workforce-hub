package com.workforcehub.auth.dto;

import com.workforcehub.common.enums.UserType;
import lombok.Data;

@Data
public class CurrentUserResponse {

    private Long userId;
    private UserType userType;
    private String displayName;
    private String loginName;
    private String email;

    /**
     * 业务用户的部门ID（仅 USER 类型）
     */
    private Long primaryDeptId;

    /**
     * 业务用户的岗位ID（仅 USER 类型）
     */
    private Long positionId;

    /**
     * 业务用户的直属上级ID（仅 USER 类型）
     */
    private Long managerUserId;

    /**
     * 业务用户的在职状态（仅 USER 类型）
     */
    private String employmentStatus;
}
