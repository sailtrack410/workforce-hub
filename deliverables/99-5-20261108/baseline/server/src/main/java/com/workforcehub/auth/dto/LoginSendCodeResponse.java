package com.workforcehub.auth.dto;

import lombok.Data;

/**
 * 员工端登录验证码发送响应。
 * - maskedEmail 非空：验证码已发往该邮箱；
 * - maskedEmail 为空：账号未绑定邮箱，前端应引导用户先登录后再绑定邮箱。
 */
@Data
public class LoginSendCodeResponse {

    private String maskedEmail;
}
