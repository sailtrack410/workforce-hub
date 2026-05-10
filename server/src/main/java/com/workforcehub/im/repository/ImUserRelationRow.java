package com.workforcehub.im.repository;

/**
 * IM 私聊准入所需的组织用户关系快照。
 */
public record ImUserRelationRow(
    Long userId,
    String realName,
    Long managerUserId,
    String employmentStatus,
    String status
) {
}
