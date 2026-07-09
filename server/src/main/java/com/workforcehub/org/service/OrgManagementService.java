package com.workforcehub.org.service;

import com.workforcehub.common.util.IdGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.auth.domain.AuthUserAccount;
import com.workforcehub.auth.domain.OrgUser;
import com.workforcehub.auth.mapper.AuthUserAccountMapper;
import com.workforcehub.auth.mapper.OrgUserMapper;
import com.workforcehub.common.event.DomainEventPublisher;
import com.workforcehub.common.event.org.OrgDepartmentStructureChangedEvent;
import com.workforcehub.common.event.org.OrgUserAccountChangedEvent;
import com.workforcehub.common.event.org.OrgUserCreatedEvent;
import com.workforcehub.common.event.org.OrgUserManagerChangedEvent;
import com.workforcehub.common.event.org.OrgUserStatusChangedEvent;
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

import static com.workforcehub.common.util.TextValues.trimToNull;

/**
 * module-org 第二批写侧治理服务
 * 组织管理：部门/岗位 CRUD、员工入职/离职/调岗、账号管理，通过领域事件通知下游
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
    private final DomainEventPublisher domainEventPublisher;

    public OrgManagementService(OrgDepartmentMapper departmentMapper,
                                OrgPositionMapper positionMapper,
                                OrgDepartmentLeaderMapper departmentLeaderMapper,
                                OrgUserMapper orgUserMapper,
                                AuthUserAccountMapper authUserAccountMapper,
                                HrEmployeeEventMapper hrEmployeeEventMapper,
                                PasswordEncoder passwordEncoder,
                                ObjectMapper objectMapper,
                                DomainEventPublisher domainEventPublisher) {
        this.departmentMapper = departmentMapper;
        this.positionMapper = positionMapper;
        this.departmentLeaderMapper = departmentLeaderMapper;
        this.orgUserMapper = orgUserMapper;
        this.authUserAccountMapper = authUserAccountMapper;
        this.hrEmployeeEventMapper = hrEmployeeEventMapper;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createDepartment(OrgDepartmentSaveRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        OrgDepartment parent = resolveParentDepartment(request.getParentId(), null);
        String deptCode = resolveDepartmentCode(request.getDeptCode(), null);
        ensureDepartmentCodeUnique(deptCode, null);
        validateDepartmentType(request.getDeptType());
        validateRecordStatus(request.getStatus());
        validateLeaderUser(request.getLeaderUserId());

        OrgDepartment department = new OrgDepartment();
        department.setId(IdGenerator.nextId());
        department.setParentId(parent == null ? null : parent.getId());
        department.setDeptCode(deptCode);
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
        publishDepartmentStructureChangedEvent(
            "CREATED",
            department,
            null,
            department.getParentId(),
            null,
            department.getLeaderUserId(),
            null,
            department.getStatus(),
            operatorId
        );
        return department.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateDepartment(Long deptId, OrgDepartmentSaveRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        OrgDepartment department = getDepartmentOrThrow(deptId);
        OrgDepartment parent = resolveParentDepartment(request.getParentId(), deptId);
        String deptCode = resolveDepartmentCode(request.getDeptCode(), department.getDeptCode());
        ensureDepartmentCodeUnique(deptCode, deptId);
        validateDepartmentType(request.getDeptType());
        validateRecordStatus(request.getStatus());
        validateLeaderUser(request.getLeaderUserId());

        String oldTreePath = department.getTreePath();
        Integer oldLevel = department.getDeptLevel();
        String newTreePath = buildTreePath(parent, deptId);
        Integer newLevel = parent == null ? 0 : parent.getDeptLevel() + 1;
        Long beforeParentId = department.getParentId();
        Long beforeLeaderUserId = department.getLeaderUserId();
        String beforeStatus = department.getStatus();

        department.setParentId(parent == null ? null : parent.getId());
        department.setDeptCode(deptCode);
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
        publishDepartmentStructureChangedEvent(
            "UPDATED",
            department,
            beforeParentId,
            department.getParentId(),
            beforeLeaderUserId,
            department.getLeaderUserId(),
            beforeStatus,
            department.getStatus(),
            operatorId
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDepartment(Long deptId) {
        Long operatorId = CurrentUserHolder.requireUserId();
        OrgDepartment department = departmentMapper.selectById(deptId);
        if (department == null) {
            return false;
        }
        if (departmentMapper.countByParentId(deptId) > 0) {
            throw new BizException(400, "部门存在子部门");
        }
        if (orgUserMapper.countByPrimaryDeptId(deptId) > 0) {
            throw new BizException(400, "部门存在员工");
        }

        List<OrgDepartmentLeader> leaders = departmentLeaderMapper.listByDeptId(deptId);
        for (OrgDepartmentLeader leader : leaders) {
            leader.setStatus("INACTIVE");
            leader.setUpdatedBy(operatorId);
            departmentLeaderMapper.updateById(leader);
        }
        department.setUpdatedBy(operatorId);
        departmentMapper.updateById(department);
        departmentMapper.deleteById(deptId);
        publishDepartmentStructureChangedEvent(
            "DELETED",
            department,
            department.getParentId(),
            null,
            department.getLeaderUserId(),
            null,
            department.getStatus(),
            "DELETED",
            operatorId
        );
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createPosition(OrgPositionSaveRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        String positionCode = resolvePositionCode(request.getPositionCode(), null);
        ensurePositionCodeUnique(positionCode, null);
        validateRecordStatus(request.getStatus());

        OrgPosition position = new OrgPosition();
        position.setId(IdGenerator.nextId());
        position.setPositionCode(positionCode);
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
        String positionCode = resolvePositionCode(request.getPositionCode(), position.getPositionCode());
        ensurePositionCodeUnique(positionCode, positionId);
        validateRecordStatus(request.getStatus());

        position.setPositionCode(positionCode);
        position.setPositionName(request.getPositionName().trim());
        position.setPositionCategory(trimToNull(request.getPositionCategory()));
        position.setStatus(defaultRecordStatus(request.getStatus()));
        position.setUpdatedBy(operatorId);
        positionMapper.updateById(position);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createUser(OrgUserCreateRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        String employeeNo = resolveEmployeeNo(request.getEmployeeNo(), null);
        ensureEmployeeNoUnique(employeeNo, null);
        getDepartmentOrThrow(request.getPrimaryDeptId());
        validatePositionExists(request.getPositionId());
        validateManagerAssignment(request.getManagerUserId(), null);
        validateDateFields(request.getHireDate(), request.getRegularizedAt());

        OrgUser user = new OrgUser();
        user.setId(IdGenerator.nextId());
        user.setEmployeeNo(employeeNo);
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
        publishUserCreatedEvent(user, operatorId);
        return user.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Long userId, OrgUserUpdateRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        OrgUser user = getUserOrThrow(userId);
        Map<String, Object> beforeSnapshot = userSnapshot(user);

        String employeeNo = resolveEmployeeNo(request.getEmployeeNo(), user.getEmployeeNo());
        ensureEmployeeNoUnique(employeeNo, userId);
        getDepartmentOrThrow(request.getPrimaryDeptId());
        validatePositionExists(request.getPositionId());
        validateManagerAssignment(request.getManagerUserId(), userId);
        validateDateFields(request.getHireDate(), request.getRegularizedAt());

        Long beforeDeptId = user.getPrimaryDeptId();
        Long beforePositionId = user.getPositionId();
        Long beforeManagerUserId = user.getManagerUserId();
        String beforeStatus = user.getStatus();

        user.setEmployeeNo(employeeNo);
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

        AccountMutation accountMutation = null;
        if (!"ACTIVE".equals(user.getStatus()) && "ACTIVE".equals(beforeStatus)) {
            accountMutation = forceDisableAccountIfExists(userId, operatorId);
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
            publishUserManagerChangedEvent(user, beforeManagerUserId, operatorId);
        }
        if (!beforeStatus.equals(user.getStatus())) {
            publishUserStatusChangedEvent("RECORD_STATUS", user, beforeStatus, user.getStatus(), operatorId);
        }
        if (accountMutation != null) {
            publishUserAccountChangedEvent(userId, accountMutation, operatorId);
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

        AccountMutation accountMutation = null;
        if (!isUserEligibleForActiveAccount(user)) {
            accountMutation = forceDisableAccountIfExists(userId, operatorId);
        }

        Map<String, Object> afterSnapshot = userSnapshot(user);
        recordEmployeeEvent(userId, resolveEmploymentChangeType(currentStatus, targetStatus), beforeSnapshot, afterSnapshot, operatorId);
        publishUserStatusChangedEvent("EMPLOYMENT_STATUS", user, currentStatus, targetStatus, operatorId);
        if (accountMutation != null) {
            publishUserAccountChangedEvent(userId, accountMutation, operatorId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void upsertUserAccount(Long userId, OrgUserAccountUpsertRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        OrgUser user = getUserOrThrow(userId);
        AuthUserAccount existing = authUserAccountMapper.selectByUserId(userId);

        if (existing == null) {
            AccountMutation created = createUserAccount(user, request, operatorId);
            publishUserAccountChangedEvent(userId, created, operatorId);
            return;
        }

        String beforeLoginName = existing.getLoginName();
        String beforeEmail = existing.getEmail();
        String beforeAccountStatus = existing.getAccountStatus();
        boolean passwordChanged = false;

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
            passwordChanged = true;
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

        AccountMutation updated = buildAccountMutation(
            "UPDATED",
            beforeLoginName,
            existing.getLoginName(),
            beforeEmail,
            existing.getEmail(),
            beforeAccountStatus,
            existing.getAccountStatus(),
            passwordChanged
        );
        if (updated != null) {
            publishUserAccountChangedEvent(userId, updated, operatorId);
        }
    }

    private AccountMutation createUserAccount(OrgUser user, OrgUserAccountUpsertRequest request, Long operatorId) {
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
        account.setId(IdGenerator.nextId());
        account.setUserId(user.getId());
        account.setLoginName(loginName);
        account.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        account.setEmail(trimToNull(request.getEmail()));
        account.setAccountStatus(accountStatus);
        account.setPasswordUpdatedAt(LocalDateTime.now());
        account.setCreatedBy(operatorId);
        account.setUpdatedBy(operatorId);
        authUserAccountMapper.insert(account);
        return new AccountMutation(
            "CREATED",
            account.getLoginName(),
            account.getEmail(),
            null,
            account.getAccountStatus(),
            true,
            true,
            account.getEmail() != null
        );
    }

    private void cascadeDepartmentTreePathChange(String oldTreePath,
                                                 String newTreePath,
                                                 Integer oldLevel,
                                                 Integer newLevel,
                                                 Long selfDeptId,
                                                 Long operatorId) {
        List<OrgDepartment> descendants = departmentMapper.listByTreePathPrefixExcludingId(oldTreePath, selfDeptId);
        int delta = newLevel - oldLevel;
        for (OrgDepartment descendant : descendants) {
            descendant.setTreePath(newTreePath + descendant.getTreePath().substring(oldTreePath.length()));
            descendant.setDeptLevel(descendant.getDeptLevel() + delta);
            descendant.setUpdatedBy(operatorId);
            departmentMapper.updateById(descendant);
        }
    }

    private void syncPrimaryDepartmentLeader(Long deptId, Long leaderUserId, Long operatorId) {
        List<OrgDepartmentLeader> primaryLeaders = departmentLeaderMapper.listPrimaryByDeptId(deptId);
        for (OrgDepartmentLeader leader : primaryLeaders) {
            leader.setStatus("INACTIVE");
            leader.setUpdatedBy(operatorId);
            departmentLeaderMapper.updateById(leader);
        }

        if (leaderUserId == null) {
            return;
        }

        OrgDepartmentLeader target = departmentLeaderMapper.selectByDeptIdAndUserIdAndLeaderType(
            deptId,
            leaderUserId,
            "PRIMARY"
        );
        if (target == null) {
            target = new OrgDepartmentLeader();
            target.setId(IdGenerator.nextId());
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
        if (departmentMapper.countByDeptCode(deptCode.trim(), selfDeptId) > 0) {
            throw new BizException(400, "部门编码已存在");
        }
    }

    private void ensurePositionCodeUnique(String positionCode, Long selfPositionId) {
        if (positionMapper.countByPositionCode(positionCode.trim(), selfPositionId) > 0) {
            throw new BizException(400, "岗位编码已存在");
        }
    }

    private String resolveDepartmentCode(String deptCode, String currentCode) {
        return resolveOrgCode(deptCode, currentCode, "DEPT");
    }

    private String resolvePositionCode(String positionCode, String currentCode) {
        return resolveOrgCode(positionCode, currentCode, "POST");
    }

    private String resolveEmployeeNo(String employeeNo, String currentEmployeeNo) {
        return resolveOrgCode(employeeNo, currentEmployeeNo, "EMP");
    }

    private String resolveOrgCode(String value, String currentCode, String prefix) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            return normalized.trim();
        }
        if (StringUtils.hasText(currentCode)) {
            return currentCode.trim();
        }
        return prefix + "_" + IdGenerator.nextIdString();
    }

    private void ensureEmployeeNoUnique(String employeeNo, Long selfUserId) {
        if (orgUserMapper.countByEmployeeNo(employeeNo.trim(), selfUserId) > 0) {
            throw new BizException(400, "工号已存在");
        }
    }

    private void ensureLoginNameUnique(String loginName, Long selfAccountId) {
        if (authUserAccountMapper.countByLoginName(loginName, selfAccountId) > 0) {
            throw new BizException(400, "登录名已存在");
        }
    }

    private void ensureEmailUnique(String email, Long selfAccountId) {
        if (!StringUtils.hasText(email)) {
            return;
        }
        if (authUserAccountMapper.countByEmail(email.trim(), selfAccountId) > 0) {
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

    private AccountMutation forceDisableAccountIfExists(Long userId, Long operatorId) {
        AuthUserAccount account = authUserAccountMapper.selectByUserId(userId);
        if (account == null) {
            return null;
        }
        if ("DISABLED".equals(account.getAccountStatus())) {
            return null;
        }
        String beforeStatus = account.getAccountStatus();
        account.setAccountStatus("DISABLED");
        account.setUpdatedBy(operatorId);
        authUserAccountMapper.updateById(account);
        return new AccountMutation(
            "AUTO_DISABLED",
            account.getLoginName(),
            account.getEmail(),
            beforeStatus,
            account.getAccountStatus(),
            false,
            false,
            false
        );
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
        event.setId(IdGenerator.nextId());
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

    private void publishDepartmentStructureChangedEvent(String changeAction,
                                                        OrgDepartment department,
                                                        Long beforeParentId,
                                                        Long afterParentId,
                                                        Long beforeLeaderUserId,
                                                        Long afterLeaderUserId,
                                                        String beforeStatus,
                                                        String afterStatus,
                                                        Long operatorId) {
        domainEventPublisher.publishAfterCommit(new OrgDepartmentStructureChangedEvent(
            changeAction,
            department.getId(),
            department.getDeptCode(),
            department.getDeptName(),
            beforeParentId,
            afterParentId,
            beforeLeaderUserId,
            afterLeaderUserId,
            beforeStatus,
            afterStatus,
            department.getTreePath(),
            operatorId,
            LocalDateTime.now()
        ));
    }

    private void publishUserCreatedEvent(OrgUser user, Long operatorId) {
        domainEventPublisher.publishAfterCommit(new OrgUserCreatedEvent(
            user.getId(),
            user.getEmployeeNo(),
            user.getRealName(),
            user.getPrimaryDeptId(),
            user.getPositionId(),
            user.getManagerUserId(),
            user.getEmploymentStatus(),
            user.getStatus(),
            operatorId,
            LocalDateTime.now()
        ));
    }

    private void publishUserManagerChangedEvent(OrgUser user, Long beforeManagerUserId, Long operatorId) {
        domainEventPublisher.publishAfterCommit(new OrgUserManagerChangedEvent(
            user.getId(),
            user.getEmployeeNo(),
            user.getPrimaryDeptId(),
            beforeManagerUserId,
            user.getManagerUserId(),
            operatorId,
            LocalDateTime.now()
        ));
    }

    private void publishUserStatusChangedEvent(String statusType,
                                               OrgUser user,
                                               String beforeStatus,
                                               String afterStatus,
                                               Long operatorId) {
        AuthUserAccount account = findUserAccount(user.getId());
        domainEventPublisher.publishAfterCommit(new OrgUserStatusChangedEvent(
            statusType,
            user.getId(),
            user.getEmployeeNo(),
            beforeStatus,
            afterStatus,
            user.getEmploymentStatus(),
            user.getStatus(),
            account == null ? null : account.getAccountStatus(),
            operatorId,
            LocalDateTime.now()
        ));
    }

    private void publishUserAccountChangedEvent(Long userId, AccountMutation mutation, Long operatorId) {
        domainEventPublisher.publishAfterCommit(new OrgUserAccountChangedEvent(
            mutation.changeAction(),
            userId,
            mutation.loginName(),
            mutation.email(),
            mutation.beforeAccountStatus(),
            mutation.afterAccountStatus(),
            mutation.loginNameChanged(),
            mutation.emailChanged(),
            mutation.passwordChanged(),
            operatorId,
            LocalDateTime.now()
        ));
    }

    private AuthUserAccount findUserAccount(Long userId) {
        return authUserAccountMapper.selectByUserId(userId);
    }

    private AccountMutation buildAccountMutation(String changeAction,
                                                 String beforeLoginName,
                                                 String afterLoginName,
                                                 String beforeEmail,
                                                 String afterEmail,
                                                 String beforeAccountStatus,
                                                 String afterAccountStatus,
                                                 boolean passwordChanged) {
        boolean loginNameChanged = !equalsString(beforeLoginName, afterLoginName);
        boolean emailChanged = !equalsString(beforeEmail, afterEmail);
        boolean accountStatusChanged = !equalsString(beforeAccountStatus, afterAccountStatus);
        if (!loginNameChanged && !emailChanged && !accountStatusChanged && !passwordChanged) {
            return null;
        }

        return new AccountMutation(
            changeAction,
            afterLoginName,
            afterEmail,
            beforeAccountStatus,
            afterAccountStatus,
            loginNameChanged,
            passwordChanged,
            emailChanged
        );
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

    private String normalizeUpper(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean equalsLong(Long left, Long right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean equalsString(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private record AccountMutation(
        String changeAction,
        String loginName,
        String email,
        String beforeAccountStatus,
        String afterAccountStatus,
        boolean loginNameChanged,
        boolean passwordChanged,
        boolean emailChanged
    ) {
    }
}
