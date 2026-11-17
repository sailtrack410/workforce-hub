package com.workforcehub.im.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 员工端发送会话消息请求。
 */
@Data
public class SendImMessageRequest {

    private String messageType;

    @Size(max = 4000, message = "消息内容不能超过4000个字符")
    private String text;

    @Size(max = 10, message = "单条消息最多支持10个附件")
    private List<Long> attachmentRefIds = List.of();

    @Size(max = 50, message = "单条消息最多@50个成员")
    private List<Long> mentionUserIds = List.of();

    @Size(max = 128, message = "客户端消息ID不能超过128个字符")
    private String clientMessageId;

    private Long quotedMessageId;
}
