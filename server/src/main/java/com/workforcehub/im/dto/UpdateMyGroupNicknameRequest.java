package com.workforcehub.im.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 设置本群昵称请求（接口需求表 #3）。
 */
@Data
public class UpdateMyGroupNicknameRequest {

    @Schema(description = "本群昵称(空串=清除)", example = "小李")
    @Size(max = 64, message = "本群昵称不能超过64字")
    private String nickname;
}
