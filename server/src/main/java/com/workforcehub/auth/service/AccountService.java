package com.workforcehub.auth.service;

import com.workforcehub.auth.domain.AuthUserAccount;
import com.workforcehub.auth.dto.BindEmailRequest;
import com.workforcehub.auth.dto.CurrentUserResponse;
import com.workforcehub.auth.dto.ResetPasswordRequest;
import com.workforcehub.auth.domain.OrgUser;
import com.workforcehub.auth.mapper.AuthUserAccountMapper;
import com.workforcehub.auth.mapper.OrgUserMapper;
import com.workforcehub.auth.security.WfhUserDetails;
import com.workforcehub.common.enums.UserType;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 账号服务
 * 负责：当前用户信息查询、邮箱绑定、密码重置
 */
@Slf4j
@Service
public class AccountService {

    private final AuthUserAccountMapper accountMapper;
    private final AdminPermissionService adminPermissionService;
    private final OrgUserMapper orgUserMapper;
    private final EmailCodeService emailCodeService;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AuthUserAccountMapper accountMapper,
                          AdminPermissionService adminPermissionService,
                          OrgUserMapper orgUserMapper,
                          EmailCodeService emailCodeService,
                          PasswordEncoder passwordEncoder) {
        this.accountMapper = accountMapper;
        this.adminPermissionService = adminPermissionService;
        this.orgUserMapper = orgUserMapper;
        this.emailCodeService = emailCodeService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 获取当前登录用户信息
     */
    public CurrentUserResponse getCurrentUser() {
        WfhUserDetails user = CurrentUserHolder.current();
        if (user == null) {
            throw new BizException(401, "未登录");
        }

        CurrentUserResponse response = new CurrentUserResponse();
        response.setUserId(user.getUserId());
        response.setUserType(user.getUserType());
        response.setDisplayName(user.getDisplayName());
        response.setLoginName(user.getUsername());
        if (user.getUserType() == UserType.ADMIN) {
            AdminPermissionService.AdminPermissionProfile permissionProfile =
                adminPermissionService.resolveProfile(user.getUserId());
            response.setRoleCodes(permissionProfile.roleCodes());
            response.setPermissionCodes(permissionProfile.permissionCodes());
        }
        if (user.getUserType() == UserType.USER) {
            AuthUserAccount account = accountMapper.selectByUserId(user.getUserId());
            if (account != null) {
                response.setEmail(account.getEmail());
            }
        }

        if (user.getUserType() == UserType.USER) {
            OrgUser orgUser = orgUserMapper.selectById(user.getUserId());
            if (orgUser != null) {
                response.setPrimaryDeptId(orgUser.getPrimaryDeptId());
                response.setPositionId(orgUser.getPositionId());
                response.setManagerUserId(orgUser.getManagerUserId());
                response.setEmploymentStatus(orgUser.getEmploymentStatus());
            }
        }

        return response;
    }

    /**
     * 绑定邮箱（员工端）
     */
    public void bindEmail(BindEmailRequest request) {
        if (!CurrentUserHolder.isEmployee()) {
            throw new BizException(403, "仅员工端支持邮箱绑定");
        }

        // 校验验证码
        if (!emailCodeService.verifyCode(request.getEmail(), "BIND", request.getCode())) {
            throw new BizException(400, "验证码错误或已过期");
        }

        // 校验邮箱是否被其他账号占用
        AuthUserAccount existing = accountMapper.selectByEmail(request.getEmail());
        Long currentUserId = CurrentUserHolder.requireUserId();
        if (existing != null && !existing.getUserId().equals(currentUserId)) {
            throw new BizException(400, "该邮箱已被其他账号绑定");
        }

        AuthUserAccount account = accountMapper.selectByUserId(currentUserId);
        if (account == null) {
            throw new BizException(404, "账号不存在");
        }

        account.setEmail(request.getEmail());
        account.setEmailVerifiedAt(LocalDateTime.now());
        accountMapper.updateById(account);

        log.info("邮箱绑定成功 userId={} email={}", currentUserId, request.getEmail());
    }

    /**
     * 通过邮箱验证码重置密码（员工端，无需登录）
     */
    public void resetPassword(ResetPasswordRequest request) {
        if (!emailCodeService.verifyCode(request.getEmail(), "RESET_PASSWORD", request.getCode())) {
            throw new BizException(400, "验证码错误或已过期");
        }

        AuthUserAccount account = accountMapper.selectByEmail(request.getEmail());
        if (account == null) {
            throw new BizException(404, "邮箱未绑定任何账号");
        }

        account.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        account.setPasswordUpdatedAt(LocalDateTime.now());
        accountMapper.updateById(account);

        log.info("密码重置成功 userId={} email={}", account.getUserId(), request.getEmail());
    }
}
