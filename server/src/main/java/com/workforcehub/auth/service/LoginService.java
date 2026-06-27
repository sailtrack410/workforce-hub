package com.workforcehub.auth.service;

import com.workforcehub.auth.domain.AuthUserAccount;
import com.workforcehub.auth.dto.LoginRequest;
import com.workforcehub.auth.dto.LoginResponse;
import com.workforcehub.auth.dto.LoginSendCodeResponse;
import com.workforcehub.auth.mapper.AuthUserAccountMapper;
import com.workforcehub.auth.mapper.AdminUserMapper;
import com.workforcehub.auth.security.EmploymentLoginBlockedException;
import com.workforcehub.auth.security.WfhAuthenticationProvider;
import com.workforcehub.auth.security.WfhUserDetails;
import com.workforcehub.common.enums.UserType;
import com.workforcehub.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 登录服务
 * 负责：
 * - 调用对应 UserType 的 Provider 完成认证
 * - 失败计数 / 锁定校验
 * - 写入 SecurityContext 与 Session
 * - 更新 last_login_at
 * - 员工端凭证通过后做邮箱二次验证（已绑定邮箱校验 LOGIN 验证码；未绑定邮箱先完成 BIND 验证）
 */
@Slf4j
@Service
public class LoginService {

    private static final String LOGIN_CODE_PURPOSE = "LOGIN";
    private static final String BIND_CODE_PURPOSE = "BIND";

    private final WfhAuthenticationProvider employeeAuthProvider;
    private final WfhAuthenticationProvider adminAuthProvider;
    private final AuthLockService authLockService;
    private final AuthUserAccountMapper accountMapper;
    private final AdminUserMapper adminUserMapper;
    private final EmailCodeService emailCodeService;
    private final boolean emailVerifyEnabled;

    public LoginService(@Qualifier("employeeAuthProvider") WfhAuthenticationProvider employeeAuthProvider,
                        @Qualifier("adminAuthProvider") WfhAuthenticationProvider adminAuthProvider,
                        AuthLockService authLockService,
                        AuthUserAccountMapper accountMapper,
                        AdminUserMapper adminUserMapper,
                        EmailCodeService emailCodeService,
                        @Value("${workforce.auth.email-verify.enabled:true}") boolean emailVerifyEnabled) {
        this.employeeAuthProvider = employeeAuthProvider;
        this.adminAuthProvider = adminAuthProvider;
        this.authLockService = authLockService;
        this.accountMapper = accountMapper;
        this.adminUserMapper = adminUserMapper;
        this.emailCodeService = emailCodeService;
        this.emailVerifyEnabled = emailVerifyEnabled;
    }

    /**
     * 员工端登录
     */
    public LoginResponse loginEmployee(LoginRequest request, HttpServletRequest httpRequest) {
        return doLogin(UserType.USER, request, httpRequest, employeeAuthProvider);
    }

    /**
     * 后台登录
     */
    public LoginResponse loginAdmin(LoginRequest request, HttpServletRequest httpRequest) {
        return doLogin(UserType.ADMIN, request, httpRequest, adminAuthProvider);
    }

    /**
     * 员工端：根据登录名 / 邮箱发送登录验证码。
     * 找不到账号或未绑定邮箱时返回 maskedEmail=null，前端据此引导后续流程。
     */
    public LoginSendCodeResponse sendLoginCode(String identifier) {
        LoginSendCodeResponse response = new LoginSendCodeResponse();
        if (!emailVerifyEnabled) {
            return response;
        }
        if (identifier == null || identifier.isBlank()) {
            return response;
        }
        AuthUserAccount account = lookupAccount(identifier.trim());
        if (account == null || account.getEmail() == null || account.getEmail().isBlank()) {
            return response;
        }
        emailCodeService.sendCode(account.getEmail(), LOGIN_CODE_PURPOSE);
        response.setMaskedEmail(maskEmail(account.getEmail()));
        return response;
    }

    private LoginResponse doLogin(UserType userType, LoginRequest request,
                                  HttpServletRequest httpRequest,
                                  WfhAuthenticationProvider provider) {
        String loginName = request.getLoginName();

        // 1. 频控校验
        if (authLockService.isLocked(userType, loginName)) {
            throw new BizException(429, "登录失败次数过多，账号已锁定 30 分钟");
        }

        // 2. 调用 Provider 认证
        Authentication authentication;
        try {
            authentication = provider.authenticate(
                new UsernamePasswordAuthenticationToken(loginName, request.getPassword())
            );
        } catch (BadCredentialsException | UsernameNotFoundException e) {
            int failCount = authLockService.recordFailure(userType, loginName);
            int remaining = authLockService.getRemainingAttempts(userType, loginName);
            log.warn("登录失败 userType={} loginName={} failCount={} remaining={}",
                userType, loginName, failCount, remaining);
            throw new BizException(401, "用户名或密码错误，剩余尝试次数 " + remaining);
        } catch (InternalAuthenticationServiceException e) {
            if (e.getCause() instanceof EmploymentLoginBlockedException blocked) {
                log.warn("员工生命周期状态不允许登录 userType={} loginName={} message={}",
                    userType, loginName, blocked.getMessage());
                throw new BizException(403, blocked.getMessage());
            }
            throw e;
        } catch (EmploymentLoginBlockedException e) {
            log.warn("员工生命周期状态不允许登录 userType={} loginName={} message={}",
                userType, loginName, e.getMessage());
            throw new BizException(403, e.getMessage());
        } catch (LockedException e) {
            // 账号在数据库层面已被标记为锁定
            log.warn("账号已锁定 userType={} loginName={}", userType, loginName);
            throw new BizException(423, "账号已锁定，请联系管理员");
        } catch (DisabledException e) {
            log.warn("账号已禁用 userType={} loginName={}", userType, loginName);
            throw new BizException(403, "账号已被禁用，请联系管理员");
        } catch (AccountExpiredException e) {
            log.warn("账号已过期 userType={} loginName={}", userType, loginName);
            throw new BizException(403, "账号已过期，请联系管理员");
        } catch (CredentialsExpiredException e) {
            log.warn("凭证已过期 userType={} loginName={}", userType, loginName);
            throw new BizException(403, "登录凭证已过期，请重置密码");
        }

        WfhUserDetails user = (WfhUserDetails) authentication.getPrincipal();

        // 3. 员工端：邮箱二次验证分支（受 workforce.auth.email-verify.enabled 开关控制）
        if (emailVerifyEnabled && userType == UserType.USER) {
            AuthUserAccount account = accountMapper.selectByUserId(user.getUserId());
            String boundEmail = account != null ? account.getEmail() : null;
            boolean hasEmail = boundEmail != null && !boundEmail.isBlank();
            if (hasEmail) {
                if (request.getEmailCode() == null || request.getEmailCode().isBlank()) {
                    // 凭证 OK 但未提供验证码，引导前端进入二验步骤；不签发会话。
                    LoginResponse pending = new LoginResponse();
                    pending.setUserId(user.getUserId());
                    pending.setUserType(userType);
                    pending.setDisplayName(user.getDisplayName());
                    pending.setLoginName(user.getUsername());
                    pending.setRequiresEmailCode(true);
                    pending.setMaskedEmail(maskEmail(boundEmail));
                    log.info("登录二验待提交 userType={} userId={} loginName={}",
                        userType, user.getUserId(), loginName);
                    return pending;
                }
                if (!emailCodeService.verifyCode(boundEmail, LOGIN_CODE_PURPOSE, request.getEmailCode().trim())) {
                    log.warn("登录二验失败 userType={} userId={} loginName={}",
                        userType, user.getUserId(), loginName);
                    throw new BizException(401, "邮箱验证码错误或已过期");
                }
            } else {
                String bindEmail = request.getBindEmail() == null ? null : request.getBindEmail().trim();
                String bindCode = request.getEmailCode() == null ? null : request.getEmailCode().trim();
                if (bindEmail == null || bindEmail.isBlank() || bindCode == null || bindCode.isBlank()) {
                    LoginResponse pending = new LoginResponse();
                    pending.setUserId(user.getUserId());
                    pending.setUserType(userType);
                    pending.setDisplayName(user.getDisplayName());
                    pending.setLoginName(user.getUsername());
                    pending.setRequiresEmailBinding(true);
                    log.info("登录前需绑定邮箱 userType={} userId={} loginName={}",
                        userType, user.getUserId(), loginName);
                    return pending;
                }
                bindEmailForLogin(account, user.getUserId(), bindEmail, bindCode);
            }
        }

        // 4. Session 轮换：先废弃旧 Session，再创建新 Session 写入认证上下文
        //    防止 session fixation 攻击（攻击者预设 JSESSIONID，受害者登录后被劫持）
        HttpSession oldSession = httpRequest.getSession(false);
        if (oldSession != null) {
            // 提取需要保留的属性（如自定义业务字段），第一版无需保留任何属性
            oldSession.invalidate();
        }
        HttpSession newSession = httpRequest.getSession(true);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        newSession.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        // 5. 清除失败计数
        authLockService.clearFailures(userType, loginName);

        // 6. 更新最后登录时间
        updateLastLogin(userType, user.getUserId());

        log.info("登录成功 userType={} userId={} loginName={} sessionId={}",
            userType, user.getUserId(), loginName, newSession.getId());

        // 7. 构造响应
        LoginResponse response = new LoginResponse();
        response.setUserId(user.getUserId());
        response.setUserType(userType);
        response.setDisplayName(user.getDisplayName());
        response.setLoginName(user.getUsername());
        return response;
    }

    private void bindEmailForLogin(AuthUserAccount account, Long userId, String email, String code) {
        if (!emailCodeService.verifyCode(email, BIND_CODE_PURPOSE, code)) {
            throw new BizException(400, "验证码错误或已过期");
        }
        AuthUserAccount existing = accountMapper.selectByEmail(email);
        if (existing != null && !existing.getUserId().equals(userId)) {
            throw new BizException(400, "该邮箱已被其他账号绑定");
        }
        if (account == null) {
            throw new BizException(404, "账号不存在");
        }
        account.setEmail(email);
        account.setEmailVerifiedAt(LocalDateTime.now());
        accountMapper.updateById(account);
        log.info("登录前邮箱绑定成功 userId={} email={}", userId, email);
    }

    /**
     * 退出登录
     */
    public void logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    private AuthUserAccount lookupAccount(String identifier) {
        AuthUserAccount account = accountMapper.selectByLoginName(identifier);
        if (account == null && identifier.contains("@")) {
            account = accountMapper.selectByEmail(identifier);
        }
        return account;
    }

    static String maskEmail(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 0) return email;
        String name = email.substring(0, at);
        String domain = email.substring(at + 1);
        String maskedName = name.length() <= 2
            ? name.charAt(0) + "*"
            : name.charAt(0) + "*".repeat(Math.min(name.length() - 2, 4)) + name.charAt(name.length() - 1);
        int dot = domain.lastIndexOf('.');
        String maskedDomain;
        if (dot <= 0) {
            maskedDomain = domain.length() <= 2 ? domain : domain.charAt(0) + "**";
        } else {
            String dHead = domain.substring(0, dot);
            String dTail = domain.substring(dot);
            String maskedHead = dHead.length() <= 2 ? dHead : dHead.charAt(0) + "**";
            maskedDomain = maskedHead + dTail;
        }
        return maskedName + "@" + maskedDomain;
    }

    private void updateLastLogin(UserType userType, Long userId) {
        try {
            if (userType == UserType.USER) {
                AuthUserAccount account = accountMapper.selectByUserId(userId);
                if (account != null) {
                    account.setLastLoginAt(LocalDateTime.now());
                    accountMapper.updateById(account);
                }
            } else {
                com.workforcehub.auth.domain.AdminUser admin = adminUserMapper.selectById(userId);
                if (admin != null) {
                    admin.setLastLoginAt(LocalDateTime.now());
                    adminUserMapper.updateById(admin);
                }
            }
        } catch (Exception e) {
            // 更新最后登录时间失败不阻断登录流程
            log.warn("更新 last_login_at 失败 userType={} userId={}", userType, userId, e);
        }
    }
}
