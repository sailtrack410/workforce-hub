package com.workforcehub.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 员工在职状态
 */
@Getter
public enum EmploymentStatus {

    PENDING_ONBOARD("PENDING_ONBOARD", "待入职"),
    PROBATION("PROBATION", "试用期"),
    ACTIVE("ACTIVE", "在职"),
    PENDING_OFFBOARD("PENDING_OFFBOARD", "待离职"),
    OFFBOARDED("OFFBOARDED", "已离职");

    @EnumValue
    private final String code;
    private final String desc;

    EmploymentStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
