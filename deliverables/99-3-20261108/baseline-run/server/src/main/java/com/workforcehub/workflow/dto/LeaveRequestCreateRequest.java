package com.workforcehub.workflow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 员工端请假申请提交请求。
 */
@Data
public class LeaveRequestCreateRequest {

    @NotNull(message = "假种不能为空")
    private Long leaveTypeId;

    @NotNull(message = "开始时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;

    @NotNull(message = "结束时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endAt;

    @NotNull(message = "请假时长不能为空")
    @Positive(message = "请假时长必须大于0")
    private Integer durationMinutes;

    @NotBlank(message = "请假理由不能为空")
    @Size(max = 512, message = "请假理由不能超过512个字符")
    private String reason;

    @Size(max = 10, message = "请假附件最多支持10个")
    private List<@Positive(message = "附件引用ID必须大于0") Long> attachmentRefIds = List.of();
}
