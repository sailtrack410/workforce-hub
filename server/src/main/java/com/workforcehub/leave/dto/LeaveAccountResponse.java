package com.workforcehub.leave.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 假期账户余额响应。
 */
@Data
public class LeaveAccountResponse {

    private Long accountId;
    private Long userId;
    private String employeeNo;
    private String realName;
    private Long leaveTypeId;
    private String leaveCode;
    private String leaveName;
    private String balanceMode;
    private String balanceModeName;
    private Integer availableMinutes;
    private Integer frozenMinutes;
    @Schema(description = "年度总额度(分钟,当年GRANT累计)", example = "5760")
    private Integer entitlementMinutes;
    private String status;
    private String statusName;
    private String lastTxnType;
    private String lastTxnTypeName;
    private Integer lastChangeMinutes;
}
