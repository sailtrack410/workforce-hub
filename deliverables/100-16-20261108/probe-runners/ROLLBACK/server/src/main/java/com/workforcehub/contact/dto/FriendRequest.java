package com.workforcehub.contact.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 添加/更新好友请求
 */
@Data
public class FriendRequest {

    @Size(max = 60)
    private String remark;
}
