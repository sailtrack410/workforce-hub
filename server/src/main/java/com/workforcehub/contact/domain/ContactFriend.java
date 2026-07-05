package com.workforcehub.contact.domain;

import lombok.Data;

/**
 * 员工好友关系
 */
@Data
public class ContactFriend {

    private Long id;
    private Long userId;
    private Long friendUserId;
    private String remark;
    private String status;
    private Long createdBy;
    private Long updatedBy;
}
