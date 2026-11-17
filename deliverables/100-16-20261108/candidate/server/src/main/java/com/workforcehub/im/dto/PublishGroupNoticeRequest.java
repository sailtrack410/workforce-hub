package com.workforcehub.im.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发布群公告请求（接口需求表 #5）。
 */
@Data
public class PublishGroupNoticeRequest {

    @Schema(description = "公告标题", example = "周报提交提醒")
    @Size(max = 128, message = "公告标题不能超过128字")
    private String title;

    @Schema(description = "公告内容", example = "请在本周五 18:00 前完成周报提交")
    @NotBlank(message = "公告内容不能为空")
    @Size(max = 2048, message = "公告内容不能超过2048字")
    private String content;
}
