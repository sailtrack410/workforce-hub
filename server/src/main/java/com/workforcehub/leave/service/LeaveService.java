package com.workforcehub.leave.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.leave.dto.AdjustLeaveAccountRequest;
import com.workforcehub.leave.dto.CreateLeaveTypeRequest;
import com.workforcehub.leave.dto.LeaveAccountResponse;
import com.workforcehub.leave.dto.LeaveTypeResponse;
import com.workforcehub.leave.repository.LeaveAccountRow;
import com.workforcehub.leave.repository.LeaveAccountTxnRow;
import com.workforcehub.leave.repository.LeaveRepository;
import com.workforcehub.leave.repository.LeaveTypeRow;
import com.workforcehub.spi.LeaveQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * module-leave 第一版假期账户服务。
 */
@Service
public class LeaveService implements LeaveQueryService {

    private static final String BALANCE_MODE_DEDUCT = "DEDUCT";
    private static final String BALANCE_MODE_NO_DEDUCT = "NO_DEDUCT";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final String TXN_TYPE_ADJUST = "ADJUST";
    private static final String TXN_TYPE_DEDUCT = "DEDUCT";
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

        Long leaveTypeId = IdWorker.getId();
        leaveRepository.insertLeaveType(leaveTypeId, leaveCode, leaveName, balanceMode, status, operatorId);
        return toLeaveTypeResponse(leaveRepository.getLeaveTypeOrThrow(leaveTypeId));
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
        String sourceTxnKey = trimToNull(request.getSourceTxnKey());
        LeaveAccountTxnRow existingTxn = leaveRepository.getTxnBySourceTxnKey(sourceTxnKey);
        if (existingTxn != null) {
            LeaveAccountRow existingAccount = leaveRepository.getAccountByIdOrThrow(existingTxn.accountId());
            return toAccountResponse(existingAccount, existingTxn.txnType(), existingTxn.changeMinutes());
        }

        LeaveAccountRow account = leaveRepository.getAccountForUpdate(request.getUserId(), leaveType.id());
        if (account == null) {
            Long accountId = IdWorker.getId();
            leaveRepository.insertAccount(accountId, request.getUserId(), leaveType.id(), operatorId);
            account = leaveRepository.getAccountForUpdate(request.getUserId(), leaveType.id());
        }
        int balanceAfter = account.availableMinutes() + request.getChangeMinutes();
        if (balanceAfter < 0) {
            throw new BizException(400, "假期余额不能调整为负数");
        }

        leaveRepository.updateAccountBalance(account.accountId(), balanceAfter, operatorId);
        leaveRepository.insertAccountTxn(
            IdWorker.getId(),
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

    public List<LeaveAccountResponse> listCurrentEmployeeAccounts() {
        Long userId = CurrentUserHolder.requireUserId();
        return leaveRepository.listUserAccounts(userId).stream()
            .map(row -> toAccountResponse(row, null, null))
            .toList();
    }

    public void ensureLeaveRequestBalance(Long userId, Long leaveTypeId, int durationMinutes) {
        LeaveTypeRow leaveType = leaveRepository.getActiveLeaveTypeOrThrow(leaveTypeId);
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

    @Transactional(rollbackFor = Exception.class)
    public void deductForWorkflowApproval(Long userId,
                                          Long leaveTypeId,
                                          int durationMinutes,
                                          Long requestId,
                                          Long operatorId) {
        LeaveTypeRow leaveType = leaveRepository.getActiveLeaveTypeOrThrow(leaveTypeId);
        if (BALANCE_MODE_NO_DEDUCT.equals(leaveType.balanceMode())) {
            return;
        }
        if (durationMinutes <= 0) {
            throw new BizException(400, "请假时长必须大于0");
        }
        String sourceTxnKey = SOURCE_TYPE_WORKFLOW_REQUEST + ":" + requestId;
        LeaveAccountTxnRow existingTxn = leaveRepository.getTxnBySourceTxnKey(sourceTxnKey);
        if (existingTxn != null) {
            return;
        }
        LeaveAccountRow account = leaveRepository.getAccountForUpdate(userId, leaveTypeId);
        if (account == null || account.availableMinutes() < durationMinutes) {
            throw new BizException(400, "假期余额不足");
        }

        int balanceAfter = account.availableMinutes() - durationMinutes;
        leaveRepository.updateAccountBalance(account.accountId(), balanceAfter, operatorId);
        leaveRepository.insertAccountTxn(
            IdWorker.getId(),
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

    @Override
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

    @Override
    public int getAvailableMinutes(Long userId, Long leaveTypeId) {
        return leaveRepository.getAvailableMinutes(userId, leaveTypeId);
    }

    @Override
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

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
