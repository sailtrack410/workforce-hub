package com.workforcehub.file.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.file.dto.AdminFileResponse;
import com.workforcehub.file.dto.AttachmentRefResponse;
import com.workforcehub.file.dto.ConfirmUploadRequest;
import com.workforcehub.file.dto.CopyEmployeeFileRequest;
import com.workforcehub.file.dto.CreateEmployeeFileFolderRequest;
import com.workforcehub.file.dto.CreateQuotaPolicyRequest;
import com.workforcehub.file.dto.CreateDownloadTicketRequest;
import com.workforcehub.file.dto.CreateUploadTicketRequest;
import com.workforcehub.file.dto.DownloadFileResult;
import com.workforcehub.file.dto.DownloadTicketResponse;
import com.workforcehub.file.dto.EmployeeFileBrowserResponse;
import com.workforcehub.file.dto.EmployeeFileFolderResponse;
import com.workforcehub.file.dto.EmployeeFileResponse;
import com.workforcehub.file.dto.FilePurgeResponse;
import com.workforcehub.file.dto.FileRestoreResponse;
import com.workforcehub.file.dto.FileTrashRequest;
import com.workforcehub.file.dto.FileTrashResponse;
import com.workforcehub.file.dto.MoveEmployeeFileRequest;
import com.workforcehub.file.dto.QuotaPolicyResponse;
import com.workforcehub.file.dto.QuotaResponse;
import com.workforcehub.file.dto.StorageSummaryResponse;
import com.workforcehub.file.dto.UpdateQuotaPolicyRequest;
import com.workforcehub.file.dto.UploadTicketResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文件中心第一版服务。
 * 当前先落 LOCAL 主链路，Provider 字段保留 MINIO 扩展位。
 */
@Service
public class FileCenterService {

    private final JdbcTemplate jdbcTemplate;
    private final FileUploadTicketService fileUploadTicketService;
    private final FileUploadConfirmService fileUploadConfirmService;
    private final FileDownloadService fileDownloadService;
    private final FileLifecycleService fileLifecycleService;
    private final FileQuotaPolicyService fileQuotaPolicyService;
    private final StorageUsageLedgerService storageUsageLedgerService;

    public FileCenterService(JdbcTemplate jdbcTemplate,
                             FileUploadTicketService fileUploadTicketService,
                             FileUploadConfirmService fileUploadConfirmService,
                             FileDownloadService fileDownloadService,
                             FileLifecycleService fileLifecycleService,
                             FileQuotaPolicyService fileQuotaPolicyService,
                             StorageUsageLedgerService storageUsageLedgerService) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileUploadTicketService = fileUploadTicketService;
        this.fileUploadConfirmService = fileUploadConfirmService;
        this.fileDownloadService = fileDownloadService;
        this.fileLifecycleService = fileLifecycleService;
        this.fileQuotaPolicyService = fileQuotaPolicyService;
        this.storageUsageLedgerService = storageUsageLedgerService;
    }

    public UploadTicketResponse createEmployeeUploadTicket(CreateUploadTicketRequest request) {
        return fileUploadTicketService.createEmployeeUploadTicket(request);
    }

    public UploadTicketResponse createAdminUploadTicket(CreateUploadTicketRequest request) {
        return fileUploadTicketService.createAdminUploadTicket(request);
    }

    public AttachmentRefResponse confirmEmployeeUpload(ConfirmUploadRequest request) {
        return fileUploadConfirmService.confirmEmployeeUpload(request);
    }

    public AttachmentRefResponse confirmAdminUpload(ConfirmUploadRequest request) {
        return fileUploadConfirmService.confirmAdminUpload(request);
    }

    public DownloadTicketResponse createEmployeeDownloadTicket(Long attachmentRefId, CreateDownloadTicketRequest request) {
        return fileDownloadService.createEmployeeDownloadTicket(attachmentRefId, request);
    }

    public DownloadFileResult downloadEmployeeFile(String ticketNo) {
        return fileDownloadService.downloadEmployeeFile(ticketNo);
    }

    public PageResult<EmployeeFileResponse> listEmployeeFiles(long pageNo, long pageSize, boolean recycleBin) {
        Long userId = CurrentUserHolder.requireUserId();
        long safePageNo = Math.max(pageNo, 1L);
        long safePageSize = Math.min(Math.max(pageSize, 1L), 100L);
        long offset = (safePageNo - 1L) * safePageSize;
        String deletedFilter = recycleBin ? "ar.deleted_at IS NOT NULL" : "ar.deleted_at IS NULL";

        Long total = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE ar.owner_type = 'PERSONAL_FILE'
              AND ar.uploader_type = 'USER'
              AND ar.uploader_id = ?
              AND ar.is_deleted = 0
              AND fo.is_deleted = 0
              AND %s
            """.formatted(deletedFilter), Long.class, userId);

        List<EmployeeFileResponse> rows = jdbcTemplate.query("""
            SELECT ar.id AS attachment_ref_id, ar.file_object_id, ar.owner_type, ar.owner_id,
                   ar.original_file_name, ar.display_name, ar.uploader_type, ar.uploader_id,
                   ar.folder_id, ar.deleted_at, ar.purge_after, ar.created_at, ar.updated_at,
                   fo.mime_type, fo.file_size, fo.scan_status
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE ar.owner_type = 'PERSONAL_FILE'
              AND ar.uploader_type = 'USER'
              AND ar.uploader_id = ?
              AND ar.is_deleted = 0
              AND fo.is_deleted = 0
              AND %s
            ORDER BY ar.created_at DESC, ar.id DESC
            LIMIT ? OFFSET ?
            """.formatted(deletedFilter),
            this::mapEmployeeFile,
            userId,
            safePageSize,
            offset
        );
        return PageResult.of(safePageNo, safePageSize, total == null ? 0L : total, rows);
    }

    public EmployeeFileBrowserResponse browseEmployeeFiles(Long folderId, String keyword, String fileType) {
        Long userId = CurrentUserHolder.requireUserId();
        ensureFolderAccess(userId, folderId);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String fileTypeCondition = fileTypeCondition(fileType);

        List<EmployeeFileFolderResponse> folders = jdbcTemplate.query("""
            SELECT id AS folder_id, parent_folder_id, folder_name, created_at, updated_at
            FROM sys_file_folder
            WHERE user_id = ?
              AND is_deleted = 0
              AND ((? IS NULL AND parent_folder_id IS NULL) OR parent_folder_id = ?)
              AND (? IS NULL OR folder_name LIKE CONCAT('%', ?, '%'))
            ORDER BY sort_order ASC, folder_name ASC, id ASC
            """,
            this::mapEmployeeFileFolder,
            userId,
            folderId,
            folderId,
            normalizedKeyword,
            normalizedKeyword
        );

        List<EmployeeFileResponse> files = jdbcTemplate.query("""
            SELECT ar.id AS attachment_ref_id, ar.file_object_id, ar.owner_type, ar.owner_id,
                   ar.folder_id, ar.original_file_name, ar.display_name, ar.uploader_type, ar.uploader_id,
                   ar.deleted_at, ar.purge_after, ar.created_at, ar.updated_at,
                   fo.mime_type, fo.file_size, fo.scan_status
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE ar.owner_type = 'PERSONAL_FILE'
              AND ar.uploader_type = 'USER'
              AND ar.uploader_id = ?
              AND ar.is_deleted = 0
              AND ar.deleted_at IS NULL
              AND fo.is_deleted = 0
              AND ((? IS NULL AND ar.folder_id IS NULL) OR ar.folder_id = ?)
              AND (? IS NULL OR ar.display_name LIKE CONCAT('%', ?, '%') OR ar.original_file_name LIKE CONCAT('%', ?, '%'))
              AND __FILE_TYPE_CONDITION__
            ORDER BY ar.created_at DESC, ar.id DESC
            """.replace("__FILE_TYPE_CONDITION__", fileTypeCondition),
            this::mapEmployeeFile,
            userId,
            folderId,
            folderId,
            normalizedKeyword,
            normalizedKeyword,
            normalizedKeyword
        );

        EmployeeFileBrowserResponse response = new EmployeeFileBrowserResponse();
        response.setCurrentFolderId(folderId);
        response.setBreadcrumbs(folderBreadcrumbs(userId, folderId));
        response.setFolders(folders);
        response.setFiles(files);
        return response;
    }

    public List<EmployeeFileFolderResponse> listEmployeeFolders() {
        Long userId = CurrentUserHolder.requireUserId();
        return jdbcTemplate.query("""
            SELECT id AS folder_id, parent_folder_id, folder_name, created_at, updated_at
            FROM sys_file_folder
            WHERE user_id = ? AND is_deleted = 0
            ORDER BY CASE WHEN parent_folder_id IS NULL THEN 0 ELSE 1 END, parent_folder_id ASC, folder_name ASC, id ASC
            """,
            this::mapEmployeeFileFolder,
            userId
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public EmployeeFileFolderResponse createEmployeeFolder(CreateEmployeeFileFolderRequest request) {
        Long userId = CurrentUserHolder.requireUserId();
        Long parentFolderId = request == null ? null : request.getParentFolderId();
        ensureFolderAccess(userId, parentFolderId);
        String folderName = normalizeFolderName(request == null ? null : request.getFolderName());
        Long folderId = IdWorker.getId();
        try {
            jdbcTemplate.update("""
                INSERT INTO sys_file_folder
                    (id, user_id, parent_folder_id, folder_name, created_by, updated_by)
                VALUES
                    (?, ?, ?, ?, ?, ?)
                """,
                folderId,
                userId,
                parentFolderId,
                folderName,
                userId,
                userId
            );
        } catch (DuplicateKeyException e) {
            throw new BizException(409, "同级目录已存在");
        }
        audit("USER", userId, "FILE_FOLDER_CREATED", "FILE_FOLDER", folderId, "SUCCESS",
            "{\"parentFolderId\":%s}".formatted(parentFolderId == null ? "null" : parentFolderId));
        return getEmployeeFolderOrThrow(userId, folderId);
    }

    @Transactional(rollbackFor = Exception.class)
    public EmployeeFileResponse moveEmployeeFile(Long attachmentRefId, MoveEmployeeFileRequest request) {
        Long userId = CurrentUserHolder.requireUserId();
        Long targetFolderId = request == null ? null : request.getTargetFolderId();
        ensureFolderAccess(userId, targetFolderId);
        ensurePersonalFileEditable(userId, attachmentRefId);
        int updated = jdbcTemplate.update("""
            UPDATE sys_attachment_ref
            SET folder_id = ?, updated_by = ?
            WHERE id = ?
              AND owner_type = 'PERSONAL_FILE'
              AND uploader_type = 'USER'
              AND uploader_id = ?
              AND is_deleted = 0
              AND deleted_at IS NULL
            """,
            targetFolderId,
            userId,
            attachmentRefId,
            userId
        );
        if (updated == 0) {
            throw new BizException(404, "文件不存在");
        }
        audit("USER", userId, "FILE_MOVED", "ATTACHMENT_REF", attachmentRefId, "SUCCESS",
            "{\"targetFolderId\":%s}".formatted(targetFolderId == null ? "null" : targetFolderId));
        return getEmployeeFileOrThrow(userId, attachmentRefId);
    }

    @Transactional(rollbackFor = Exception.class)
    public EmployeeFileResponse copyEmployeeFile(Long attachmentRefId, CopyEmployeeFileRequest request) {
        Long userId = CurrentUserHolder.requireUserId();
        Long targetFolderId = request == null ? null : request.getTargetFolderId();
        ensureFolderAccess(userId, targetFolderId);
        CopySourceRow source = getCopySourceOrThrow(userId, attachmentRefId);
        fileQuotaPolicyService.ensurePersonalQuota(userId, source.fileSize());
        fileQuotaPolicyService.ensureGlobalQuota(source.fileSize());

        Long copiedAttachmentRefId = IdWorker.getId();
        String displayName = normalizeCopyDisplayName(request == null ? null : request.getDisplayName(), source.displayName());
        jdbcTemplate.update("""
            INSERT INTO sys_attachment_ref
                (id, owner_type, owner_id, file_object_id, source_upload_ticket_id, original_file_name,
                 display_name, uploader_type, uploader_id, visibility_scope, folder_id, created_by, updated_by)
            VALUES
                (?, 'PERSONAL_FILE', NULL, ?, NULL, ?, ?, 'USER', ?, 'OWNER', ?, ?, ?)
            """,
            copiedAttachmentRefId,
            source.fileObjectId(),
            source.originalFileName(),
            displayName,
            userId,
            targetFolderId,
            userId,
            userId
        );
        jdbcTemplate.update("UPDATE sys_file_object SET ref_count = ref_count + 1 WHERE id = ?", source.fileObjectId());
        storageUsageLedgerService.increaseUsage("USER", userId, source.fileSize(), 1);
        storageUsageLedgerService.increaseUsage("GLOBAL", null, source.fileSize(), 1);
        audit("USER", userId, "FILE_COPIED", "ATTACHMENT_REF", copiedAttachmentRefId, "SUCCESS",
            "{\"sourceAttachmentRefId\":%s,\"targetFolderId\":%s}".formatted(
                attachmentRefId,
                targetFolderId == null ? "null" : targetFolderId
            ));
        return getEmployeeFileOrThrow(userId, copiedAttachmentRefId);
    }

    public PageResult<AdminFileResponse> listAdminFiles(long pageNo, long pageSize, boolean recycleBin) {
        CurrentUserHolder.requireUserId();
        long safePageNo = Math.max(pageNo, 1L);
        long safePageSize = Math.min(Math.max(pageSize, 1L), 100L);
        long offset = (safePageNo - 1L) * safePageSize;
        String deletedFilter = recycleBin ? "ar.deleted_at IS NOT NULL" : "ar.deleted_at IS NULL";

        Long total = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE ar.owner_type = 'ADMIN_FILE'
              AND ar.uploader_type = 'ADMIN'
              AND ar.is_deleted = 0
              AND fo.is_deleted = 0
              AND %s
            """.formatted(deletedFilter), Long.class);

        List<AdminFileResponse> rows = jdbcTemplate.query("""
            SELECT ar.id AS attachment_ref_id, ar.file_object_id, ar.owner_type, ar.owner_id,
                   ar.original_file_name, ar.display_name, ar.uploader_type, ar.uploader_id,
                   ar.deleted_at, ar.purge_after, ar.created_at, ar.updated_at,
                   fo.mime_type, fo.file_size, fo.scan_status
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE ar.owner_type = 'ADMIN_FILE'
              AND ar.uploader_type = 'ADMIN'
              AND ar.is_deleted = 0
              AND fo.is_deleted = 0
              AND %s
            ORDER BY ar.created_at DESC, ar.id DESC
            LIMIT ? OFFSET ?
            """.formatted(deletedFilter),
            this::mapAdminFile,
            safePageSize,
            offset
        );
        return PageResult.of(safePageNo, safePageSize, total == null ? 0L : total, rows);
    }

    public DownloadTicketResponse createAdminDownloadTicket(Long attachmentRefId, CreateDownloadTicketRequest request) {
        return fileDownloadService.createAdminDownloadTicket(attachmentRefId, request);
    }

    public DownloadFileResult downloadAdminFile(String ticketNo) {
        return fileDownloadService.downloadAdminFile(ticketNo);
    }

    public QuotaResponse getEmployeeQuota() {
        return fileQuotaPolicyService.getEmployeeQuota();
    }

    public StorageSummaryResponse getStorageSummary() {
        return fileQuotaPolicyService.getStorageSummary();
    }

    public List<QuotaPolicyResponse> listQuotaPolicies(String scopeType) {
        return fileQuotaPolicyService.listQuotaPolicies(scopeType);
    }

    public QuotaPolicyResponse createQuotaPolicy(CreateQuotaPolicyRequest request) {
        return fileQuotaPolicyService.createQuotaPolicy(request);
    }

    public QuotaPolicyResponse updateQuotaPolicy(Long policyId, UpdateQuotaPolicyRequest request) {
        return fileQuotaPolicyService.updateQuotaPolicy(policyId, request);
    }

    public FileTrashResponse trashEmployeeFile(Long attachmentRefId, FileTrashRequest request) {
        return fileLifecycleService.trashEmployeeFile(attachmentRefId, request);
    }

    public FileRestoreResponse restoreEmployeeFile(Long attachmentRefId, FileTrashRequest request) {
        return fileLifecycleService.restoreEmployeeFile(attachmentRefId, request);
    }

    public FilePurgeResponse purgeEmployeeFile(Long attachmentRefId) {
        return fileLifecycleService.purgeEmployeeFile(attachmentRefId);
    }

    public FileTrashResponse trashAdminFile(Long attachmentRefId, FileTrashRequest request) {
        return fileLifecycleService.trashAdminFile(attachmentRefId, request);
    }

    public FileRestoreResponse restoreAdminFile(Long attachmentRefId, FileTrashRequest request) {
        return fileLifecycleService.restoreAdminFile(attachmentRefId, request);
    }

    public FilePurgeResponse purgeAdminFile(Long attachmentRefId) {
        return fileLifecycleService.purgeAdminFile(attachmentRefId);
    }

    private AdminFileResponse mapAdminFile(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        AdminFileResponse response = new AdminFileResponse();
        response.setAttachmentRefId(rs.getLong("attachment_ref_id"));
        response.setFileObjectId(rs.getLong("file_object_id"));
        response.setOwnerType(rs.getString("owner_type"));
        response.setOwnerId(rs.getObject("owner_id", Long.class));
        response.setOriginalFileName(rs.getString("original_file_name"));
        response.setDisplayName(rs.getString("display_name"));
        response.setUploaderType(rs.getString("uploader_type"));
        response.setUploaderId(rs.getObject("uploader_id", Long.class));
        response.setMimeType(rs.getString("mime_type"));
        response.setFileSize(rs.getLong("file_size"));
        response.setScanStatus(rs.getString("scan_status"));
        response.setDeletedAt(rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toLocalDateTime());
        response.setPurgeAfter(rs.getTimestamp("purge_after") == null ? null : rs.getTimestamp("purge_after").toLocalDateTime());
        response.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        response.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return response;
    }

    private EmployeeFileResponse mapEmployeeFile(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        EmployeeFileResponse response = new EmployeeFileResponse();
        response.setAttachmentRefId(rs.getLong("attachment_ref_id"));
        response.setFileObjectId(rs.getLong("file_object_id"));
        response.setOwnerType(rs.getString("owner_type"));
        response.setOwnerId(rs.getObject("owner_id", Long.class));
        response.setFolderId(rs.getObject("folder_id", Long.class));
        response.setOriginalFileName(rs.getString("original_file_name"));
        response.setDisplayName(rs.getString("display_name"));
        response.setUploaderType(rs.getString("uploader_type"));
        response.setUploaderId(rs.getObject("uploader_id", Long.class));
        response.setMimeType(rs.getString("mime_type"));
        response.setFileSize(rs.getLong("file_size"));
        response.setScanStatus(rs.getString("scan_status"));
        response.setDeletedAt(rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toLocalDateTime());
        response.setPurgeAfter(rs.getTimestamp("purge_after") == null ? null : rs.getTimestamp("purge_after").toLocalDateTime());
        response.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        response.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return response;
    }

    private EmployeeFileFolderResponse mapEmployeeFileFolder(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        EmployeeFileFolderResponse response = new EmployeeFileFolderResponse();
        response.setFolderId(rs.getLong("folder_id"));
        response.setParentFolderId(rs.getObject("parent_folder_id", Long.class));
        response.setFolderName(rs.getString("folder_name"));
        response.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        response.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return response;
    }

    private EmployeeFileFolderResponse getEmployeeFolderOrThrow(Long userId, Long folderId) {
        List<EmployeeFileFolderResponse> rows = jdbcTemplate.query("""
            SELECT id AS folder_id, parent_folder_id, folder_name, created_at, updated_at
            FROM sys_file_folder
            WHERE id = ? AND user_id = ? AND is_deleted = 0
            """,
            this::mapEmployeeFileFolder,
            folderId,
            userId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "文件夹不存在");
        }
        return rows.get(0);
    }

    private EmployeeFileResponse getEmployeeFileOrThrow(Long userId, Long attachmentRefId) {
        List<EmployeeFileResponse> rows = jdbcTemplate.query("""
            SELECT ar.id AS attachment_ref_id, ar.file_object_id, ar.owner_type, ar.owner_id,
                   ar.folder_id, ar.original_file_name, ar.display_name, ar.uploader_type, ar.uploader_id,
                   ar.deleted_at, ar.purge_after, ar.created_at, ar.updated_at,
                   fo.mime_type, fo.file_size, fo.scan_status
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE ar.id = ?
              AND ar.owner_type = 'PERSONAL_FILE'
              AND ar.uploader_type = 'USER'
              AND ar.uploader_id = ?
              AND ar.is_deleted = 0
              AND fo.is_deleted = 0
            """,
            this::mapEmployeeFile,
            attachmentRefId,
            userId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "文件不存在");
        }
        return rows.get(0);
    }

    private CopySourceRow getCopySourceOrThrow(Long userId, Long attachmentRefId) {
        List<CopySourceRow> rows = jdbcTemplate.query("""
            SELECT ar.id AS attachment_ref_id, ar.file_object_id, ar.original_file_name, ar.display_name,
                   ar.deleted_at, ar.is_deleted, fo.file_size, fo.is_deleted AS file_deleted
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE ar.id = ?
              AND ar.owner_type = 'PERSONAL_FILE'
              AND ar.uploader_type = 'USER'
              AND ar.uploader_id = ?
            """,
            (rs, rowNum) -> new CopySourceRow(
                rs.getLong("attachment_ref_id"),
                rs.getLong("file_object_id"),
                rs.getString("original_file_name"),
                rs.getString("display_name"),
                rs.getLong("file_size"),
                rs.getTimestamp("deleted_at") == null,
                rs.getInt("is_deleted") == 0,
                rs.getInt("file_deleted") == 0
            ),
            attachmentRefId,
            userId
        );
        if (rows.isEmpty() || !rows.get(0).active()) {
            throw new BizException(404, "文件不存在");
        }
        return rows.get(0);
    }

    private void ensurePersonalFileEditable(Long userId, Long attachmentRefId) {
        EmployeeFileResponse file = getEmployeeFileOrThrow(userId, attachmentRefId);
        if (file.getDeletedAt() != null) {
            throw new BizException(409, "文件已删除");
        }
    }

    private void ensureFolderAccess(Long userId, Long folderId) {
        if (folderId == null) {
            return;
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

    private List<EmployeeFileFolderResponse> folderBreadcrumbs(Long userId, Long folderId) {
        List<EmployeeFileFolderResponse> breadcrumbs = new ArrayList<>();
        Long currentFolderId = folderId;
        while (currentFolderId != null) {
            EmployeeFileFolderResponse folder = getEmployeeFolderOrThrow(userId, currentFolderId);
            breadcrumbs.add(folder);
            currentFolderId = folder.getParentFolderId();
        }
        Collections.reverse(breadcrumbs);
        return breadcrumbs;
    }

    private String normalizeFolderName(String folderName) {
        if (!StringUtils.hasText(folderName)) {
            throw new BizException(400, "文件夹名称不能为空");
        }
        String normalized = folderName.trim();
        if (normalized.length() > 64) {
            throw new BizException(400, "文件夹名称不能超过64个字符");
        }
        if (normalized.contains("/") || normalized.contains("\\")) {
            throw new BizException(400, "文件夹名称不能包含路径分隔符");
        }
        return normalized;
    }

    private String normalizeCopyDisplayName(String displayName, String sourceName) {
        if (StringUtils.hasText(displayName)) {
            return displayName.trim();
        }
        String name = StringUtils.hasText(sourceName) ? sourceName : "未命名文件";
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == name.length() - 1) {
            return name + " 副本";
        }
        return name.substring(0, dotIndex) + " 副本" + name.substring(dotIndex);
    }

    private String fileTypeCondition(String fileType) {
        if (!StringUtils.hasText(fileType) || "all".equalsIgnoreCase(fileType)) {
            return "1 = 1";
        }
        return switch (fileType.trim().toLowerCase()) {
            case "image" -> "(LOWER(fo.mime_type) LIKE 'image/%' OR LOWER(ar.display_name) LIKE '%.png' OR LOWER(ar.display_name) LIKE '%.jpg' OR LOWER(ar.display_name) LIKE '%.jpeg' OR LOWER(ar.display_name) LIKE '%.gif' OR LOWER(ar.display_name) LIKE '%.webp' OR LOWER(ar.display_name) LIKE '%.svg')";
            case "document" -> "(LOWER(fo.mime_type) LIKE 'text/%' OR LOWER(fo.mime_type) = 'application/pdf' OR LOWER(ar.display_name) LIKE '%.pdf' OR LOWER(ar.display_name) LIKE '%.doc' OR LOWER(ar.display_name) LIKE '%.docx' OR LOWER(ar.display_name) LIKE '%.txt' OR LOWER(ar.display_name) LIKE '%.md')";
            case "sheet" -> "(LOWER(ar.display_name) LIKE '%.xls' OR LOWER(ar.display_name) LIKE '%.xlsx' OR LOWER(ar.display_name) LIKE '%.csv')";
            case "archive" -> "(LOWER(ar.display_name) LIKE '%.zip' OR LOWER(ar.display_name) LIKE '%.rar' OR LOWER(ar.display_name) LIKE '%.7z' OR LOWER(ar.display_name) LIKE '%.tar' OR LOWER(ar.display_name) LIKE '%.gz')";
            case "media" -> "(LOWER(fo.mime_type) LIKE 'audio/%' OR LOWER(fo.mime_type) LIKE 'video/%')";
            case "code" -> "(LOWER(fo.mime_type) LIKE '%json%' OR LOWER(ar.display_name) LIKE '%.json' OR LOWER(ar.display_name) LIKE '%.jsonl' OR LOWER(ar.display_name) LIKE '%.js' OR LOWER(ar.display_name) LIKE '%.ts' OR LOWER(ar.display_name) LIKE '%.tsx' OR LOWER(ar.display_name) LIKE '%.vue' OR LOWER(ar.display_name) LIKE '%.java' OR LOWER(ar.display_name) LIKE '%.sql' OR LOWER(ar.display_name) LIKE '%.xml' OR LOWER(ar.display_name) LIKE '%.html' OR LOWER(ar.display_name) LIKE '%.css' OR LOWER(ar.display_name) LIKE '%.scss' OR LOWER(ar.display_name) LIKE '%.yml' OR LOWER(ar.display_name) LIKE '%.yaml')";
            default -> "1 = 1";
        };
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

    private record CopySourceRow(
        Long attachmentRefId,
        Long fileObjectId,
        String originalFileName,
        String displayName,
        Long fileSize,
        boolean notTrashed,
        boolean attachmentActive,
        boolean fileActive
    ) {
        boolean active() {
            return notTrashed && attachmentActive && fileActive;
        }
    }

}
