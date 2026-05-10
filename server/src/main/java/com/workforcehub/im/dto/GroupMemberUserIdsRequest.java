package com.workforcehub.im.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 员工端群成员批量治理请求。
 */
@Data
public class GroupMemberUserIdsRequest {

    @NotEmpty(message = "群成员不能为空")
    @Size(max = 200, message = "群成员不能超过200人")
    private List<Long> memberUserIds;
}
