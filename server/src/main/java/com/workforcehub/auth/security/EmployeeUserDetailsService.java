package com.workforcehub.auth.security;

import com.workforcehub.auth.domain.AuthUserAccount;
import com.workforcehub.auth.domain.OrgUser;
import com.workforcehub.auth.mapper.AuthUserAccountMapper;
import com.workforcehub.auth.mapper.OrgUserMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 员工/领导端 UserDetailsService
 * 通过 login_name 加载账号，并关联 org_user 主档
 *
 * 登录准入矩阵（按 employment_status）：
 * - PENDING_ONBOARD（待入职）：不允许登录，档案虽建但尚未正式生效上岗
 * - PROBATION（试用期）：允许登录
 * - ACTIVE（在职）：允许登录
 * - PENDING_OFFBOARD（待离职）：允许登录，需要处理工作交接
 * - OFFBOARDED（已离职）：不允许登录
 */
@Service("employeeUserDetailsService")
public class EmployeeUserDetailsService implements UserDetailsService {

    /**
     * 允许登录的员工状态白名单
     */
    private static final Set<String> LOGIN_ALLOWED_STATUSES = Set.of(
        "PROBATION",
        "ACTIVE",
        "PENDING_OFFBOARD"
    );

    private final AuthUserAccountMapper accountMapper;
    private final OrgUserMapper orgUserMapper;

    public EmployeeUserDetailsService(AuthUserAccountMapper accountMapper,
                                      OrgUserMapper orgUserMapper) {
        this.accountMapper = accountMapper;
        this.orgUserMapper = orgUserMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String loginName) throws UsernameNotFoundException {
        AuthUserAccount account = accountMapper.selectByLoginName(loginName);
        if (account == null) {
            throw new UsernameNotFoundException("账号不存在");
        }

        OrgUser orgUser = orgUserMapper.selectById(account.getUserId());
        if (orgUser == null) {
            throw new UsernameNotFoundException("员工档案不存在");
        }

        // 按登录准入矩阵校验
        String status = orgUser.getEmploymentStatus();
        if (!LOGIN_ALLOWED_STATUSES.contains(status)) {
            String message = switch (status) {
                case "PENDING_ONBOARD" -> "尚未正式入职，无法登录";
                case "OFFBOARDED" -> "员工已离职，无法登录";
                default -> "当前员工状态不允许登录";
            };
            throw new EmploymentLoginBlockedException(message);
        }

        return new EmployeeUserDetails(
            orgUser.getId(),
            account.getId(),
            account.getLoginName(),
            account.getPasswordHash(),
            orgUser.getRealName(),
            account.getAccountStatus()
        );
    }
}
