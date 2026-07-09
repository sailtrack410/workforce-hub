package com.workforcehub.im.dto;

import lombok.Data;

/**
 * 员工端群资料响应。
 */
@Data
public class ImGroupProfileResponse {

    private Long conversationId;
    private String title;
    private Long avatarFileId;
    private String groupType;
    private String groupTypeName;
    private Long ownerUserId;
    private String noticeText;
    private String status;
    private String statusName;
    private String currentMemberRole;
    private String currentMemberRoleName;
}
