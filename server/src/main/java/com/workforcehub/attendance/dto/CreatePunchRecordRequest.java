package com.workforcehub.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreatePunchRecordRequest {

    private LocalDateTime punchAt;

    @NotBlank(message = "打卡类型不能为空")
    private String punchType;

    @NotBlank(message = "打卡来源不能为空")
    private String sourceType;

    @Size(max = 256, message = "位置描述不能超过256个字符")
    private String locationText;

    @Size(max = 64, message = "IP地址不能超过64个字符")
    private String ipAddress;

    @Size(max = 128, message = "设备ID不能超过128个字符")
    private String deviceId;
}
