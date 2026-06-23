package com.workforcehub.im.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 员工端消息动作响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImMessageActionResponse {

    private Long messageId;
    private String actionType;
    private String status;
}
