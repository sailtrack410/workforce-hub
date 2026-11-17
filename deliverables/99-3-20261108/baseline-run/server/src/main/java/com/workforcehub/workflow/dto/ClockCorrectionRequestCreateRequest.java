package com.workforcehub.workflow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 员工端补卡申请提交请求。
 */
@Data
public class ClockCorrectionRequestCreateRequest {

    @NotNull(message = "补卡日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate targetDate;

    @NotBlank(message = "缺卡类型不能为空")
    private String missingPunchType;

    @NotNull(message = "补卡时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expectedPunchAt;

    @NotBlank(message = "补卡原因不能为空")
    @Size(max = 512, message = "补卡原因不能超过512个字符")
    private String reason;

    @Size(max = 10, message = "补卡附件最多支持10个")
    private List<Long> attachmentRefIds = List.of();
}
