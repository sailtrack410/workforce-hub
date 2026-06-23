package com.workforcehub.im.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工端消息提醒请求。
 */
@Data
public class RemindImMessageRequest {

    private LocalDateTime remindAt;

    private String note;
}
