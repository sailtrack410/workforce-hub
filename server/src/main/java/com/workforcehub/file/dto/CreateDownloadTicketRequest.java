package com.workforcehub.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateDownloadTicketRequest {

    @NotBlank(message = "下载类型不能为空")
    private String downloadType;

    @Size(max = 256, message = "下载原因不能超过256个字符")
    private String reason;
}
