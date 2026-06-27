package com.workforcehub.file.service;

import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.file.dto.AdminFileResponse;
import com.workforcehub.file.dto.EmployeeFileResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文件列表查询服务。
 */
@Service
public class FileQueryService {

    private final JdbcTemplate jdbcTemplate;

    public FileQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
                   ar.deleted_at, ar.purge_after, ar.created_at, ar.updated_at,
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
