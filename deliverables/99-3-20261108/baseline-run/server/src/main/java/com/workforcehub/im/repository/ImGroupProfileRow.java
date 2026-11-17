package com.workforcehub.im.repository;

public record ImGroupProfileRow(
    Long conversationId,
    String title,
    Long avatarFileId,
    String groupType,
    Long ownerUserId,
    String noticeText,
    String status
) {
}
