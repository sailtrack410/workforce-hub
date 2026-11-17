package com.workforcehub.personal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个人待办创建/更新请求。
 */
@Data
public class PersonalTodoSaveRequest {

    @NotBlank(message = "标题不能为空")
    private String title;

    private String category;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dueAt;

    private String note;
}
