package com.workforcehub.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 知识入库状态
 */
@Getter
public enum IngestStatus {

    NOT_INGESTED("NOT_INGESTED", "未入库"),
    PENDING("PENDING", "待入库"),
    PROCESSING("PROCESSING", "入库中"),
    READY("READY", "已可用"),
    FAILED("FAILED", "入库失败"),
    OFFLINE("OFFLINE", "已下线");

    @EnumValue
    private final String code;
    private final String desc;

    IngestStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
