package com.workforcehub.im.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 员工端会话偏好更新请求。
 */
@Data
public class UpdateConversationPreferenceRequest {

    private Boolean pin;
    private Boolean mute;
    @Schema(description = "保存到通讯录", example = "false")
    private Boolean saveToContacts;
    @Schema(description = "显示成员昵称", example = "true")
    private Boolean showMemberNickname;
}
