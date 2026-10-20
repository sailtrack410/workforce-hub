package com.workforcehub.auth.service;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.auth.repository.AuthRegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;

/**
 * 员工自助注册与短信验证码（接口需求表 #12）。
 * 短信通道未接入：验证码写日志返回（演示模式），生产接入短信网关后替换 sendSms。
 */
@Service
public class EmployeeRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeRegistrationService.class);
    private static final String SCENE_REGISTER = "REGISTER";
    private static final int CODE_TTL_MINUTES = 10;
    private static final int CODE_LENGTH = 6;

    private final AuthRegistrationRepository registrationRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmployeeRegistrationService(AuthRegistrationRepository registrationRepository) {
        this.registrationRepository = registrationRepository;
    }

    /** 发送注册短信验证码 */
    public Map<String, Object> sendRegisterSmsCode(String phone) {
        ensurePhoneFormat(phone);
        if (registrationRepository.existsPhoneUser(phone)) {
            throw new BizException(409, "该手机号已注册");
        }
        String code = String.format("%0" + CODE_LENGTH + "d", secureRandom.nextInt((int) Math.pow(10, CODE_LENGTH)));
        registrationRepository.insertSmsCode(IdGenerator.nextId(), phone, SCENE_REGISTER,
            sha256(code), LocalDateTime.now().plusMinutes(CODE_TTL_MINUTES));
        String masked = sendSms(phone, code);
        return Map.of("maskedPhone", masked);
    }

    /** 员工自助注册：核销验证码与邀请码，建员工档案与登录账号，成功后用手机号+密码登录 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> register(String realName, String phone, String smsCode,
                                        String password, String inviteCode) {
        ensurePhoneFormat(phone);
        if (realName == null || realName.isBlank()) {
            throw new BizException(400, "姓名不能为空");
        }
        if (password == null || password.length() < 8 || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new BizException(400, "密码需8位以上且包含字母和数字");
        }
        if (registrationRepository.existsPhoneUser(phone)) {
            throw new BizException(409, "该手机号已注册");
        }
        String expected = registrationRepository.findLatestUsableCodeHash(phone, SCENE_REGISTER)
            .orElseThrow(() -> new BizException(400, "验证码错误或已过期"));
        if (!expected.equals(sha256(smsCode))) {
            throw new BizException(400, "验证码错误或已过期");
        }
        Long deptId = registrationRepository.findUsableInviteDept(inviteCode)
            .orElseThrow(() -> new BizException(400, "邀请码无效或已使用"));

        Long userId = IdGenerator.nextId();
        String employeeNo = "EMP_" + IdGenerator.nextIdString();
        registrationRepository.insertOrgUser(userId, employeeNo, realName.trim(), phone, deptId);
        registrationRepository.insertUserAccount(IdGenerator.nextId(), userId, phone,
            new BCryptPasswordEncoder().encode(password));
        registrationRepository.markSmsCodeUsed(phone, SCENE_REGISTER);
        registrationRepository.markInviteUsed(inviteCode, userId);
        return Map.of("userId", String.valueOf(userId), "loginName", phone);
    }

    private void ensurePhoneFormat(String phone) {
        if (phone == null || !phone.matches("1\\d{10}")) {
            throw new BizException(400, "手机号格式不正确");
        }
    }

    /** 演示模式短信：日志输出并返回掩码号码；生产替换为短信网关调用 */
    private String sendSms(String phone, String code) {
        log.info("[SMS-DEMO] phone={} code={}", phone, code);
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
