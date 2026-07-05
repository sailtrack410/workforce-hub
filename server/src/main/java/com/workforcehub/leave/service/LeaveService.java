package com.workforcehub.leave.service;

import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.leave.dto.AdjustLeaveAccountRequest;
import com.workforcehub.leave.dto.CreateLeaveTypeRequest;
import com.workforcehub.leave.dto.LeaveAccountResponse;
import com.workforcehub.leave.dto.LeaveTypeResponse;
import com.workforcehub.leave.repository.ApprovedLeavePeriod;
import com.workforcehub.leave.repository.LeaveAccountRow;
import com.workforcehub.leave.repository.LeaveAccountTxnRow;
import com.workforcehub.leave.repository.LeaveRepository;
import com.workforcehub.leave.repository.LeaveTypeRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.workforcehub.common.util.TextValues.trimToNull;

/**
 * module-leave 第一版假期账户服务。
 */
@Service
public class LeaveService {

    private static final String BALANCE_MODE_DEDUCT = "DEDUCT";
    private static final String BALANCE_MODE_NO_DEDUCT = "NO_DEDUCT";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final String TXN_TYPE_ADJUST = "ADJUST";
    private static final String TXN_TYPE_DEDUCT = "DEDUCT";
    private static final String TXN_TYPE_FREEZE = "FREEZE";
    private static final String TXN_TYPE_UNFREEZE = "UNFREEZE";
    private static final String SOURCE_TYPE_ADMIN = "ADMIN";
    private static final String SOURCE_TYPE_WORKFLOW_REQUEST = "WORKFLOW_REQUEST";

    private final LeaveRepository leaveRepository;

    public LeaveService(LeaveRepository leaveRepository) {
        this.leaveRepository = leaveRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public LeaveTypeResponse createLeaveType(CreateLeaveTypeRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        String leaveCode = request.getLeaveCode().trim().toUpperCase(Locale.ROOT);
        String leaveName = request.getLeaveName().trim();
        String balanceMode = normalizeBalanceMode(request.getBalanceMode());
        String status = normalizeStatus(request.getStatus());
        if (leaveRepository.existsLeaveCode(leaveCode)) {
            throw new BizException(409, "假种编码已存在");
        }

        Long leaveTypeId = IdGenerator.nextId();
        leaveRepository.insertLeaveType(leaveTypeId, leaveCode, leaveName, balanceMode, status, operatorId);
        return toLeaveTypeResponse(leaveRepository.getLeaveTypeOrThrow(leaveTypeId));
    }

    @Transactional(rollbackFor = Exception.class)
    public LeaveTypeResponse updateLeaveType(Long leaveTypeId, CreateLeaveTypeRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        leaveRepository.getLeaveTypeOrThrow(leaveTypeId);
        String leaveCode = request.getLeaveCode().trim().toUpperCase(Locale.ROOT);
        String leaveName = request.getLeaveName().trim();
        String balanceMode = normalizeBalanceMode(request.getBalanceMode());
        String status = normalizeStatus(request.getStatus());
        if (leaveRepository.existsLeaveCodeExceptId(leaveCode, leaveTypeId)) {
            throw new BizException(409, "假种编码已存在");
        }
        int updated = leaveRepository.updateLeaveType(leaveTypeId, leaveCode, leaveName, balanceMode, status, operatorId);
        if (updated == 0) {
            throw new BizException(404, "假种不存在");
        }
        return toLeaveTypeResponse(leaveRepository.getLeaveTypeOrThrow(leaveTypeId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteLeaveType(Long leaveTypeId) {
        Long operatorId = CurrentUserHolder.requireUserId();
        int updated = leaveRepository.deleteLeaveType(leaveTypeId, operatorId);
        if (updated == 0) {
            throw new BizException(404, "假种不存在");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public LeaveAccountResponse adjustAccount(AdjustLeaveAccountRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        if (request.getChangeMinutes() == 0) {
            throw new BizException(400, "调整分钟数不能为0");
        }
        if (!leaveRepository.existsActiveUser(request.getUserId())) {
            throw new BizException(404, "员工不存在或不可用");
        }
        LeaveTypeRow leaveType = leaveRepository.getActiveLeaveTypeOrThrow(request.getLeaveTypeId());
        // 幂等：同一笔外部业务只允许调整一次，重复请求直接返回原结果
        String sourceTxnKey = trimToNull(request.getSourceTxnKey());
        LeaveAccountTxnRow existingTxn = leaveRepository.getTxnBySourceTxnKey(sourceTxnKey);
        if (existingTxn != null) {
            LeaveAccountRow existingAccount = leaveRepository.getAccountByIdOrThrow(existingTxn.accountId());
            ensureAdjustmentIdempotencyFingerprint(existingTxn, existingAccount, request);
            return toAccountResponse(existingAccount, existingTxn.txnType(), existingTxn.changeMinutes());
        }

        LeaveAccountRow account = leaveRepository.getAccountForUpdate(request.getUserId(), leaveType.id());
        if (account == null) {
            Long accountId = IdGenerator.nextId();
            leaveRepository.insertAccount(accountId, request.getUserId(), leaveType.id(), operatorId);
            account = leaveRepository.getAccountForUpdate(request.getUserId(), leaveType.id());
        }
        int balanceAfter = account.availableMinutes() + request.getChangeMinutes();
        if (balanceAfter < 0) {
            throw new BizException(400, "假期余额不能调整为负数");
        }

        leaveRepository.updateAccountBalance(account.accountId(), balanceAfter, operatorId, account.version());
        leaveRepository.insertAccountTxn(
            IdGenerator.nextId(),
            account.accountId(),
            TXN_TYPE_ADJUST,
            request.getChangeMinutes(),
            balanceAfter,
            SOURCE_TYPE_ADMIN,
            operatorId,
            sourceTxnKey,
            LocalDateTime.now()
        );
        LeaveAccountRow updatedAccount = leaveRepository.getAccountByIdOrThrow(account.accountId());
        return toAccountResponse(updatedAccount, TXN_TYPE_ADJUST, request.getChangeMinutes());
    }

    public List<LeaveTypeResponse> listLeaveTypes() {
        return leaveRepository.listLeaveTypes().stream()
            .filter(row -> !"ANNUAL_DEMO".equals(row.leaveCode()))
            .map(this::toLeaveTypeResponse)
            .toList();
    }

    public List<LeaveAccountResponse> listCurrentEmployeeAccounts() {
        Long userId = CurrentUserHolder.requireUserId();
        return leaveRepository.listUserAccounts(userId).stream()
            .map(row -> toAccountResponse(row, null, null))
            .toList();
    }

    public List<LeaveAccountResponse> listAdminAccounts(Long userId, Long leaveTypeId) {
        return leaveRepository.listAccounts(userId, leaveTypeId).stream()
            .map(row -> toAccountResponse(row, null, null))
            .toList();
    }

    // 提交请假申请前的余额校验，不扣减，只检查是否够用
    public void ensureLeaveRequestBalance(Long userId, Long leaveTypeId, int durationMinutes) {
        LeaveTypeRow leaveType = leaveRepository.getActiveLeaveTypeOrThrow(leaveTypeId);
        // 不扣余额的假种（如调休）跳过校验
        if (BALANCE_MODE_NO_DEDUCT.equals(leaveType.balanceMode())) {
            return;
        }
        if (durationMinutes <= 0) {
            throw new BizException(400, "请假时长必须大于0");
        }
        int availableMinutes = leaveRepository.getAvailableMinutes(userId, leaveTypeId);
        if (availableMinutes < durationMinutes) {
            throw new BizException(400, "假期余额不足");
        }
    }

    // 请假申请提交时冻结余额，防止并发超领
    @Transactional(rollbackFor = Exception.class)
    public void freezeForWorkflowSubmission(Long userId,
                                            Long leaveTypeId,
                                            int durationMinutes,
                                            Long requestId,
                                            Long operatorId) {
        LeaveTypeRow leaveType = leaveRepository.getActiveLeaveTypeOrThrow(leaveTypeId);
        if (BALANCE_MODE_NO_DEDUCT.equals(leaveType.balanceMode())) {
            return;
        }
        ensurePositiveDuration(durationMinutes);
        String sourceTxnKey = workflowFreezeSourceTxnKey(requestId);
        // 幂等：重复提交不重复冻结
        LeaveAccountTxnRow existingTxn = leaveRepository.getTxnBySourceTxnKey(sourceTxnKey);
        if (existingTxn != null) {
            LeaveAccountRow existingAccount = leaveRepository.getAccountByIdOrThrow(existingTxn.accountId());
            ensureWorkflowFreezeIdempotencyFingerprint(
                existingTxn,
                existingAccount,
                userId,
                leaveTypeId,
                durationMinutes,
                requestId
            );
            return;
        }

        // SELECT FOR UPDATE 行锁，防止并发扣减
        LeaveAccountRow account = leaveRepository.getAccountForUpdate(userId, leaveTypeId);
        if (account == null || account.availableMinutes() < durationMinutes) {
            throw new BizException(400, "假期余额不足");
        }

        int availableAfter = account.availableMinutes() - durationMinutes;
        int frozenAfter = account.frozenMinutes() + durationMinutes;
        leaveRepository.updateAccountBalances(account.accountId(), availableAfter, frozenAfter, operatorId, account.version());
        leaveRepository.insertAccountTxn(
            IdGenerator.nextId(),
            account.accountId(),
            TXN_TYPE_FREEZE,
            -durationMinutes,
            availableAfter,
            SOURCE_TYPE_WORKFLOW_REQUEST,
            requestId,
            sourceTxnKey,
            LocalDateTime.now()
        );
    }

    // 审批通过后，将冻结余额正式扣减
    @Transactional(rollbackFor = Exception.class)
    public void consumeFrozenForWorkflowApproval(Long userId,
                                                 Long leaveTypeId,
                                                 int durationMinutes,
                                                 Long requestId,
                                                 Long operatorId) {
        LeaveTypeRow leaveType = leaveRepository.getActiveLeaveTypeOrThrow(leaveTypeId);
        if (BALANCE_MODE_NO_DEDUCT.equals(leaveType.balanceMode())) {
            return;
        }
        ensurePositiveDuration(durationMinutes);
        String sourceTxnKey = workflowDeductSourceTxnKey(requestId);
        LeaveAccountTxnRow existingTxn = leaveRepository.getTxnBySourceTxnKey(sourceTxnKey);
        if (existingTxn != null) {
            LeaveAccountRow existingAccount = leaveRepository.getAccountByIdOrThrow(existingTxn.accountId());
            ensureWorkflowDeductIdempotencyFingerprint(
                existingTxn,
                existingAccount,
                userId,
                leaveTypeId,
                durationMinutes,
                requestId
            );
            return;
        }
        LeaveAccountRow account = leaveRepository.getAccountForUpdate(userId, leaveTypeId);
        if (account == null) {
            throw new BizException(400, "假期余额不足");
        }

        // 优先从冻结余额扣，降级到可用余额（兼容历史无冻结记录的申请）
        LeaveAccountTxnRow freezeTxn = leaveRepository.getTxnBySourceTxnKey(workflowFreezeSourceTxnKey(requestId));
        int balanceAfter;
        if (account.frozenMinutes() >= durationMinutes) {
            balanceAfter = account.availableMinutes();
            leaveRepository.updateAccountBalances(
                account.accountId(),
                account.availableMinutes(),
                account.frozenMinutes() - durationMinutes,
                operatorId,
                account.version()
            );
        } else if (freezeTxn == null && account.availableMinutes() >= durationMinutes) {
            // 历史申请没有冻结记录，直接扣可用余额
            balanceAfter = account.availableMinutes() - durationMinutes;
            leaveRepository.updateAccountBalance(account.accountId(), balanceAfter, operatorId, account.version());
        } else {
            throw new BizException(400, "冻结假期余额不足");
        }
        leaveRepository.insertAccountTxn(
            IdGenerator.nextId(),
            account.accountId(),
            TXN_TYPE_DEDUCT,
            -durationMinutes,
            balanceAfter,
            SOURCE_TYPE_WORKFLOW_REQUEST,
            requestId,
            sourceTxnKey,
            LocalDateTime.now()
        );
    }

    // 请假被驳回/撤回时，释放之前冻结的余额
    @Transactional(rollbackFor = Exception.class)
    public void releaseFrozenForWorkflowTerminal(Long userId,
                                                 Long leaveTypeId,
                                                 int durationMinutes,
                                                 Long requestId,
                                                 String terminalStatus,
                                                 Long operatorId) {
        LeaveTypeRow leaveType = leaveRepository.getActiveLeaveTypeOrThrow(leaveTypeId);
        if (BALANCE_MODE_NO_DEDUCT.equals(leaveType.balanceMode())) {
            return;
        }
        ensurePositiveDuration(durationMinutes);
        // 没有冻结记录说明是历史申请，跳过
        LeaveAccountTxnRow freezeTxn = leaveRepository.getTxnBySourceTxnKey(workflowFreezeSourceTxnKey(requestId));
        if (freezeTxn == null) {
            return;
        }
        String sourceTxnKey = workflowUnfreezeSourceTxnKey(requestId, terminalStatus);
        LeaveAccountTxnRow existingTxn = leaveRepository.getTxnBySourceTxnKey(sourceTxnKey);
        if (existingTxn != null) {
            LeaveAccountRow existingAccount = leaveRepository.getAccountByIdOrThrow(existingTxn.accountId());
            ensureWorkflowUnfreezeIdempotencyFingerprint(
                existingTxn,
                existingAccount,
                userId,
                leaveTypeId,
                durationMinutes,
                requestId
            );
            return;
        }

        LeaveAccountRow account = leaveRepository.getAccountForUpdate(userId, leaveTypeId);
        if (account == null || account.frozenMinutes() < durationMinutes) {
            throw new BizException(400, "冻结假期余额不足");
        }

        int availableAfter = account.availableMinutes() + durationMinutes;
        int frozenAfter = account.frozenMinutes() - durationMinutes;
        leaveRepository.updateAccountBalances(account.accountId(), availableAfter, frozenAfter, operatorId, account.version());
        leaveRepository.insertAccountTxn(
            IdGenerator.nextId(),
            account.accountId(),
            TXN_TYPE_UNFREEZE,
            durationMinutes,
            availableAfter,
            SOURCE_TYPE_WORKFLOW_REQUEST,
            requestId,
            sourceTxnKey,
            LocalDateTime.now()
        );
    }

    public List<Map<String, Object>> getLeaveBalances(Long userId) {
        return leaveRepository.listUserAccounts(userId).stream()
            .map(row -> Map.<String, Object>of(
                "leaveTypeId", row.leaveTypeId(),
                "leaveCode", row.leaveCode(),
                "leaveName", row.leaveName(),
                "balanceMode", row.balanceMode(),
                "availableMinutes", row.availableMinutes(),
                "frozenMinutes", row.frozenMinutes(),
                "status", row.status()
            ))
            .toList();
    }

    public int getAvailableMinutes(Long userId, Long leaveTypeId) {
        return leaveRepository.getAvailableMinutes(userId, leaveTypeId);
    }

    public List<ApprovedLeavePeriod> listApprovedLeavePeriodsOnDate(Long userId, LocalDate workDate) {
        return leaveRepository.listApprovedLeavePeriodsOnDate(userId, workDate);
    }

    private LeaveTypeResponse toLeaveTypeResponse(LeaveTypeRow row) {
        LeaveTypeResponse response = new LeaveTypeResponse();
        response.setLeaveTypeId(row.id());
        response.setLeaveCode(row.leaveCode());
        response.setLeaveName(row.leaveName());
        response.setBalanceMode(row.balanceMode());
        response.setStatus(row.status());
        response.setCreatedAt(row.createdAt());
        response.setUpdatedAt(row.updatedAt());
        return response;
    }

    private LeaveAccountResponse toAccountResponse(LeaveAccountRow row,
                                                   String lastTxnType,
                                                   Integer lastChangeMinutes) {
        LeaveAccountResponse response = new LeaveAccountResponse();
        response.setAccountId(row.accountId());
        response.setUserId(row.userId());
        response.setLeaveTypeId(row.leaveTypeId());
        response.setLeaveCode(row.leaveCode());
        response.setLeaveName(row.leaveName());
        response.setBalanceMode(row.balanceMode());
        response.setAvailableMinutes(row.availableMinutes());
        response.setFrozenMinutes(row.frozenMinutes());
        response.setStatus(row.status());
        response.setLastTxnType(lastTxnType);
        response.setLastChangeMinutes(lastChangeMinutes);
        return response;
    }

    private String normalizeBalanceMode(String balanceMode) {
        String normalized = balanceMode.trim().toUpperCase(Locale.ROOT);
        if (!BALANCE_MODE_DEDUCT.equals(normalized) && !BALANCE_MODE_NO_DEDUCT.equals(normalized)) {
            throw new BizException(400, "余额模式仅支持 DEDUCT/NO_DEDUCT");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUS_ACTIVE.equals(normalized) && !STATUS_INACTIVE.equals(normalized)) {
            throw new BizException(400, "假种状态仅支持 ACTIVE/INACTIVE");
        }
        return normalized;
    }

    private void ensureAdjustmentIdempotencyFingerprint(LeaveAccountTxnRow existingTxn,
                                                        LeaveAccountRow existingAccount,
                                                        AdjustLeaveAccountRequest request) {
        boolean sameFingerprint = TXN_TYPE_ADJUST.equals(existingTxn.txnType())
            && SOURCE_TYPE_ADMIN.equals(existingTxn.sourceType())
            && existingTxn.changeMinutes().equals(request.getChangeMinutes())
            && existingAccount.userId().equals(request.getUserId())
            && existingAccount.leaveTypeId().equals(request.getLeaveTypeId());
        if (!sameFingerprint) {
            throw new BizException(409, "幂等键已被不同余额调整操作使用");
        }
    }

    private void ensureWorkflowDeductIdempotencyFingerprint(LeaveAccountTxnRow existingTxn,
                                                            LeaveAccountRow existingAccount,
                                                            Long userId,
                                                            Long leaveTypeId,
                                                            int durationMinutes,
                                                            Long requestId) {
        boolean sameFingerprint = TXN_TYPE_DEDUCT.equals(existingTxn.txnType())
            && SOURCE_TYPE_WORKFLOW_REQUEST.equals(existingTxn.sourceType())
            && requestId.equals(existingTxn.sourceId())
            && existingTxn.changeMinutes().equals(-durationMinutes)
            && existingAccount.userId().equals(userId)
            && existingAccount.leaveTypeId().equals(leaveTypeId);
        if (!sameFingerprint) {
            throw new BizException(409, "幂等键已被不同假期扣减操作使用");
        }
    }

    private void ensureWorkflowFreezeIdempotencyFingerprint(LeaveAccountTxnRow existingTxn,
                                                            LeaveAccountRow existingAccount,
                                                            Long userId,
                                                            Long leaveTypeId,
                                                            int durationMinutes,
                                                            Long requestId) {
        boolean sameFingerprint = TXN_TYPE_FREEZE.equals(existingTxn.txnType())
            && SOURCE_TYPE_WORKFLOW_REQUEST.equals(existingTxn.sourceType())
            && requestId.equals(existingTxn.sourceId())
            && existingTxn.changeMinutes().equals(-durationMinutes)
            && existingAccount.userId().equals(userId)
            && existingAccount.leaveTypeId().equals(leaveTypeId);
        if (!sameFingerprint) {
            throw new BizException(409, "幂等键已被不同假期冻结操作使用");
        }
    }

    private void ensureWorkflowUnfreezeIdempotencyFingerprint(LeaveAccountTxnRow existingTxn,
                                                              LeaveAccountRow existingAccount,
                                                              Long userId,
                                                              Long leaveTypeId,
                                                              int durationMinutes,
                                                              Long requestId) {
        boolean sameFingerprint = TXN_TYPE_UNFREEZE.equals(existingTxn.txnType())
            && SOURCE_TYPE_WORKFLOW_REQUEST.equals(existingTxn.sourceType())
            && requestId.equals(existingTxn.sourceId())
            && existingTxn.changeMinutes().equals(durationMinutes)
            && existingAccount.userId().equals(userId)
            && existingAccount.leaveTypeId().equals(leaveTypeId);
        if (!sameFingerprint) {
            throw new BizException(409, "幂等键已被不同假期解冻操作使用");
        }
    }

    private void ensurePositiveDuration(int durationMinutes) {
        if (durationMinutes <= 0) {
            throw new BizException(400, "请假时长必须大于0");
        }
    }

    private String workflowDeductSourceTxnKey(Long requestId) {
        return SOURCE_TYPE_WORKFLOW_REQUEST + ":" + requestId;
    }

    private String workflowFreezeSourceTxnKey(Long requestId) {
        return SOURCE_TYPE_WORKFLOW_REQUEST + ":" + requestId + ":FREEZE";
    }

    private String workflowUnfreezeSourceTxnKey(Long requestId, String terminalStatus) {
        String status = StringUtils.hasText(terminalStatus) ? terminalStatus.trim().toUpperCase(Locale.ROOT) : "TERMINAL";
        return SOURCE_TYPE_WORKFLOW_REQUEST + ":" + requestId + ":UNFREEZE:" + status;
    }

}
