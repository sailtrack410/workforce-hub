package com.workforcehub.leave.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台假期余额调整请求。
 */
@Data
public class AdjustLeaveAccountRequest {

    @NotNull(message = "员工ID不能为空")
    private Long userId;

    @NotNull(message = "假种ID不能为空")
    private Long leaveTypeId;

    @NotNull(message = "调整分钟数不能为空")
    private Integer changeMinutes;

    @Size(max = 512, message = "调整原因不能超过512个字符")
    private String reason;

    @Size(max = 128, message = "幂等键不能超过128个字符")
    private String sourceTxnKey;
}
