package com.workforcehub.file.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.file.dto.CreateDownloadTicketRequest;
import com.workforcehub.file.dto.DownloadFileResult;
import com.workforcehub.file.dto.DownloadTicketResponse;
import com.workforcehub.file.repository.AttachmentRefRepository;
import com.workforcehub.file.repository.AttachmentRow;
import com.workforcehub.file.repository.DownloadAttachmentRow;
import com.workforcehub.file.storage.StorageObjectResource;
import com.workforcehub.file.storage.StorageProviderRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件下载服务。
 * 负责下载票据签发、下载票据消费和物理对象读取。
 */
@Service
public class FileDownloadService {

    private final JdbcTemplate jdbcTemplate;
    private final StorageProviderRegistry storageProviderRegistry;
    private final DownloadTicketExecutionService downloadTicketExecutionService;
    private final AttachmentRefRepository attachmentRefRepository;

    public FileDownloadService(JdbcTemplate jdbcTemplate,
                               StorageProviderRegistry storageProviderRegistry,
                               DownloadTicketExecutionService downloadTicketExecutionService,
                               AttachmentRefRepository attachmentRefRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageProviderRegistry = storageProviderRegistry;
        this.downloadTicketExecutionService = downloadTicketExecutionService;
        this.attachmentRefRepository = attachmentRefRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public DownloadTicketResponse createEmployeeDownloadTicket(Long attachmentRefId, CreateDownloadTicketRequest request) {
        Long userId = CurrentUserHolder.requireUserId();
        AttachmentRow attachment = attachmentRefRepository.getAttachmentOrThrow(attachmentRefId);
        ensureEmployeeDownloadableAttachment(attachment);
        if (attachment.deletedAt() != null || attachment.isDeleted() != 0) {
            throw new BizException(409, "文件已删除");
        }
        String downloadType = normalizeEmployeeDownloadType(request.getDownloadType());
        if (downloadType == null) {
            throw new BizException(400, "员工端仅支持单文件下载或内联预览");
        }
        boolean ownUpload = "USER".equals(attachment.uploaderType()) && userId.equals(attachment.uploaderId());
        boolean inlineAvatar = "USER_AVATAR".equals(attachment.ownerType())
            && "USER".equals(attachment.uploaderType())
            && "INLINE".equals(downloadType);
        if (!ownUpload && !inlineAvatar) {
            throw new BizException(403, "无权访问该文件");
        }

        Long ticketId = IdWorker.getId();
        String ticketNo = "DL" + ticketId;
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(10);
        String downloadUrl = "/api/employee/files/download/" + ticketNo;
        jdbcTemplate.update("""
            INSERT INTO sys_download_ticket
                (id, ticket_no, attachment_ref_id, requester_type, requester_id,
                 download_type, download_url, reason, expire_at, status)
            VALUES
                (?, ?, ?, 'USER', ?, ?, ?, ?, ?, 'ACTIVE')
            """,
            ticketId,
            ticketNo,
            attachmentRefId,
            userId,
            downloadType,
            downloadUrl,
            blankToNull(request.getReason()),
            expireAt
        );

        DownloadTicketResponse response = new DownloadTicketResponse();
        response.setDownloadTicketId(ticketId);
        response.setTicketNo(ticketNo);
        response.setDownloadUrl(downloadUrl);
        response.setExpireAt(expireAt);
        response.setFileName(attachment.displayName());
        response.setFileSize(attachment.fileSize());
        audit("USER", userId, "FILE_DOWNLOAD_TICKET_CREATED", "DOWNLOAD_TICKET", ticketId, "SUCCESS",
            "{\"attachmentRefId\":%s}".formatted(attachmentRefId));
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public DownloadFileResult downloadEmployeeFile(String ticketNo) {
        Long userId = CurrentUserHolder.requireUserId();
        DownloadTicketRow ticket = getDownloadTicketOrThrow(ticketNo);
        if (!"USER".equals(ticket.requesterType()) || !userId.equals(ticket.requesterId())) {
            throw new BizException(403, "无权使用该下载票据");
        }
        if (!"ACTIVE".equals(ticket.status())) {
            throw new BizException(409, "下载票据已使用或已失效");
        }
        if (ticket.expireAt().isBefore(LocalDateTime.now())) {
            downloadTicketExecutionService.expireActiveTicket(ticket.id());
            throw new BizException(410, "下载链接已过期，请重新申请");
        }

        DownloadAttachmentRow attachment = attachmentRefRepository.getDownloadAttachmentOrThrow(ticket.attachmentRefId());
        boolean ownUpload = "USER".equals(attachment.uploaderType()) && userId.equals(attachment.uploaderId());
        boolean inlineAvatar = "USER_AVATAR".equals(attachment.ownerType())
            && "USER".equals(attachment.uploaderType())
            && "INLINE".equals(ticket.downloadType());
        if (!ownUpload && !inlineAvatar) {
            throw new BizException(403, "无权访问该文件");
        }
        ensureEmployeeDownloadableAttachment(attachment);
        if (attachment.deletedAt() != null || attachment.attachmentDeleted() != 0 || attachment.fileDeleted() != 0) {
            throw new BizException(409, "文件已删除");
        }

        downloadTicketExecutionService.claimActiveTicket(ticket.id());
        StorageObjectResource resource = storageProviderRegistry.require(attachment.storageProvider())
            .loadObject(attachment.bucketName(), attachment.objectKey());
        audit("USER", userId, "FILE_DOWNLOAD_EXECUTED", "DOWNLOAD_TICKET", ticket.id(), "SUCCESS",
            "{\"attachmentRefId\":%s}".formatted(attachment.attachmentRefId()));
        return new DownloadFileResult(resource.resource(), attachment.displayName(), attachment.mimeType(), resource.size());
    }

    @Transactional(rollbackFor = Exception.class)
    public DownloadTicketResponse createAdminDownloadTicket(Long attachmentRefId, CreateDownloadTicketRequest request) {
        Long adminId = CurrentUserHolder.requireUserId();
        AttachmentRow attachment = attachmentRefRepository.getAttachmentOrThrow(attachmentRefId);
        ensureAdminFileAttachment(attachment);
        if (attachment.deletedAt() != null || attachment.isDeleted() != 0) {
            throw new BizException(409, "文件已删除");
        }
        if (!"SINGLE".equalsIgnoreCase(request.getDownloadType())) {
            throw new BizException(400, "第一版仅支持单文件下载");
        }

        Long ticketId = IdWorker.getId();
        String ticketNo = "DL" + ticketId;
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(10);
        String downloadUrl = "/api/admin/files/download/" + ticketNo;
        jdbcTemplate.update("""
            INSERT INTO sys_download_ticket
                (id, ticket_no, attachment_ref_id, requester_type, requester_id,
                 download_type, download_url, reason, expire_at, status)
            VALUES
                (?, ?, ?, 'ADMIN', ?, 'SINGLE', ?, ?, ?, 'ACTIVE')
            """,
            ticketId,
            ticketNo,
            attachmentRefId,
            adminId,
            downloadUrl,
            blankToNull(request.getReason()),
            expireAt
        );

        DownloadTicketResponse response = new DownloadTicketResponse();
        response.setDownloadTicketId(ticketId);
        response.setTicketNo(ticketNo);
        response.setDownloadUrl(downloadUrl);
        response.setExpireAt(expireAt);
        response.setFileName(attachment.displayName());
        response.setFileSize(attachment.fileSize());
        audit("ADMIN", adminId, "FILE_ADMIN_DOWNLOAD_TICKET_CREATED", "DOWNLOAD_TICKET", ticketId, "SUCCESS",
            "{\"attachmentRefId\":%s}".formatted(attachmentRefId));
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public DownloadFileResult downloadAdminFile(String ticketNo) {
        Long adminId = CurrentUserHolder.requireUserId();
        DownloadTicketRow ticket = getDownloadTicketOrThrow(ticketNo);
        if (!"ADMIN".equals(ticket.requesterType()) || !adminId.equals(ticket.requesterId())) {
            throw new BizException(403, "无权使用该下载票据");
        }
        if (!"ACTIVE".equals(ticket.status())) {
            throw new BizException(409, "下载票据已使用或已失效");
        }
        if (ticket.expireAt().isBefore(LocalDateTime.now())) {
            downloadTicketExecutionService.expireActiveTicket(ticket.id());
            throw new BizException(410, "下载链接已过期，请重新申请");
        }

        DownloadAttachmentRow attachment = attachmentRefRepository.getDownloadAttachmentOrThrow(ticket.attachmentRefId());
        ensureAdminDownloadAttachment(attachment);
        if (attachment.deletedAt() != null || attachment.attachmentDeleted() != 0 || attachment.fileDeleted() != 0) {
            throw new BizException(409, "文件已删除");
        }

        downloadTicketExecutionService.claimActiveTicket(ticket.id());
        StorageObjectResource resource = storageProviderRegistry.require(attachment.storageProvider())
            .loadObject(attachment.bucketName(), attachment.objectKey());
        audit("ADMIN", adminId, "FILE_ADMIN_DOWNLOAD_EXECUTED", "DOWNLOAD_TICKET", ticket.id(), "SUCCESS",
            "{\"attachmentRefId\":%s}".formatted(attachment.attachmentRefId()));
        return new DownloadFileResult(resource.resource(), attachment.displayName(), attachment.mimeType(), resource.size());
    }

    private void ensureAdminFileAttachment(AttachmentRow attachment) {
        if ((!"ADMIN_FILE".equals(attachment.ownerType()) && !"RAGFLOW_DOCUMENT".equals(attachment.ownerType()))
            || !"ADMIN".equals(attachment.uploaderType())) {
            throw new BizException(403, "后台接口仅允许治理后台文件");
        }
    }

    private void ensureEmployeeDownloadableAttachment(AttachmentRow attachment) {
        if (!"PERSONAL_FILE".equals(attachment.ownerType()) && !"USER_AVATAR".equals(attachment.ownerType())) {
            throw new BizException(403, employeeDownloadErrorMessage(attachment.ownerType()));
        }
    }

    private void ensureEmployeeDownloadableAttachment(DownloadAttachmentRow attachment) {
        if (!"PERSONAL_FILE".equals(attachment.ownerType()) && !"USER_AVATAR".equals(attachment.ownerType())) {
            throw new BizException(403, employeeDownloadErrorMessage(attachment.ownerType()));
        }
    }

    private String employeeDownloadErrorMessage(String ownerType) {
        return "WF_REQUEST".equals(ownerType)
            ? "员工端文件接口仅允许下载个人文件"
            : "员工端文件接口仅允许下载个人文件或头像";
    }

    private String normalizeEmployeeDownloadType(String downloadType) {
        if (!StringUtils.hasText(downloadType)) {
            return "SINGLE";
        }
        String normalized = downloadType.trim().toUpperCase();
        return "SINGLE".equals(normalized) || "INLINE".equals(normalized) ? normalized : null;
    }

    private void ensureAdminDownloadAttachment(DownloadAttachmentRow attachment) {
        if ((!"ADMIN_FILE".equals(attachment.ownerType()) && !"RAGFLOW_DOCUMENT".equals(attachment.ownerType()))
            || !"ADMIN".equals(attachment.uploaderType())) {
            throw new BizException(403, "后台接口仅允许治理后台文件");
        }
    }

    private DownloadTicketRow getDownloadTicketOrThrow(String ticketNo) {
        List<DownloadTicketRow> rows = jdbcTemplate.query("""
            SELECT id, ticket_no, attachment_ref_id, requester_type, requester_id,
                   download_type, expire_at, status
            FROM sys_download_ticket
            WHERE ticket_no = ? AND is_deleted = 0
            """,
            (rs, rowNum) -> new DownloadTicketRow(
                rs.getLong("id"),
                rs.getString("ticket_no"),
                rs.getLong("attachment_ref_id"),
                rs.getString("requester_type"),
                rs.getLong("requester_id"),
                rs.getString("download_type"),
                rs.getTimestamp("expire_at").toLocalDateTime(),
                rs.getString("status")
            ),
            ticketNo
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "下载票据不存在");
        }
        return rows.get(0);
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

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private record DownloadTicketRow(
        Long id,
        String ticketNo,
        Long attachmentRefId,
        String requesterType,
        Long requesterId,
        String downloadType,
        LocalDateTime expireAt,
        String status
    ) {}
}
