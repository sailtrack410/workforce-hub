package com.workforcehub.hr.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工生命周期事件
 */
@Data
public class HrEmployeeEvent {

    private Long id;

    private Long userId;
    private String changeType;
    private Long requestId;
    private String beforeSnapshotJson;
    private String afterSnapshotJson;
    private LocalDateTime effectiveAt;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
