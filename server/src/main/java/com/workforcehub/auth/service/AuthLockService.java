package com.workforcehub.auth.service;

import com.workforcehub.common.enums.UserType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 登录失败频控
 * Key 格式: auth:login:fail:{userType}:{loginName}
 *
 * 策略：
 * - 每次失败计数 +1
 * - 失败 5 次后锁定 30 分钟
 * - 登录成功立即清零
 */
@Service
public class AuthLockService {

    private static final String KEY_PREFIX = "auth:login:fail:";
    private static final int MAX_FAIL_COUNT = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    private final RedisTemplate<String, Object> redisTemplate;

    public AuthLockService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 检查是否已被锁定
     */
    public boolean isLocked(UserType userType, String loginName) {
        Integer count = (Integer) redisTemplate.opsForValue().get(buildKey(userType, loginName));
        return count != null && count >= MAX_FAIL_COUNT;
    }

    /**
     * 登录失败，计数 +1
     * @return 当前累计失败次数
     */
    public int recordFailure(UserType userType, String loginName) {
        String key = buildKey(userType, loginName);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            // 第一次失败时设置 TTL
            redisTemplate.expire(key, LOCK_DURATION.getSeconds(), TimeUnit.SECONDS);
        }
        return count != null ? count.intValue() : 0;
    }

    /**
     * 登录成功，清零失败计数
     */
    public void clearFailures(UserType userType, String loginName) {
        redisTemplate.delete(buildKey(userType, loginName));
    }

    /**
     * 获取剩余可尝试次数
     */
    public int getRemainingAttempts(UserType userType, String loginName) {
        Integer count = (Integer) redisTemplate.opsForValue().get(buildKey(userType, loginName));
        if (count == null) {
            return MAX_FAIL_COUNT;
        }
        return Math.max(0, MAX_FAIL_COUNT - count);
    }

    private String buildKey(UserType userType, String loginName) {
        return KEY_PREFIX + userType.name() + ":" + loginName;
    }
}
