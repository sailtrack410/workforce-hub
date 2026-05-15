package com.workforcehub.file.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.file.config.FileStorageProperties;
import com.workforcehub.file.dto.CreateQuotaPolicyRequest;
import com.workforcehub.file.dto.QuotaPolicyResponse;
import com.workforcehub.file.dto.QuotaResponse;
import com.workforcehub.file.dto.StorageSummaryResponse;
import com.workforcehub.file.dto.UpdateQuotaPolicyRequest;
import com.workforcehub.file.storage.StorageProvider;
import com.workforcehub.file.storage.StorageProviderRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件中心配额策略服务。
 */
@Service
public class FileQuotaPolicyService {

    private static final long DEFAULT_PERSONAL_QUOTA_BYTES = 10L * 1024L * 1024L * 1024L;
    private static final long DEFAULT_GLOBAL_QUOTA_BYTES = 400L * 1024L * 1024L * 1024L;
    private static final long DEFAULT_CACHE_QUOTA_BYTES = 50L * 1024L * 1024L * 1024L;

    private final JdbcTemplate jdbcTemplate;
    private final StorageProviderRegistry storageProviderRegistry;
    private final FileStorageProperties fileStorageProperties;
    private final StorageUsageLedgerService storageUsageLedgerService;

    public FileQuotaPolicyService(JdbcTemplate jdbcTemplate,
                                  StorageProviderRegistry storageProviderRegistry,
                                  FileStorageProperties fileStorageProperties,
                                  StorageUsageLedgerService storageUsageLedgerService) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageProviderRegistry = storageProviderRegistry;
        this.fileStorageProperties = fileStorageProperties;
        this.storageUsageLedgerService = storageUsageLedgerService;
    }

    public QuotaResponse getEmployeeQuota() {
        Long userId = CurrentUserHolder.requireUserId();
        long personalQuota = resolveQuota("USER", userId, resolveQuota("USER_DEFAULT", null, DEFAULT_PERSONAL_QUOTA_BYTES));
        long personalUsed = storageUsageLedgerService.usedBytes("USER", userId);
        long globalQuota = resolveQuota("GLOBAL", null, DEFAULT_GLOBAL_QUOTA_BYTES);
        long globalUsed = storageUsageLedgerService.usedBytes("GLOBAL", null);
        long cacheQuota = resolveQuota("TEMP_CACHE", null, DEFAULT_CACHE_QUOTA_BYTES);
        long cacheUsed = storageUsageLedgerService.cacheUsedBytes("TEMP_CACHE", null);

        QuotaResponse response = new QuotaResponse();
        response.setPersonalQuotaBytes(personalQuota);
        response.setPersonalUsedBytes(personalUsed);
        response.setPersonalUsagePercent(ratio(personalUsed, personalQuota));
        response.setOrgPoolQuotaBytes(globalQuota);
        response.setOrgPoolUsedBytes(globalUsed);
        response.setCacheQuotaBytes(cacheQuota);
        response.setCacheUsedBytes(cacheUsed);
        response.setUploadWritable(personalUsed < personalQuota && globalUsed < globalQuota);
        response.setBlockReason(resolveBlockReason(personalUsed, personalQuota, globalUsed, globalQuota));
        return response;
    }

    public StorageSummaryResponse getStorageSummary() {
        CurrentUserHolder.requireUserId();
        StorageProvider provider = storageProviderRegistry.require(fileStorageProperties.getStorageProvider());
        long globalQuota = resolveQuota("GLOBAL", null, DEFAULT_GLOBAL_QUOTA_BYTES);
        long globalUsed = storageUsageLedgerService.usedBytes("GLOBAL", null);
        long cacheQuota = resolveQuota("TEMP_CACHE", null, DEFAULT_CACHE_QUOTA_BYTES);
        long cacheUsed = storageUsageLedgerService.cacheUsedBytes("TEMP_CACHE", null);

        StorageSummaryResponse response = new StorageSummaryResponse();
        response.setOrgPoolQuotaBytes(globalQuota);
        response.setOrgPoolUsedBytes(globalUsed);
        response.setOrgPoolWarningThreshold(BigDecimal.valueOf(0.8));
        response.setOrgPoolBlockThreshold(BigDecimal.ONE);
        response.setCacheQuotaBytes(cacheQuota);
        response.setCacheUsedBytes(cacheUsed);
        response.setBackendFreeBytes(provider.usableFreeBytes());
        response.setBackendWritable(provider.usableFreeBytes() > 0 && globalUsed < globalQuota);
        response.setWriteProtectReason(globalUsed >= globalQuota ? "企业文件总空间不足" : null);
        response.setFileObjectCount(countLong("SELECT COUNT(*) FROM sys_file_object WHERE is_deleted = 0"));
        response.setAttachmentRefCount(countLong("SELECT COUNT(*) FROM sys_attachment_ref WHERE is_deleted = 0"));
        response.setRecycleBinCount(countLong("SELECT COUNT(*) FROM sys_attachment_ref WHERE deleted_at IS NOT NULL AND is_deleted = 0"));
        response.setPendingCleanupObjectCount(countLong("SELECT COUNT(*) FROM sys_file_object WHERE cleanup_after IS NOT NULL AND is_deleted = 0"));
        return response;
    }

    public List<QuotaPolicyResponse> listQuotaPolicies(String scopeType) {
        CurrentUserHolder.requireUserId();
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT id, policy_code, policy_name, scope_type, scope_id, quota_bytes,
                   cache_quota_bytes, warning_threshold, block_threshold, status, created_at, updated_at
            FROM sys_storage_quota_policy
            WHERE is_deleted = 0
            """);
        if (StringUtils.hasText(scopeType)) {
            validateScopeType(scopeType);
            sql.append(" AND scope_type = ?");
            args.add(scopeType);
        }
        sql.append(" ORDER BY updated_at DESC, id DESC");
        return jdbcTemplate.query(sql.toString(), this::mapQuotaPolicy, args.toArray());
    }

    @Transactional(rollbackFor = Exception.class)
    public QuotaPolicyResponse createQuotaPolicy(CreateQuotaPolicyRequest request) {
        Long adminId = CurrentUserHolder.requireUserId();
        validateQuotaPolicyPayload(
            request.getScopeType(),
            request.getScopeId(),
            request.getQuotaBytes(),
            request.getWarningThreshold(),
            request.getBlockThreshold(),
            request.getStatus()
        );
        ensureQuotaPolicyCodeNotExists(request.getPolicyCode());
        ensureQuotaPolicyScopeNotExists(request.getScopeType(), request.getScopeId(), null);
        Long policyId = IdWorker.getId();
        jdbcTemplate.update("""
            INSERT INTO sys_storage_quota_policy
                (id, policy_code, policy_name, scope_type, scope_id, quota_bytes,
                 cache_quota_bytes, warning_threshold, block_threshold, status, created_by)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            policyId,
            request.getPolicyCode(),
            request.getPolicyName(),
            request.getScopeType(),
            request.getScopeId(),
            request.getQuotaBytes(),
            request.getCacheQuotaBytes(),
            request.getWarningThreshold(),
            request.getBlockThreshold(),
            request.getStatus(),
            adminId
        );
        audit("ADMIN", adminId, "FILE_QUOTA_POLICY_CREATED", "STORAGE_QUOTA_POLICY", policyId, "SUCCESS",
            "{\"scopeType\":\"%s\",\"scopeId\":%s}".formatted(request.getScopeType(), request.getScopeId()));
        return getQuotaPolicyOrThrow(policyId);
    }

    @Transactional(rollbackFor = Exception.class)
    public QuotaPolicyResponse updateQuotaPolicy(Long policyId, UpdateQuotaPolicyRequest request) {
        Long adminId = CurrentUserHolder.requireUserId();
        QuotaPolicyRow existing = getQuotaPolicyRowOrThrow(policyId);
        validateQuotaPolicyPayload(
            existing.scopeType(),
            existing.scopeId(),
            request.getQuotaBytes(),
            request.getWarningThreshold(),
            request.getBlockThreshold(),
            request.getStatus()
        );
        jdbcTemplate.update("""
            UPDATE sys_storage_quota_policy
            SET policy_name = ?,
                quota_bytes = ?,
                cache_quota_bytes = ?,
                warning_threshold = ?,
                block_threshold = ?,
                status = ?,
                updated_by = ?,
                updated_at = NOW()
            WHERE id = ? AND is_deleted = 0
            """,
            request.getPolicyName(),
            request.getQuotaBytes(),
            request.getCacheQuotaBytes(),
            request.getWarningThreshold(),
            request.getBlockThreshold(),
            request.getStatus(),
            adminId,
            policyId
        );
        audit("ADMIN", adminId, "FILE_QUOTA_POLICY_UPDATED", "STORAGE_QUOTA_POLICY", policyId, "SUCCESS",
            "{\"scopeType\":\"%s\",\"scopeId\":%s}".formatted(existing.scopeType(), existing.scopeId()));
        return getQuotaPolicyOrThrow(policyId);
    }

    public void ensurePersonalQuota(Long userId, Long incomingBytes) {
        long quota = resolveQuota("USER", userId, resolveQuota("USER_DEFAULT", null, DEFAULT_PERSONAL_QUOTA_BYTES));
        long used = storageUsageLedgerService.usedBytes("USER", userId);
        if (used + incomingBytes > quota) {
            throw new BizException(507, "个人云空间不足，请清理文件或联系管理员扩容");
        }
    }

    public void ensureGlobalQuota(Long incomingBytes) {
        long quota = resolveQuota("GLOBAL", null, DEFAULT_GLOBAL_QUOTA_BYTES);
        long used = storageUsageLedgerService.usedBytes("GLOBAL", null);
        if (used + incomingBytes > quota) {
            throw new BizException(507, "企业文件总空间不足，请联系系统管理员扩容");
        }
    }

    private long resolveQuota(String scopeType, Long scopeId, long defaultValue) {
        List<Long> values = jdbcTemplate.query("""
            SELECT quota_bytes FROM sys_storage_quota_policy
            WHERE scope_type = ? AND ((scope_id IS NULL AND ? IS NULL) OR scope_id = ?)
              AND status = 'ENABLED' AND is_deleted = 0
            ORDER BY updated_at DESC
            LIMIT 1
            """,
            (rs, rowNum) -> rs.getLong("quota_bytes"),
            scopeType,
            scopeId,
            scopeId
        );
        return values.isEmpty() ? defaultValue : values.get(0);
    }

    private void validateQuotaPolicyPayload(String scopeType,
                                            Long scopeId,
                                            Long quotaBytes,
                                            BigDecimal warningThreshold,
                                            BigDecimal blockThreshold,
                                            String status) {
        validateScopeType(scopeType);
        if (requiresScopeId(scopeType) && scopeId == null) {
            throw new BizException(400, "当前作用域必须指定 scopeId");
        }
        if (!requiresScopeId(scopeType) && scopeId != null) {
            throw new BizException(400, "当前作用域不应指定 scopeId");
        }
        if (!List.of("ENABLED", "DISABLED").contains(status)) {
            throw new BizException(400, "策略状态仅支持 ENABLED 或 DISABLED");
        }
        if (warningThreshold.compareTo(blockThreshold) >= 0) {
            throw new BizException(400, "预警阈值必须小于阻断阈值");
        }
        long usedBytes = storageUsageLedgerService.usedBytes(scopeType, scopeId);
        if (quotaBytes < usedBytes) {
            throw new BizException(409, "配额不能低于当前已用量");
        }
    }

    private void validateScopeType(String scopeType) {
        if (!List.of("GLOBAL", "USER_DEFAULT", "USER", "DEPARTMENT", "ROLE", "GROUP", "KNOWLEDGE_SPACE", "TEMP_CACHE")
            .contains(scopeType)) {
            throw new BizException(400, "不支持的配额作用域类型");
        }
    }

    private void ensureQuotaPolicyScopeNotExists(String scopeType, Long scopeId, Long exceptPolicyId) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_storage_quota_policy
            WHERE scope_type = ?
              AND ((scope_id IS NULL AND ? IS NULL) OR scope_id = ?)
              AND (? IS NULL OR id <> ?)
              AND is_deleted = 0
            """,
            Integer.class,
            scopeType,
            scopeId,
            scopeId,
            exceptPolicyId,
            exceptPolicyId
        );
        if (count != null && count > 0) {
            throw new BizException(409, "同一作用域已存在配额策略");
        }
    }

    private void ensureQuotaPolicyCodeNotExists(String policyCode) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_storage_quota_policy
            WHERE policy_code = ? AND is_deleted = 0
            """,
            Integer.class,
            policyCode
        );
        if (count != null && count > 0) {
            throw new BizException(409, "配额策略编码已存在");
        }
    }

    private boolean requiresScopeId(String scopeType) {
        return List.of("USER", "DEPARTMENT", "ROLE", "GROUP", "KNOWLEDGE_SPACE").contains(scopeType);
    }

    private QuotaPolicyResponse getQuotaPolicyOrThrow(Long policyId) {
        QuotaPolicyRow row = getQuotaPolicyRowOrThrow(policyId);
        return toQuotaPolicyResponse(row);
    }

    private QuotaPolicyRow getQuotaPolicyRowOrThrow(Long policyId) {
        List<QuotaPolicyRow> rows = jdbcTemplate.query("""
            SELECT id, policy_code, policy_name, scope_type, scope_id, quota_bytes,
                   cache_quota_bytes, warning_threshold, block_threshold, status, created_at, updated_at
            FROM sys_storage_quota_policy
            WHERE id = ? AND is_deleted = 0
            """,
            this::mapQuotaPolicyRow,
            policyId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "配额策略不存在");
        }
        return rows.get(0);
    }

    private QuotaPolicyResponse mapQuotaPolicy(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return toQuotaPolicyResponse(mapQuotaPolicyRow(rs, rowNum));
    }

    private QuotaPolicyRow mapQuotaPolicyRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new QuotaPolicyRow(
            rs.getLong("id"),
            rs.getString("policy_code"),
            rs.getString("policy_name"),
            rs.getString("scope_type"),
            rs.getObject("scope_id", Long.class),
            rs.getLong("quota_bytes"),
            rs.getObject("cache_quota_bytes", Long.class),
            rs.getBigDecimal("warning_threshold"),
            rs.getBigDecimal("block_threshold"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private QuotaPolicyResponse toQuotaPolicyResponse(QuotaPolicyRow row) {
        QuotaPolicyResponse response = new QuotaPolicyResponse();
        response.setPolicyId(row.id());
        response.setPolicyCode(row.policyCode());
        response.setPolicyName(row.policyName());
        response.setScopeType(row.scopeType());
        response.setScopeId(row.scopeId());
        response.setQuotaBytes(row.quotaBytes());
        response.setCacheQuotaBytes(row.cacheQuotaBytes());
        response.setWarningThreshold(row.warningThreshold());
        response.setBlockThreshold(row.blockThreshold());
        response.setStatus(row.status());
        response.setCreatedAt(row.createdAt());
        response.setUpdatedAt(row.updatedAt());
        return response;
    }

    private BigDecimal ratio(long used, long quota) {
        if (quota <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(used)
            .divide(BigDecimal.valueOf(quota), 4, RoundingMode.HALF_UP);
    }

    private String resolveBlockReason(long personalUsed, long personalQuota, long globalUsed, long globalQuota) {
        if (personalUsed >= personalQuota) {
            return "个人云空间不足，请清理文件或联系管理员扩容";
        }
        if (globalUsed >= globalQuota) {
            return "企业文件总空间不足，请联系系统管理员扩容";
        }
        return null;
    }

    private long countLong(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }

    private void audit(String operatorType, Long operatorId, String actionType,
                       String targetType, Long targetId, String result, String detailJson) {
        jdbcTemplate.update("""
            INSERT INTO sys_audit_log
                (id, operator_type, operator_id, action_type, target_type, target_id, result, detail_json)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON))
            """,
            IdWorker.getId(),
            operatorType,
            operatorId,
            actionType,
            targetType,
            targetId,
            result,
            detailJson
        );
    }

    private record QuotaPolicyRow(
        Long id,
        String policyCode,
        String policyName,
        String scopeType,
        Long scopeId,
        Long quotaBytes,
        Long cacheQuotaBytes,
        BigDecimal warningThreshold,
        BigDecimal blockThreshold,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}
}
