package com.workforcehub.leave.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 假种响应。
 */
@Data
public class LeaveTypeResponse {

    private Long leaveTypeId;
    private String leaveCode;
    private String leaveName;
    private String balanceMode;
    private String balanceModeName;
    private String status;
    private String statusName;
    /** 当前员工该假种已获批的请假总时长（分钟），仅员工端接口返回 */
    private Integer usedMinutes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
