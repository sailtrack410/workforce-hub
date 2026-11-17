package com.workforcehub.im.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群公告响应（接口需求表 #5）。
 */
@Data
public class ImGroupNoticeResponse {

    @Schema(description = "公告ID", example = "2054016024106426370")
    private String noticeId;

    @Schema(description = "公告标题", example = "周报提交提醒")
    private String title;

    @Schema(description = "公告内容", example = "请在本周五 18:00 前完成周报提交")
    private String content;

    @Schema(description = "发布人", example = "张伟")
    private String publisherName;

    @Schema(description = "发布时间", example = "2026-10-10T18:20:00")
    private LocalDateTime publishedAt;
}
