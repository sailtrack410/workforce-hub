package com.workforcehub.im.repository;

import java.time.LocalDateTime;

public record ImConversationMemberRow(
    Long memberId,
    String memberType,
    String memberRole,
    String status,
    LocalDateTime joinedAt
) {
}
