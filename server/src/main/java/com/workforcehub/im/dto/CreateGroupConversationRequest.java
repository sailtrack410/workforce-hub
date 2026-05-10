package com.workforcehub.im.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 员工端创建自定义群聊请求。
 */
@Data
public class CreateGroupConversationRequest {

    @NotBlank(message = "群名称不能为空")
    @Size(max = 64, message = "群名称不能超过64个字符")
    private String title;

    @NotEmpty(message = "群成员不能为空")
    @Size(max = 200, message = "群成员不能超过200人")
    private List<Long> memberUserIds;
}
