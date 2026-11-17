package com.workforcehub.file.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUploadTicketRequest {

    @NotBlank(message = "文件归属类型不能为空")
    private String ownerType;

    private Long ownerId;

    private Long folderId;

    @NotBlank(message = "文件名不能为空")
    @Size(max = 256, message = "文件名不能超过256个字符")
    private String fileName;

    @NotBlank(message = "MIME类型不能为空")
    @Size(max = 128, message = "MIME类型不能超过128个字符")
    private String mimeType;

    @NotNull(message = "文件大小不能为空")
    @Min(value = 1, message = "文件大小不能为空")
    private Long fileSize;

    @NotBlank(message = "文件SHA-256不能为空")
    @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "文件SHA-256格式不正确")
    private String contentSha256;

    @Pattern(regexp = "^$|^[a-fA-F0-9]{32}$", message = "文件MD5格式不正确")
    private String contentMd5;

    @Size(max = 128, message = "客户端请求ID不能超过128个字符")
    private String clientRequestId;
}
