package com.workforcehub.im.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 员工端维护群资料请求。
 */
@Data
public class UpdateGroupConversationRequest {

    @Size(max = 64, message = "群名称不能超过64个字符")
    private String title;

    @Size(max = 1024, message = "群公告不能超过1024个字符")
    private String noticeText;
}
