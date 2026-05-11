package com.workforcehub.im.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 员工端编辑会话消息请求。
 */
@Data
public class EditImMessageRequest {

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "消息内容不能超过4000个字符")
    private String text;
}
