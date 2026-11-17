package com.workforcehub.im.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 员工端消息动作响应。
 */
@Data
@NoArgsConstructor
public class ImMessageActionResponse {

    private Long messageId;
    private String actionType;
    private String actionTypeName;
    private String status;
    private String statusName;

    public ImMessageActionResponse(Long messageId, String actionType, String actionTypeName, String status, String statusName) {
        this.messageId = messageId;
        this.actionType = actionType;
        this.actionTypeName = actionTypeName;
        this.status = status;
        this.statusName = statusName;
    }
}
