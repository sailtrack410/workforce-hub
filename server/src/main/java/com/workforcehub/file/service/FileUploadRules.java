package com.workforcehub.file.service;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.im.repository.ImRepository;
import org.springframework.stereotype.Component;

@Component
public class FileUploadRules {

    private final StorageUsageLedgerService storageUsageLedgerService;
    private final FileQuotaPolicyService fileQuotaPolicyService;
    private final ImRepository imRepository;

    public FileUploadRules(StorageUsageLedgerService storageUsageLedgerService,
                           FileQuotaPolicyService fileQuotaPolicyService,
                           ImRepository imRepository) {
        this.storageUsageLedgerService = storageUsageLedgerService;
        this.fileQuotaPolicyService = fileQuotaPolicyService;
        this.imRepository = imRepository;
    }

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
