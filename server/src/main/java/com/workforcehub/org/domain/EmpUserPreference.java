package com.workforcehub.org.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工系统偏好持久化
 * 对应数据库表 emp_user_preference
 */
@Data
@TableName("emp_user_preference")
public class EmpUserPreference {

    @TableId(type = IdType.INPUT)
    private Long userId;

    private String theme;
    private String messageDensity;
    private Integer listWidth;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
