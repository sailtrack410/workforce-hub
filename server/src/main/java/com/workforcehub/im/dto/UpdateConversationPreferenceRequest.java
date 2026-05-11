package com.workforcehub.im.dto;

import lombok.Data;

/**
 * 员工端会话偏好更新请求。
 */
@Data
public class UpdateConversationPreferenceRequest {

    private Boolean pin;
    private Boolean mute;
}
