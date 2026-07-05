package com.workforcehub.file.service;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.im.repository.ImRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class FileUploadRules {

    private final StorageUsageLedgerService storageUsageLedgerService;
    private final FileQuotaPolicyService fileQuotaPolicyService;
    private final ImRepository imRepository;
    private final JdbcTemplate jdbcTemplate;

    public FileUploadRules(StorageUsageLedgerService storageUsageLedgerService,
                           FileQuotaPolicyService fileQuotaPolicyService,
                           ImRepository imRepository,
                           JdbcTemplate jdbcTemplate) {
        this.storageUsageLedgerService = storageUsageLedgerService;
        this.fileQuotaPolicyService = fileQuotaPolicyService;
        this.imRepository = imRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    // 员工端文件归属校验：限制 ownerType 白名单，群文件额外校验群成员身份
    public void validateEmployeeOwner(String ownerType, Long ownerId) {
        if (!"PERSONAL_FILE".equals(ownerType)
            && !"USER_AVATAR".equals(ownerType)
            && !"IM_MESSAGE_PENDING".equals(ownerType)
            && !"IM_GROUP_FILE".equals(ownerType)) {
            throw new BizException(400, "员工端不支持该文件归属");
        }
        if ("IM_GROUP_FILE".equals(ownerType)) {
            Long userId = CurrentUserHolder.requireUserId();
            if (ownerId == null) {
                throw new BizException(400, "群文件必须指定群聊");
            }
            if (!imRepository.existsActiveUserMembership(ownerId, userId)) {
                throw new BizException(403, "无权访问该群聊");
            }
            return;
        }
        if (ownerId != null) {
            throw new BizException(400, "员工端文件不应指定业务归属ID");
        }
    }

    public void validateAdminOwner(String ownerType, Long ownerId) {
        if (!"ADMIN_FILE".equals(ownerType) && !"RAGFLOW_DOCUMENT".equals(ownerType)) {
            throw new BizException(400, "后台第一版仅支持后台治理文件");
        }
        if ("RAGFLOW_DOCUMENT".equals(ownerType)) {
            if (ownerId == null) {
                throw new BizException(400, "知识库文件必须指定知识库");
            }
            return;
        }
        if (ownerId != null) {
            throw new BizException(400, "后台治理文件不应指定业务归属ID");
        }
    }

    public void validateEmployeeFolder(String ownerType, Long userId, Long folderId) {
        if (folderId == null) {
            return;
        }
        if (!"PERSONAL_FILE".equals(ownerType)) {
            throw new BizException(400, "仅个人文件支持选择文件夹");
        }
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_file_folder
            WHERE id = ? AND user_id = ? AND is_deleted = 0
            """,
            Integer.class,
            folderId,
            userId
        );
        if (count == null || count == 0) {
            throw new BizException(404, "文件夹不存在");
        }
    }

    // 配额校验：群文件走群配额，其余走个人配额
    public void ensureEmployeeQuota(String ownerType, Long userId, Long incomingBytes) {
        if (!"IM_GROUP_FILE".equals(ownerType)) {
            fileQuotaPolicyService.ensurePersonalQuota(userId, incomingBytes);
        }
    }

    public void increaseEmployeeUsage(String ownerType, Long userId, Long incomingBytes) {
        if (!"IM_GROUP_FILE".equals(ownerType)) {
            storageUsageLedgerService.increaseUsage("USER", userId, incomingBytes, 1);
        }
    }
}
