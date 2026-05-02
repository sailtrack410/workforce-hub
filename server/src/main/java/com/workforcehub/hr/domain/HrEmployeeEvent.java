package com.workforcehub.hr.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工生命周期事件
 */
@Data
@TableName("hr_employee_event")
public class HrEmployeeEvent {

    @TableId(type = IdType.ASSIGN_ID)
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
