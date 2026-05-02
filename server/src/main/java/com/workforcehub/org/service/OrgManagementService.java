package com.workforcehub.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.auth.domain.AuthUserAccount;
import com.workforcehub.auth.domain.OrgUser;
import com.workforcehub.auth.mapper.AuthUserAccountMapper;
import com.workforcehub.auth.mapper.OrgUserMapper;
import com.workforcehub.common.enums.EmploymentStatus;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.hr.domain.HrEmployeeEvent;
import com.workforcehub.hr.mapper.HrEmployeeEventMapper;
import com.workforcehub.org.domain.OrgDepartment;
import com.workforcehub.org.domain.OrgDepartmentLeader;
import com.workforcehub.org.domain.OrgPosition;
import com.workforcehub.org.dto.OrgDepartmentSaveRequest;
import com.workforcehub.org.dto.OrgEmploymentStatusUpdateRequest;
import com.workforcehub.org.dto.OrgPositionSaveRequest;
import com.workforcehub.org.dto.OrgUserAccountUpsertRequest;
import com.workforcehub.org.dto.OrgUserCreateRequest;
import com.workforcehub.org.dto.OrgUserUpdateRequest;
import com.workforcehub.org.mapper.OrgDepartmentLeaderMapper;
import com.workforcehub.org.mapper.OrgDepartmentMapper;
import com.workforcehub.org.mapper.OrgPositionMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * module-org 第二批写侧治理服务
 * 收口部门、岗位、员工与账号联动的后台治理能力。
 */
@Service
public class OrgManagementService {

    private static final Set<String> DEPARTMENT_TYPES = Set.of("COMPANY", "BRANCH", "DEPARTMENT", "TEAM");
    private static final Set<String> RECORD_STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final Set<String> ACCOUNT_STATUSES = Set.of("ACTIVE", "LOCKED", "DISABLED");
    private static final Set<String> LOGIN_ALLOWED_EMPLOYMENT_STATUSES = Set.of("PROBATION", "ACTIVE", "PENDING_OFFBOARD");
    private static final Map<String, Set<String>> EMPLOYMENT_TRANSITIONS = Map.of(
        "PENDING_ONBOARD", Set.of("PROBATION", "ACTIVE"),
        "PROBATION", Set.of("ACTIVE", "PENDING_OFFBOARD"),
        "ACTIVE", Set.of("PENDING_OFFBOARD"),
        "PENDING_OFFBOARD", Set.of("PROBATION", "ACTIVE", "OFFBOARDED"),
        "OFFBOARDED", Set.of()
    );

    private final OrgDepartmentMapper departmentMapper;
    private final OrgPositionMapper positionMapper;
    private final OrgDepartmentLeaderMapper departmentLeaderMapper;
    private final OrgUserMapper orgUserMapper;
    private final AuthUserAccountMapper authUserAccountMapper;
    private final HrEmployeeEventMapper hrEmployeeEventMapper;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public OrgManagementService(OrgDepartmentMapper departmentMapper,
                                OrgPositionMapper positionMapper,
                                OrgDepartmentLeaderMapper departmentLeaderMapper,
                                OrgUserMapper orgUserMapper,
                                AuthUserAccountMapper authUserAccountMapper,
                                HrEmployeeEventMapper hrEmployeeEventMapper,
                                PasswordEncoder passwordEncoder,
                                ObjectMapper objectMapper) {
        this.departmentMapper = departmentMapper;
        this.positionMapper = positionMapper;
        this.departmentLeaderMapper = departmentLeaderMapper;
        this.orgUserMapper = orgUserMapper;
        this.authUserAccountMapper = authUserAccountMapper;
        this.hrEmployeeEventMapper = hrEmployeeEventMapper;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createDepartment(OrgDepartmentSaveRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        OrgDepartment parent = resolveParentDepartment(request.getParentId(), null);
        ensureDepartmentCodeUnique(request.getDeptCode(), null);
        validateDepartmentType(request.getDeptType());
        validateRecordStatus(request.getStatus());
        validateLeaderUser(request.getLeaderUserId());

        OrgDepartment department = new OrgDepartment();
        department.setId(IdWorker.getId());
        department.setParentId(parent == null ? null : parent.getId());
        department.setDeptCode(request.getDeptCode().trim());
        department.setDeptName(request.getDeptName().trim());
        department.setDeptType(normalizeUpper(request.getDeptType()));
        department.setDeptLevel(parent == null ? 0 : parent.getDeptLevel() + 1);
        department.setTreePath(buildTreePath(parent, department.getId()));
        department.setLeaderUserId(request.getLeaderUserId());
        department.setStatus(defaultRecordStatus(request.getStatus()));
        department.setCreatedBy(operatorId);
        department.setUpdatedBy(operatorId);
        departmentMapper.insert(department);
        syncPrimaryDepartmentLeader(department.getId(), request.getLeaderUserId(), operatorId);
        return department.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateDepartment(Long deptId, OrgDepartmentSaveRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        OrgDepartment department = getDepartmentOrThrow(deptId);
        OrgDepartment parent = resolveParentDepartment(request.getParentId(), deptId);
        ensureDepartmentCodeUnique(request.getDeptCode(), deptId);
        validateDepartmentType(request.getDeptType());
        validateRecordStatus(request.getStatus());
        validateLeaderUser(request.getLeaderUserId());

        String oldTreePath = department.getTreePath();
        Integer oldLevel = department.getDeptLevel();
        String newTreePath = buildTreePath(parent, deptId);
        Integer newLevel = parent == null ? 0 : parent.getDeptLevel() + 1;

        department.setParentId(parent == null ? null : parent.getId());
        department.setDeptCode(request.getDeptCode().trim());
        department.setDeptName(request.getDeptName().trim());
        department.setDeptType(normalizeUpper(request.getDeptType()));
        department.setDeptLevel(newLevel);
        department.setTreePath(newTreePath);
        department.setLeaderUserId(request.getLeaderUserId());
        department.setStatus(defaultRecordStatus(request.getStatus()));
        department.setUpdatedBy(operatorId);
        departmentMapper.updateById(department);

        if (!oldTreePath.equals(newTreePath)) {
            cascadeDepartmentTreePathChange(oldTreePath, newTreePath, oldLevel, newLevel, deptId, operatorId);
        }

        syncPrimaryDepartmentLeader(deptId, request.getLeaderUserId(), operatorId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createPosition(OrgPositionSaveRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        ensurePositionCodeUnique(request.getPositionCode(), null);
        validateRecordStatus(request.getStatus());

        OrgPosition position = new OrgPosition();
        position.setPositionCode(request.getPositionCode().trim());
        position.setPositionName(request.getPositionName().trim());
        position.setPositionCategory(trimToNull(request.getPositionCategory()));
        position.setStatus(defaultRecordStatus(request.getStatus()));
        position.setCreatedBy(operatorId);
        position.setUpdatedBy(operatorId);
        positionMapper.insert(position);
        return position.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePosition(Long positionId, OrgPositionSaveRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        OrgPosition position = getPositionOrThrow(positionId);
        ensurePositionCodeUnique(request.getPositionCode(), positionId);
        validateRecordStatus(request.getStatus());

        position.setPositionCode(request.getPositionCode().trim());
        position.setPositionName(request.getPositionName().trim());
        position.setPositionCategory(trimToNull(request.getPositionCategory()));
        position.setStatus(defaultRecordStatus(request.getStatus()));
        position.setUpdatedBy(operatorId);
        positionMapper.updateById(position);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createUser(OrgUserCreateRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        ensureEmployeeNoUnique(request.getEmployeeNo(), null);
        getDepartmentOrThrow(request.getPrimaryDeptId());
        validatePositionExists(request.getPositionId());
        validateManagerAssignment(request.getManagerUserId(), null);
        validateDateFields(request.getHireDate(), request.getRegularizedAt());

        OrgUser user = new OrgUser();
        user.setEmployeeNo(request.getEmployeeNo().trim());
        user.setRealName(request.getRealName().trim());
        user.setGender(trimToNull(request.getGender()));
        user.setMobile(trimToNull(request.getMobile()));
        user.setPrimaryDeptId(request.getPrimaryDeptId());
        user.setPositionId(request.getPositionId());
        user.setManagerUserId(request.getManagerUserId());
        user.setEmploymentStatus(defaultEmploymentStatus(request.getEmploymentStatus()));
        user.setHireDate(request.getHireDate());
        user.setRegularizedAt(request.getRegularizedAt());
        user.setServiceStartDate(defaultServiceStartDate(request.getServiceStartDate(), request.getHireDate()));
        user.setAnnualLeaveStartDate(defaultAnnualLeaveStartDate(
            request.getAnnualLeaveStartDate(),
            request.getServiceStartDate(),
            request.getHireDate()
        ));
        user.setWorkLocation(trimToNull(request.getWorkLocation()));
        user.setStatus(defaultRecordStatus(request.getStatus()));
        user.setCreatedBy(operatorId);
        user.setUpdatedBy(operatorId);
        orgUserMapper.insert(user);
        return user.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Long userId, OrgUserUpdateRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        OrgUser user = getUserOrThrow(userId);
        Map<String, Object> beforeSnapshot = userSnapshot(user);

        ensureEmployeeNoUnique(request.getEmployeeNo(), userId);
        getDepartmentOrThrow(request.getPrimaryDeptId());
        validatePositionExists(request.getPositionId());
        validateManagerAssignment(request.getManagerUserId(), userId);
        validateDateFields(request.getHireDate(), request.getRegularizedAt());

        Long beforeDeptId = user.getPrimaryDeptId();
        Long beforePositionId = user.getPositionId();
        Long beforeManagerUserId = user.getManagerUserId();
        String beforeStatus = user.getStatus();

        user.setEmployeeNo(request.getEmployeeNo().trim());
        user.setRealName(request.getRealName().trim());
        user.setGender(trimToNull(request.getGender()));
        user.setMobile(trimToNull(request.getMobile()));
        user.setPrimaryDeptId(request.getPrimaryDeptId());
        user.setPositionId(request.getPositionId());
        user.setManagerUserId(request.getManagerUserId());
        user.setHireDate(request.getHireDate());
        user.setRegularizedAt(request.getRegularizedAt());
        user.setServiceStartDate(defaultServiceStartDate(request.getServiceStartDate(), request.getHireDate()));
        user.setAnnualLeaveStartDate(defaultAnnualLeaveStartDate(
            request.getAnnualLeaveStartDate(),
            request.getServiceStartDate(),
            request.getHireDate()
        ));
        user.setWorkLocation(trimToNull(request.getWorkLocation()));
        user.setStatus(defaultRecordStatus(request.getStatus()));
        user.setUpdatedBy(operatorId);
        orgUserMapper.updateById(user);

        if (!"ACTIVE".equals(user.getStatus()) && "ACTIVE".equals(beforeStatus)) {
            forceDisableAccountIfExists(userId, operatorId);
        }

        Map<String, Object> afterSnapshot = userSnapshot(user);
        if (!equalsLong(beforeDeptId, user.getPrimaryDeptId())) {
            recordEmployeeEvent(userId, "TRANSFER_DEPARTMENT", beforeSnapshot, afterSnapshot, operatorId);
        }
        if (!equalsLong(beforePositionId, user.getPositionId())) {
            recordEmployeeEvent(userId, "TRANSFER_POSITION", beforeSnapshot, afterSnapshot, operatorId);
        }
        if (!equalsLong(beforeManagerUserId, user.getManagerUserId())) {
            recordEmployeeEvent(userId, "CHANGE_MANAGER", beforeSnapshot, afterSnapshot, operatorId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateEmploymentStatus(Long userId, OrgEmploymentStatusUpdateRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        OrgUser user = getUserOrThrow(userId);
        String targetStatus = normalizeEmploymentStatus(request.getEmploymentStatus());
        String currentStatus = user.getEmploymentStatus();
        if (currentStatus.equals(targetStatus)) {
            return;
        }

        Set<String> allowedTargets = EMPLOYMENT_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowedTargets.contains(targetStatus)) {
            throw new BizException(400, "不允许从 " + currentStatus + " 变更到 " + targetStatus);
        }

        Map<String, Object> beforeSnapshot = userSnapshot(user);
        user.setEmploymentStatus(targetStatus);
        user.setUpdatedBy(operatorId);
        orgUserMapper.updateById(user);

        if (!isUserEligibleForActiveAccount(user)) {
            forceDisableAccountIfExists(userId, operatorId);
        }

        Map<String, Object> afterSnapshot = userSnapshot(user);
        recordEmployeeEvent(userId, resolveEmploymentChangeType(currentStatus, targetStatus), beforeSnapshot, afterSnapshot, operatorId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void upsertUserAccount(Long userId, OrgUserAccountUpsertRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        OrgUser user = getUserOrThrow(userId);
        AuthUserAccount existing = authUserAccountMapper.selectOne(
            new LambdaQueryWrapper<AuthUserAccount>().eq(AuthUserAccount::getUserId, userId)
        );

        if (existing == null) {
            createUserAccount(user, request, operatorId);
            return;
        }

        if (StringUtils.hasText(request.getLoginName())) {
            String loginName = request.getLoginName().trim();
            ensureLoginNameUnique(loginName, existing.getId());
            existing.setLoginName(loginName);
        }
        if (request.getEmail() != null) {
            ensureEmailUnique(request.getEmail(), existing.getId());
            existing.setEmail(trimToNull(request.getEmail()));
        }
        if (StringUtils.hasText(request.getNewPassword())) {
            existing.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            existing.setPasswordUpdatedAt(LocalDateTime.now());
        }
        if (StringUtils.hasText(request.getAccountStatus())) {
            String accountStatus = normalizeAccountStatus(request.getAccountStatus());
            if ("ACTIVE".equals(accountStatus) && !isUserEligibleForActiveAccount(user)) {
                throw new BizException(400, "当前员工状态不允许启用登录账号");
            }
            existing.setAccountStatus(accountStatus);
        }
        existing.setUpdatedBy(operatorId);
        authUserAccountMapper.updateById(existing);
    }

    private void createUserAccount(OrgUser user, OrgUserAccountUpsertRequest request, Long operatorId) {
        if (!StringUtils.hasText(request.getLoginName())) {
            throw new BizException(400, "创建账号时 loginName 不能为空");
        }
        if (!StringUtils.hasText(request.getNewPassword())) {
            throw new BizException(400, "创建账号时 newPassword 不能为空");
        }

        String loginName = request.getLoginName().trim();
        ensureLoginNameUnique(loginName, null);
        ensureEmailUnique(request.getEmail(), null);

        String accountStatus = StringUtils.hasText(request.getAccountStatus())
            ? normalizeAccountStatus(request.getAccountStatus())
            : defaultAccountStatusForUser(user);
        if ("ACTIVE".equals(accountStatus) && !isUserEligibleForActiveAccount(user)) {
            throw new BizException(400, "当前员工状态不允许启用登录账号");
        }

        AuthUserAccount account = new AuthUserAccount();
        account.setUserId(user.getId());
        account.setLoginName(loginName);
        account.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        account.setEmail(trimToNull(request.getEmail()));
        account.setAccountStatus(accountStatus);
        account.setPasswordUpdatedAt(LocalDateTime.now());
        account.setCreatedBy(operatorId);
        account.setUpdatedBy(operatorId);
        authUserAccountMapper.insert(account);
    }

    private void cascadeDepartmentTreePathChange(String oldTreePath,
                                                 String newTreePath,
                                                 Integer oldLevel,
                                                 Integer newLevel,
                                                 Long selfDeptId,
                                                 Long operatorId) {
        List<OrgDepartment> descendants = departmentMapper.selectList(
            new LambdaQueryWrapper<OrgDepartment>()
                .likeRight(OrgDepartment::getTreePath, oldTreePath)
                .ne(OrgDepartment::getId, selfDeptId)
        );
        int delta = newLevel - oldLevel;
        for (OrgDepartment descendant : descendants) {
            descendant.setTreePath(newTreePath + descendant.getTreePath().substring(oldTreePath.length()));
            descendant.setDeptLevel(descendant.getDeptLevel() + delta);
            descendant.setUpdatedBy(operatorId);
            departmentMapper.updateById(descendant);
        }
    }

    private void syncPrimaryDepartmentLeader(Long deptId, Long leaderUserId, Long operatorId) {
        List<OrgDepartmentLeader> primaryLeaders = departmentLeaderMapper.selectList(
            new LambdaQueryWrapper<OrgDepartmentLeader>()
                .eq(OrgDepartmentLeader::getDeptId, deptId)
                .eq(OrgDepartmentLeader::getIsPrimary, 1)
        );
        for (OrgDepartmentLeader leader : primaryLeaders) {
            leader.setStatus("INACTIVE");
            leader.setUpdatedBy(operatorId);
            departmentLeaderMapper.updateById(leader);
        }

        if (leaderUserId == null) {
            return;
        }

        OrgDepartmentLeader target = departmentLeaderMapper.selectOne(
            new LambdaQueryWrapper<OrgDepartmentLeader>()
                .eq(OrgDepartmentLeader::getDeptId, deptId)
                .eq(OrgDepartmentLeader::getUserId, leaderUserId)
                .eq(OrgDepartmentLeader::getLeaderType, "PRIMARY")
        );
        if (target == null) {
            target = new OrgDepartmentLeader();
            target.setDeptId(deptId);
            target.setUserId(leaderUserId);
            target.setLeaderType("PRIMARY");
            target.setIsPrimary(1);
            target.setSortOrder(1);
            target.setStatus("ACTIVE");
            target.setCreatedBy(operatorId);
            target.setUpdatedBy(operatorId);
            departmentLeaderMapper.insert(target);
            return;
        }

        target.setIsPrimary(1);
        target.setSortOrder(1);
        target.setStatus("ACTIVE");
        target.setUpdatedBy(operatorId);
        departmentLeaderMapper.updateById(target);
    }

    private OrgDepartment resolveParentDepartment(Long parentId, Long selfDeptId) {
        if (parentId == null) {
            return null;
        }
        if (selfDeptId != null && parentId.equals(selfDeptId)) {
            throw new BizException(400, "部门不能挂到自己下面");
        }

        OrgDepartment parent = getDepartmentOrThrow(parentId);
        if (selfDeptId != null) {
            OrgDepartment self = getDepartmentOrThrow(selfDeptId);
            if (parent.getTreePath().startsWith(self.getTreePath())) {
                throw new BizException(400, "部门不能挂到自己的子部门下面");
            }
        }
        return parent;
    }

    private OrgDepartment getDepartmentOrThrow(Long deptId) {
        OrgDepartment department = departmentMapper.selectById(deptId);
        if (department == null) {
            throw new BizException(404, "部门不存在");
        }
        return department;
    }

    private OrgPosition getPositionOrThrow(Long positionId) {
        OrgPosition position = positionMapper.selectById(positionId);
        if (position == null) {
            throw new BizException(404, "岗位不存在");
        }
        return position;
    }

    private OrgUser getUserOrThrow(Long userId) {
        OrgUser user = orgUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(404, "员工不存在");
        }
        return user;
    }

    private void validatePositionExists(Long positionId) {
        if (positionId == null) {
            return;
        }
        OrgPosition position = getPositionOrThrow(positionId);
        if (!"ACTIVE".equals(position.getStatus())) {
            throw new BizException(400, "仅允许绑定启用状态的岗位");
        }
    }

    private void validateLeaderUser(Long leaderUserId) {
        if (leaderUserId == null) {
            return;
        }
        OrgUser user = getUserOrThrow(leaderUserId);
        if ("OFFBOARDED".equals(user.getEmploymentStatus())) {
            throw new BizException(400, "已离职员工不能担任部门负责人");
        }
    }

    private void validateManagerAssignment(Long managerUserId, Long selfUserId) {
        if (managerUserId == null) {
            return;
        }
        if (selfUserId != null && managerUserId.equals(selfUserId)) {
            throw new BizException(400, "直属上级不能是员工本人");
        }
        OrgUser manager = getUserOrThrow(managerUserId);
        if ("OFFBOARDED".equals(manager.getEmploymentStatus())) {
            throw new BizException(400, "已离职员工不能作为直属上级");
        }
        if (!"ACTIVE".equals(manager.getStatus())) {
            throw new BizException(400, "仅允许绑定启用状态的直属上级");
        }
        if (selfUserId == null) {
            return;
        }

        Set<Long> visited = new HashSet<>();
        Long currentUserId = managerUserId;
        while (currentUserId != null) {
            if (!visited.add(currentUserId)) {
                throw new BizException(400, "组织汇报关系存在循环");
            }
            if (currentUserId.equals(selfUserId)) {
                throw new BizException(400, "直属上级不能是当前员工的下级");
            }
            OrgUser current = orgUserMapper.selectById(currentUserId);
            currentUserId = current == null ? null : current.getManagerUserId();
        }
    }

    private void validateDateFields(LocalDate hireDate, LocalDate regularizedAt) {
        if (hireDate != null && regularizedAt != null && regularizedAt.isBefore(hireDate)) {
            throw new BizException(400, "regularizedAt 不能早于 hireDate");
        }
    }

    private void ensureDepartmentCodeUnique(String deptCode, Long selfDeptId) {
        LambdaQueryWrapper<OrgDepartment> wrapper = new LambdaQueryWrapper<OrgDepartment>()
            .eq(OrgDepartment::getDeptCode, deptCode.trim());
        if (selfDeptId != null) {
            wrapper.ne(OrgDepartment::getId, selfDeptId);
        }
        if (departmentMapper.selectCount(wrapper) > 0) {
            throw new BizException(400, "部门编码已存在");
        }
    }

    private void ensurePositionCodeUnique(String positionCode, Long selfPositionId) {
        LambdaQueryWrapper<OrgPosition> wrapper = new LambdaQueryWrapper<OrgPosition>()
            .eq(OrgPosition::getPositionCode, positionCode.trim());
        if (selfPositionId != null) {
            wrapper.ne(OrgPosition::getId, selfPositionId);
        }
        if (positionMapper.selectCount(wrapper) > 0) {
            throw new BizException(400, "岗位编码已存在");
        }
    }

    private void ensureEmployeeNoUnique(String employeeNo, Long selfUserId) {
        LambdaQueryWrapper<OrgUser> wrapper = new LambdaQueryWrapper<OrgUser>()
            .eq(OrgUser::getEmployeeNo, employeeNo.trim());
        if (selfUserId != null) {
            wrapper.ne(OrgUser::getId, selfUserId);
        }
        if (orgUserMapper.selectCount(wrapper) > 0) {
            throw new BizException(400, "工号已存在");
        }
    }

    private void ensureLoginNameUnique(String loginName, Long selfAccountId) {
        LambdaQueryWrapper<AuthUserAccount> wrapper = new LambdaQueryWrapper<AuthUserAccount>()
            .eq(AuthUserAccount::getLoginName, loginName);
        if (selfAccountId != null) {
            wrapper.ne(AuthUserAccount::getId, selfAccountId);
        }
        if (authUserAccountMapper.selectCount(wrapper) > 0) {
            throw new BizException(400, "登录名已存在");
        }
    }

    private void ensureEmailUnique(String email, Long selfAccountId) {
        if (!StringUtils.hasText(email)) {
            return;
        }
        LambdaQueryWrapper<AuthUserAccount> wrapper = new LambdaQueryWrapper<AuthUserAccount>()
            .eq(AuthUserAccount::getEmail, email.trim());
        if (selfAccountId != null) {
            wrapper.ne(AuthUserAccount::getId, selfAccountId);
        }
        if (authUserAccountMapper.selectCount(wrapper) > 0) {
            throw new BizException(400, "邮箱已被其他账号占用");
        }
    }

    private void validateDepartmentType(String deptType) {
        String normalized = normalizeUpper(deptType);
        if (!DEPARTMENT_TYPES.contains(normalized)) {
            throw new BizException(400, "deptType 不合法");
        }
    }

    private void validateRecordStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return;
        }
        String normalized = normalizeUpper(status);
        if (!RECORD_STATUSES.contains(normalized)) {
            throw new BizException(400, "status 仅支持 ACTIVE 或 INACTIVE");
        }
    }

    private String defaultRecordStatus(String status) {
        return StringUtils.hasText(status) ? normalizeUpper(status) : "ACTIVE";
    }

    private String defaultEmploymentStatus(String employmentStatus) {
        if (!StringUtils.hasText(employmentStatus)) {
            return EmploymentStatus.PENDING_ONBOARD.getCode();
        }
        return normalizeEmploymentStatus(employmentStatus);
    }

    private String normalizeEmploymentStatus(String employmentStatus) {
        String normalized = normalizeUpper(employmentStatus);
        for (EmploymentStatus value : EmploymentStatus.values()) {
            if (value.getCode().equals(normalized)) {
                return normalized;
            }
        }
        throw new BizException(400, "employmentStatus 不合法");
    }

    private String normalizeAccountStatus(String accountStatus) {
        String normalized = normalizeUpper(accountStatus);
        if (!ACCOUNT_STATUSES.contains(normalized)) {
            throw new BizException(400, "accountStatus 不合法");
        }
        return normalized;
    }

    private String defaultAccountStatusForUser(OrgUser user) {
        return isUserEligibleForActiveAccount(user) ? "ACTIVE" : "DISABLED";
    }

    private boolean isUserEligibleForActiveAccount(OrgUser user) {
        return "ACTIVE".equals(user.getStatus())
            && LOGIN_ALLOWED_EMPLOYMENT_STATUSES.contains(user.getEmploymentStatus());
    }

    private void forceDisableAccountIfExists(Long userId, Long operatorId) {
        AuthUserAccount account = authUserAccountMapper.selectOne(
            new LambdaQueryWrapper<AuthUserAccount>().eq(AuthUserAccount::getUserId, userId)
        );
        if (account == null) {
            return;
        }
        if ("DISABLED".equals(account.getAccountStatus())) {
            return;
        }
        account.setAccountStatus("DISABLED");
        account.setUpdatedBy(operatorId);
        authUserAccountMapper.updateById(account);
    }

    private String resolveEmploymentChangeType(String beforeStatus, String afterStatus) {
        if ("PENDING_ONBOARD".equals(beforeStatus)
            && ("PROBATION".equals(afterStatus) || "ACTIVE".equals(afterStatus))) {
            return "ONBOARD";
        }
        if ("PROBATION".equals(beforeStatus) && "ACTIVE".equals(afterStatus)) {
            return "REGULARIZE";
        }
        if ("OFFBOARDED".equals(afterStatus)) {
            return "OFFBOARD";
        }
        if ("PENDING_OFFBOARD".equals(afterStatus)) {
            return "PREPARE_OFFBOARD";
        }
        return "CHANGE_EMPLOYMENT_STATUS";
    }

    private void recordEmployeeEvent(Long userId,
                                     String changeType,
                                     Map<String, Object> beforeSnapshot,
                                     Map<String, Object> afterSnapshot,
                                     Long operatorId) {
        HrEmployeeEvent event = new HrEmployeeEvent();
        event.setUserId(userId);
        event.setChangeType(changeType);
        event.setBeforeSnapshotJson(toJson(beforeSnapshot));
        event.setAfterSnapshotJson(toJson(afterSnapshot));
        event.setEffectiveAt(LocalDateTime.now());
        event.setStatus("EFFECTIVE");
        event.setCreatedBy(operatorId);
        event.setUpdatedBy(operatorId);
        hrEmployeeEventMapper.insert(event);
    }

    private Map<String, Object> userSnapshot(OrgUser user) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("userId", user.getId());
        snapshot.put("employeeNo", user.getEmployeeNo());
        snapshot.put("realName", user.getRealName());
        snapshot.put("primaryDeptId", user.getPrimaryDeptId());
        snapshot.put("positionId", user.getPositionId());
        snapshot.put("managerUserId", user.getManagerUserId());
        snapshot.put("employmentStatus", user.getEmploymentStatus());
        snapshot.put("status", user.getStatus());
        snapshot.put("hireDate", user.getHireDate());
        snapshot.put("regularizedAt", user.getRegularizedAt());
        snapshot.put("serviceStartDate", user.getServiceStartDate());
        snapshot.put("annualLeaveStartDate", user.getAnnualLeaveStartDate());
        snapshot.put("workLocation", user.getWorkLocation());
        return snapshot;
    }

    private String toJson(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("员工事件快照序列化失败", e);
        }
    }

    private LocalDate defaultServiceStartDate(LocalDate serviceStartDate, LocalDate hireDate) {
        return serviceStartDate != null ? serviceStartDate : hireDate;
    }

    private LocalDate defaultAnnualLeaveStartDate(LocalDate annualLeaveStartDate,
                                                  LocalDate serviceStartDate,
                                                  LocalDate hireDate) {
        if (annualLeaveStartDate != null) {
            return annualLeaveStartDate;
        }
        if (serviceStartDate != null) {
            return serviceStartDate;
        }
        return hireDate;
    }

    private String buildTreePath(OrgDepartment parent, Long deptId) {
        if (parent == null) {
            return "/" + deptId + "/";
        }
        return parent.getTreePath() + deptId + "/";
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeUpper(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean equalsLong(Long left, Long right) {
        return left == null ? right == null : left.equals(right);
    }
}
