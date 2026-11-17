package com.workforcehub.im.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 员工端更新会话已读位点请求。
 */
@Data
public class UpdateReadPositionRequest {

    @NotNull(message = "已读序号不能为空")
    @Min(value = 0, message = "已读序号不能小于0")
    private Long lastReadSeq;
}
