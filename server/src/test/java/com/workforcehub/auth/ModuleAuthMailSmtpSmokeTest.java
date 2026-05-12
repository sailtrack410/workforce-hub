package com.workforcehub.auth;

import com.workforcehub.auth.service.EmailCodeService;
import com.workforcehub.support.LocalIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 SMTP 发信冒烟测试。
 *
 * 默认跳过，避免日常回归误发真实邮件。需要真实联调时显式设置：
 * MAIL_SMOKE_ENABLED=true
 */
@EnabledIfEnvironmentVariable(named = "MAIL_SMOKE_ENABLED", matches = "true")
@SpringBootTest
@ActiveProfiles("api")
class ModuleAuthMailSmtpSmokeTest extends LocalIntegrationTestSupport {

    @Autowired
    private EmailCodeService emailCodeService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    @DisplayName("真实 SMTP 应能发送绑定邮箱验证码")
    void realSmtpShouldSendBindEmailCode() {
        assertRequiredEnv("MAIL_HOST");
        assertRequiredEnv("MAIL_PORT");
        assertRequiredEnv("MAIL_USERNAME");
        assertRequiredEnv("MAIL_PASSWORD");

        String email = env("MAIL_SMOKE_TO", env("MAIL_USERNAME", ""));
        assertTrue(!email.isBlank(), "MAIL_SMOKE_TO 或 MAIL_USERNAME 必须至少配置一个收件邮箱");

        emailCodeService.sendCode(email, "BIND");

        Object code = redisTemplate.opsForValue().get(emailCodeService.buildCodeKey(email, "BIND"));
        assertNotNull(code, "真实 SMTP 发送成功后仍应把验证码写入 Redis，供后续绑定/重置流程校验");
    }

    private static void assertRequiredEnv(String key) {
        assertTrue(!env(key, "").isBlank(), key + " 未配置，无法执行真实 SMTP 冒烟测试");
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
