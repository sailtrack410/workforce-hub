package com.workforcehub.im.dto;

import lombok.Data;

import java.util.List;

/**
 * 员工端会话详情响应。
 */
@Data
public class ImConversationDetailResponse {

    private ImConversationSummaryResponse conversation;
    private List<ImConversationMemberResponse> members;
    private ImGroupProfileResponse groupProfile;
}
