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
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
