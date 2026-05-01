package com.workforcehub.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 业务申请状态
 */
@Getter
public enum RequestStatus {

    DRAFT("DRAFT", "草稿"),
    PENDING_REVIEW("PENDING_REVIEW", "待审批"),
    IN_APPROVAL("IN_APPROVAL", "审批中"),
    APPROVED("APPROVED", "已通过"),
    REJECTED("REJECTED", "已驳回"),
    WITHDRAWN("WITHDRAWN", "已撤回");

    @EnumValue
    private final String code;
    private final String desc;

    RequestStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
