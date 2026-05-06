package com.workforcehub.leave.dto;

import lombok.Data;

/**
 * 假期账户余额响应。
 */
@Data
public class LeaveAccountResponse {

    private Long accountId;
    private Long userId;
    private Long leaveTypeId;
    private String leaveCode;
    private String leaveName;
    private String balanceMode;
    private Integer availableMinutes;
    private Integer frozenMinutes;
    private String status;
    private String lastTxnType;
    private Integer lastChangeMinutes;
}
