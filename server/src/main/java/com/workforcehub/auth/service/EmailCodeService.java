package com.workforcehub.auth.service;

import com.workforcehub.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 邮箱验证码服务
 *
 * Redis Key 设计：
 * - auth:email:code:{purpose}:{email}     存验证码，TTL 5 分钟
 * - auth:email:send:{purpose}:{email}     防重发标记，TTL 60 秒
 *
 * purpose 取值：BIND（绑定邮箱）/ RESET_PASSWORD（找回密码）
 */
@Slf4j
@Service
public class EmailCodeService {

    private static final String CODE_KEY_PREFIX = "auth:email:code:";
    private static final String SEND_LOCK_KEY_PREFIX = "auth:email:send:";

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RedisTemplate<String, Object> redisTemplate;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@workforce-hub.local}")
    private String mailFrom;

    public EmailCodeService(RedisTemplate<String, Object> redisTemplate,
                            JavaMailSender mailSender) {
        this.redisTemplate = redisTemplate;
        this.mailSender = mailSender;
    }

    /**
     * 发送验证码
     */
    public void sendCode(String email, String purpose) {
        validatePurpose(purpose);

        // 防重发
        String sendLockKey = SEND_LOCK_KEY_PREFIX + purpose + ":" + email;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
            sendLockKey, "1", RESEND_INTERVAL.getSeconds(), TimeUnit.SECONDS
        );
        if (Boolean.FALSE.equals(acquired)) {
            throw new BizException(429, "验证码发送过于频繁，请稍后再试");
        }

        // 生成 6 位数字验证码
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        // 存储验证码
        String codeKey = CODE_KEY_PREFIX + purpose + ":" + email;
        redisTemplate.opsForValue().set(codeKey, code, CODE_TTL.getSeconds(), TimeUnit.SECONDS);

        // 发送邮件
        try {
            sendMail(email, purpose, code);
        } catch (Exception e) {
            // 发送失败要清掉锁，让用户能重试
            redisTemplate.delete(sendLockKey);
            redisTemplate.delete(codeKey);
            log.error("邮件发送失败 email={} purpose={}", email, purpose, e);
            throw new BizException(500, "邮件发送失败，请稍后重试");
        }

        log.info("验证码已发送 email={} purpose={}", email, purpose);
    }

    /**
     * 校验验证码
     * 校验通过后立即删除，防止重放
     */
    public boolean verifyCode(String email, String purpose, String code) {
        validatePurpose(purpose);
        String codeKey = CODE_KEY_PREFIX + purpose + ":" + email;
        Object stored = redisTemplate.opsForValue().get(codeKey);
        if (stored == null) {
            return false;
        }
        if (!stored.toString().equals(code)) {
            return false;
        }
        redisTemplate.delete(codeKey);
        return true;
    }

    private void validatePurpose(String purpose) {
        if (!"BIND".equals(purpose) && !"RESET_PASSWORD".equals(purpose)) {
            throw new BizException(400, "不支持的验证码用途");
        }
    }

    private void sendMail(String email, String purpose, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        if ("BIND".equals(purpose)) {
            message.setSubject("【Workforce Hub】邮箱绑定验证码");
            message.setText("您的邮箱绑定验证码为：" + code + "，5 分钟内有效。如非本人操作请忽略。");
        } else {
            message.setSubject("【Workforce Hub】密码重置验证码");
            message.setText("您的密码重置验证码为：" + code + "，5 分钟内有效。如非本人操作请忽略。");
        }
        mailSender.send(message);
    }
}
