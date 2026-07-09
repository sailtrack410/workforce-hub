package com.workforcehub.im.dto;

import lombok.Data;

/**
 * 员工端会话消息 @ 成员响应。
 */
@Data
public class ImMessageMentionResponse {

    private Long userId;
    private String name;
}
