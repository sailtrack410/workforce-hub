package com.workforcehub.auth.service;

import com.workforcehub.auth.domain.AuthUserAccount;
import com.workforcehub.auth.dto.LoginRequest;
import com.workforcehub.auth.dto.LoginResponse;
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
 */
@Slf4j
@Service
public class LoginService {

    private final WfhAuthenticationProvider employeeAuthProvider;
    private final WfhAuthenticationProvider adminAuthProvider;
    private final AuthLockService authLockService;
    private final AuthUserAccountMapper accountMapper;
    private final AdminUserMapper adminUserMapper;

    public LoginService(@Qualifier("employeeAuthProvider") WfhAuthenticationProvider employeeAuthProvider,
                        @Qualifier("adminAuthProvider") WfhAuthenticationProvider adminAuthProvider,
                        AuthLockService authLockService,
                        AuthUserAccountMapper accountMapper,
                        AdminUserMapper adminUserMapper) {
        this.employeeAuthProvider = employeeAuthProvider;
        this.adminAuthProvider = adminAuthProvider;
        this.authLockService = authLockService;
        this.accountMapper = accountMapper;
        this.adminUserMapper = adminUserMapper;
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

        // 3. Session 轮换：先废弃旧 Session，再创建新 Session 写入认证上下文
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

        // 4. 清除失败计数
        authLockService.clearFailures(userType, loginName);

        // 5. 更新最后登录时间
        WfhUserDetails user = (WfhUserDetails) authentication.getPrincipal();
        updateLastLogin(userType, user.getUserId());

        log.info("登录成功 userType={} userId={} loginName={} sessionId={}",
            userType, user.getUserId(), loginName, newSession.getId());

        // 6. 构造响应
        LoginResponse response = new LoginResponse();
        response.setUserId(user.getUserId());
        response.setUserType(userType);
        response.setDisplayName(user.getDisplayName());
        response.setLoginName(loginName);
        return response;
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

    private void updateLastLogin(UserType userType, Long userId) {
        try {
            if (userType == UserType.USER) {
                AuthUserAccount account = accountMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuthUserAccount>()
                        .eq(AuthUserAccount::getUserId, userId)
                );
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
