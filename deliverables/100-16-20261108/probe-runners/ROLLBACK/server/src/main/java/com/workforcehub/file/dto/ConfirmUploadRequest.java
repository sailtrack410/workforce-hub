package com.workforcehub.file.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConfirmUploadRequest {

    @NotNull(message = "上传票据ID不能为空")
    private Long uploadTicketId;

    @NotBlank(message = "对象key不能为空")
    private String objectKey;

    @NotBlank(message = "文件SHA-256不能为空")
    @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "文件SHA-256格式不正确")
    private String contentSha256;

    @NotNull(message = "文件大小不能为空")
    @Min(value = 1, message = "文件大小不能为空")
    private Long fileSize;

    @NotBlank(message = "文件归属类型不能为空")
    private String ownerType;

    private Long ownerId;

    private Long folderId;

    @NotBlank(message = "展示文件名不能为空")
    @Size(max = 256, message = "展示文件名不能超过256个字符")
    private String displayName;
}
