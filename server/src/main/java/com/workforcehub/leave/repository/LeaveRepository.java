package com.workforcehub.leave.repository;

import com.workforcehub.common.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * module-leave 第一版 JDBC 仓储。
 */
@Repository
public class LeaveRepository {

    private final JdbcTemplate jdbcTemplate;

    public LeaveRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsLeaveCode(String leaveCode) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM lv_type
            WHERE leave_code = ? AND is_deleted = 0
            """, Long.class, leaveCode);
        return count != null && count > 0;
    }

    public void insertLeaveType(Long leaveTypeId,
                                String leaveCode,
                                String leaveName,
                                String balanceMode,
                                String status,
                                Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO lv_type
                (id, leave_code, leave_name, balance_mode, status, created_by, updated_by)
            VALUES
                (?, ?, ?, ?, ?, ?, ?)
            """,
            leaveTypeId,
            leaveCode,
            leaveName,
            balanceMode,
            status,
            operatorId,
            operatorId
        );
    }

    public LeaveTypeRow getLeaveTypeOrThrow(Long leaveTypeId) {
        List<LeaveTypeRow> rows = jdbcTemplate.query("""
            SELECT id, leave_code, leave_name, balance_mode, status, created_at, updated_at
            FROM lv_type
            WHERE id = ? AND is_deleted = 0
            LIMIT 1
            """,
            (rs, rowNum) -> new LeaveTypeRow(
                rs.getLong("id"),
                rs.getString("leave_code"),
                rs.getString("leave_name"),
                rs.getString("balance_mode"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
            ),
            leaveTypeId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "假种不存在");
        }
        return rows.get(0);
    }

    public LeaveTypeRow getActiveLeaveTypeOrThrow(Long leaveTypeId) {
        LeaveTypeRow row = getLeaveTypeOrThrow(leaveTypeId);
        if (!"ACTIVE".equals(row.status())) {
            throw new BizException(409, "假种已停用");
        }
        return row;
    }

    public boolean existsActiveUser(Long userId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM org_user
            WHERE id = ?
              AND status = 'ACTIVE'
              AND employment_status <> 'OFFBOARDED'
              AND is_deleted = 0
            """, Long.class, userId);
        return count != null && count > 0;
    }

    public LeaveAccountTxnRow getTxnBySourceTxnKey(String sourceTxnKey) {
        if (!StringUtils.hasText(sourceTxnKey)) {
            return null;
        }
        List<LeaveAccountTxnRow> rows = jdbcTemplate.query("""
            SELECT id, account_id, txn_type, change_minutes, balance_after,
                   source_type, source_id, source_txn_key
            FROM lv_account_txn
            WHERE source_txn_key = ?
            LIMIT 1
            """,
            (rs, rowNum) -> new LeaveAccountTxnRow(
                rs.getLong("id"),
                rs.getLong("account_id"),
                rs.getString("txn_type"),
                rs.getInt("change_minutes"),
                rs.getInt("balance_after"),
                rs.getString("source_type"),
                rs.getObject("source_id", Long.class),
                rs.getString("source_txn_key")
            ),
            sourceTxnKey
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public LeaveAccountRow getAccountByIdOrThrow(Long accountId) {
        List<LeaveAccountRow> rows = jdbcTemplate.query(accountSelectSql() + """
            WHERE la.id = ?
            LIMIT 1
            """,
            this::mapAccount,
            accountId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "假期账户不存在");
        }
        return rows.get(0);
    }

    public LeaveAccountRow getAccountForUpdate(Long userId, Long leaveTypeId) {
        List<LeaveAccountRow> rows = jdbcTemplate.query(accountSelectSql() + """
            WHERE la.user_id = ?
              AND la.leave_type_id = ?
              AND la.status = 'ACTIVE'
            LIMIT 1
            FOR UPDATE
            """,
            this::mapAccount,
            userId,
            leaveTypeId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void insertAccount(Long accountId, Long userId, Long leaveTypeId, Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO lv_account
                (id, user_id, leave_type_id, available_minutes, frozen_minutes,
                 version, status, created_by, updated_by)
            VALUES
                (?, ?, ?, 0, 0, 0, 'ACTIVE', ?, ?)
            """,
            accountId,
            userId,
            leaveTypeId,
            operatorId,
            operatorId
        );
    }

    public void updateAccountBalance(Long accountId, int availableMinutes, Long operatorId) {
        jdbcTemplate.update("""
            UPDATE lv_account
            SET available_minutes = ?,
                version = version + 1,
                updated_by = ?
            WHERE id = ?
              AND status = 'ACTIVE'
            """,
            availableMinutes,
            operatorId,
            accountId
        );
    }

    public void insertAccountTxn(Long txnId,
                                 Long accountId,
                                 String txnType,
                                 int changeMinutes,
                                 int balanceAfter,
                                 String sourceType,
                                 Long sourceId,
                                 String sourceTxnKey,
                                 LocalDateTime effectiveAt) {
        jdbcTemplate.update("""
            INSERT INTO lv_account_txn
                (id, account_id, txn_type, change_minutes, balance_after,
                 source_type, source_id, source_txn_key, effective_at)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            txnId,
            accountId,
            txnType,
            changeMinutes,
            balanceAfter,
            sourceType,
            sourceId,
            sourceTxnKey,
            effectiveAt
        );
    }

    public List<LeaveAccountRow> listUserAccounts(Long userId) {
        return jdbcTemplate.query(accountSelectSql() + """
            WHERE la.user_id = ?
              AND la.status = 'ACTIVE'
              AND lt.is_deleted = 0
            ORDER BY lt.leave_code ASC, la.id ASC
            """,
            this::mapAccount,
            userId
        );
    }

    public int getAvailableMinutes(Long userId, Long leaveTypeId) {
        Integer minutes = jdbcTemplate.queryForObject("""
            SELECT COALESCE((
                SELECT la.available_minutes
                FROM lv_account la
                JOIN lv_type lt ON lt.id = la.leave_type_id
                WHERE la.user_id = ?
                  AND la.leave_type_id = ?
                  AND la.status = 'ACTIVE'
                  AND lt.status = 'ACTIVE'
                  AND lt.is_deleted = 0
                LIMIT 1
            ), 0)
            """, Integer.class, userId, leaveTypeId);
        return minutes == null ? 0 : minutes;
    }

    private String accountSelectSql() {
        return """
            SELECT la.id AS account_id,
                   la.user_id,
                   la.leave_type_id,
                   lt.leave_code,
                   lt.leave_name,
                   lt.balance_mode,
                   la.available_minutes,
                   la.frozen_minutes,
                   la.version,
                   la.status
            FROM lv_account la
            JOIN lv_type lt ON lt.id = la.leave_type_id
            """;
    }

    private LeaveAccountRow mapAccount(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new LeaveAccountRow(
            rs.getLong("account_id"),
            rs.getLong("user_id"),
            rs.getLong("leave_type_id"),
            rs.getString("leave_code"),
            rs.getString("leave_name"),
            rs.getString("balance_mode"),
            rs.getInt("available_minutes"),
            rs.getInt("frozen_minutes"),
            rs.getInt("version"),
            rs.getString("status")
        );
    }
}
