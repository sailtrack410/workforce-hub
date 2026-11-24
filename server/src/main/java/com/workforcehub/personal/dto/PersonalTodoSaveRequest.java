package com.workforcehub.personal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个人待办创建/更新请求。
 */
@Data
public class PersonalTodoSaveRequest {
    @Size(max = 64)
    private String clientRequestId;

    private Integer expectedVersion;

    @NotBlank(message = "标题不能为空")
    @Size(max = 128, message = "标题不能超过128个字符")
    private String title;

    private String category;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dueAt;

    private boolean allDay;

    private Integer reminderMinutes;

    @Size(max = 1024, message = "备注不能超过1024个字符")
    private String note;
}
