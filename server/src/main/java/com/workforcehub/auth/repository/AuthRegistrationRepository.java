package com.workforcehub.auth.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 注册短信验证码与邀请码仓库（接口需求表 #12，表 auth_sms_code / auth_invite_code）。
 */
@Repository
public class AuthRegistrationRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuthRegistrationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 写入短信验证码(code 已哈希) */
    public void insertSmsCode(Long id, String phone, String scene, String codeHash, LocalDateTime expiredAt) {
        jdbcTemplate.update("""
            INSERT INTO auth_sms_code (id, phone, scene, code_hash, expired_at)
            VALUES (?, ?, ?, ?, ?)
            """, id, phone, scene, codeHash, expiredAt);
    }

    /** 取最新未核销且未过期的验证码哈希 */
    public Optional<String> findLatestUsableCodeHash(String phone, String scene) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT code_hash FROM auth_sms_code
            WHERE phone = ? AND scene = ? AND used_at IS NULL AND expired_at > NOW()
            ORDER BY created_at DESC
            LIMIT 1
            """, phone, scene);
        return rows.isEmpty() ? Optional.empty() : Optional.of((String) rows.get(0).get("code_hash"));
    }

    /** 核销最新验证码 */
    public void markSmsCodeUsed(String phone, String scene) {
        jdbcTemplate.update("""
            UPDATE auth_sms_code SET used_at = NOW()
            WHERE id = (
                SELECT id FROM (
                    SELECT id FROM auth_sms_code
                    WHERE phone = ? AND scene = ? AND used_at IS NULL AND expired_at > NOW()
                    ORDER BY created_at DESC LIMIT 1
                ) latest
            )
            """, phone, scene);
    }

    /** 手机号是否已注册(org_user.mobile + auth_user_account.login_name 双查) */
    public boolean existsPhoneUser(String phone) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM org_user u
            JOIN auth_user_account a ON a.user_id = u.id AND a.is_deleted = 0
            WHERE u.mobile = ? AND u.is_deleted = 0
            """, Integer.class, phone);
        return count != null && count > 0;
    }

    /** 邀请码取部门：有效=存在、未核销、未过期；返回 primary_dept_id */
    public Optional<Long> findUsableInviteDept(String inviteCode) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, primary_dept_id FROM auth_invite_code
            WHERE invite_code = ? AND used_by IS NULL
              AND (expired_at IS NULL OR expired_at > NOW())
            LIMIT 1
            """, inviteCode);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(((Number) rows.get(0).get("primary_dept_id")).longValue());
    }

    /** 核销邀请码 */
    public void markInviteUsed(String inviteCode, Long userId) {
        jdbcTemplate.update("""
            UPDATE auth_invite_code SET used_by = ?, used_at = NOW()
            WHERE invite_code = ? AND used_by IS NULL
            """, userId, inviteCode);
    }

    /** 创建员工档案(注册最小集：试用期, 部门来自邀请码) */
    public void insertOrgUser(Long id, String employeeNo, String realName, String phone, Long deptId) {
        jdbcTemplate.update("""
            INSERT INTO org_user (id, employee_no, real_name, mobile, primary_dept_id, employment_status, hire_date)
            VALUES (?, ?, ?, ?, ?, 'PROBATION', CURDATE())
            """, id, employeeNo, realName, phone, deptId);
    }

    /** 创建登录账号(login_name=手机号) */
    public void insertUserAccount(Long id, Long userId, String loginName, String passwordHash) {
        jdbcTemplate.update("""
            INSERT INTO auth_user_account (id, user_id, login_name, password_hash, account_status)
            VALUES (?, ?, ?, ?, 'ACTIVE')
            """, id, userId, loginName, passwordHash);
    }
}
