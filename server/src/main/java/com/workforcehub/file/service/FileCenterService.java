package com.workforcehub.file.service;

import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.file.dto.AdminFileResponse;
import com.workforcehub.file.dto.AttachmentRefResponse;
import com.workforcehub.file.dto.ConfirmUploadRequest;
import com.workforcehub.file.dto.CreateQuotaPolicyRequest;
import com.workforcehub.file.dto.CreateDownloadTicketRequest;
import com.workforcehub.file.dto.CreateUploadTicketRequest;
import com.workforcehub.file.dto.DownloadFileResult;
import com.workforcehub.file.dto.DownloadTicketResponse;
import com.workforcehub.file.dto.FilePurgeResponse;
import com.workforcehub.file.dto.FileRestoreResponse;
import com.workforcehub.file.dto.FileTrashRequest;
import com.workforcehub.file.dto.FileTrashResponse;
import com.workforcehub.file.dto.QuotaPolicyResponse;
import com.workforcehub.file.dto.QuotaResponse;
import com.workforcehub.file.dto.StorageSummaryResponse;
import com.workforcehub.file.dto.UpdateQuotaPolicyRequest;
import com.workforcehub.file.dto.UploadTicketResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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

    public FileCenterService(JdbcTemplate jdbcTemplate,
                             FileUploadTicketService fileUploadTicketService,
                             FileUploadConfirmService fileUploadConfirmService,
                             FileDownloadService fileDownloadService,
                             FileLifecycleService fileLifecycleService,
                             FileQuotaPolicyService fileQuotaPolicyService) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileUploadTicketService = fileUploadTicketService;
        this.fileUploadConfirmService = fileUploadConfirmService;
        this.fileDownloadService = fileDownloadService;
        this.fileLifecycleService = fileLifecycleService;
        this.fileQuotaPolicyService = fileQuotaPolicyService;
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

}
