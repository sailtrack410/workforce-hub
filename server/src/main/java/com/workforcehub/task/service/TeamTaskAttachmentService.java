package com.workforcehub.task.service;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.file.dto.DownloadFileResult;
import com.workforcehub.file.dto.DownloadTicketResponse;
import com.workforcehub.file.repository.AttachmentRefRepository;
import com.workforcehub.file.repository.DownloadAttachmentRow;
import com.workforcehub.file.service.DownloadTicketExecutionService;
import com.workforcehub.file.storage.StorageProviderRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TeamTaskAttachmentService {
    private final JdbcTemplate jdbc;
    private final TeamTaskService tasks;
    private final AttachmentRefRepository attachments;
    private final StorageProviderRegistry storage;
    private final DownloadTicketExecutionService tickets;

    public TeamTaskAttachmentService(JdbcTemplate jdbc, TeamTaskService tasks, AttachmentRefRepository attachments,
                                      StorageProviderRegistry storage, DownloadTicketExecutionService tickets) {
        this.jdbc = jdbc; this.tasks = tasks; this.attachments = attachments; this.storage = storage; this.tickets = tickets;
    }

    public record Attachment(Long attachmentRefId, String fileName, long fileSize, String mimeType, String scanStatus, boolean canRemove) {}

    public List<Attachment> list(Long taskId) {
        var task = tasks.detail(taskId);
        Long userId = CurrentUserHolder.requireUserId();
        return jdbc.query("""
            SELECT a.attachment_ref_id, a.added_by, r.display_name, f.file_size, f.mime_type, f.scan_status
            FROM team_task_attachment a JOIN sys_attachment_ref r ON r.id = a.attachment_ref_id AND r.is_deleted = 0
            JOIN sys_file_object f ON f.id = r.file_object_id AND f.is_deleted = 0
            WHERE a.task_id = ? ORDER BY a.created_at, a.attachment_ref_id
            """, (rs, n) -> new Attachment(rs.getLong("attachment_ref_id"), rs.getString("display_name"),
                rs.getLong("file_size"), rs.getString("mime_type"), rs.getString("scan_status"),
                task.isCanEdit() || userId.equals(rs.getLong("added_by"))), taskId);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Attachment> attach(Long taskId, Long attachmentRefId) {
        lock(taskId); tasks.detail(taskId);
        var attachment = attachments.getAttachmentOrThrow(attachmentRefId);
        if (!"USER".equals(attachment.uploaderType()) || !CurrentUserHolder.requireUserId().equals(attachment.uploaderId())
            || !"PERSONAL_FILE".equals(attachment.ownerType()) || attachment.isDeleted() != 0 || attachment.deletedAt() != null) {
            throw new BizException(403, "只能添加自己上传的有效文件");
        }
        if (!java.util.Set.of("CLEAN", "PENDING", "SCANNING").contains(attachment.scanStatus())) throw new BizException(409, "文件未通过安全检查");
        jdbc.update("""
            INSERT INTO team_task_attachment (task_id, attachment_ref_id, added_by) VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE attachment_ref_id = VALUES(attachment_ref_id)
            """, taskId, attachmentRefId, CurrentUserHolder.requireUserId());
        return list(taskId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long taskId, Long attachmentRefId) {
        lock(taskId);
        var task = tasks.detail(taskId);
        var rows = jdbc.queryForList("SELECT added_by FROM team_task_attachment WHERE task_id = ? AND attachment_ref_id = ?", Long.class, taskId, attachmentRefId);
        if (rows.isEmpty()) throw new BizException(404, "任务附件不存在");
        if (!task.isCanEdit() && !CurrentUserHolder.requireUserId().equals(rows.get(0))) throw new BizException(403, "无权移除该附件");
        jdbc.update("DELETE FROM team_task_attachment WHERE task_id = ? AND attachment_ref_id = ?", taskId, attachmentRefId);
    }

    @Transactional(rollbackFor = Exception.class)
    public DownloadTicketResponse createTicket(Long taskId, Long attachmentRefId) {
        tasks.detail(taskId); requireLinked(taskId, attachmentRefId);
        var attachment = attachments.getDownloadAttachmentOrThrow(attachmentRefId); downloadable(attachment);
        Long ticketId = IdGenerator.nextId(); String ticketNo = "TKDL" + ticketId;
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(10);
        String url = "/api/employee/team-tasks/" + taskId + "/attachments/" + attachmentRefId + "/download/" + ticketNo;
        jdbc.update("""
            INSERT INTO sys_download_ticket (id, ticket_no, attachment_ref_id, requester_type, requester_id, download_type, download_url, expire_at, status)
            VALUES (?, ?, ?, 'USER', ?, 'ATTACHMENT', ?, ?, 'ACTIVE')
            """, ticketId, ticketNo, attachmentRefId, CurrentUserHolder.requireUserId(), url, expireAt);
        var result = new DownloadTicketResponse(); result.setDownloadTicketId(ticketId); result.setTicketNo(ticketNo);
        result.setDownloadUrl(url); result.setExpireAt(expireAt); result.setFileName(attachment.displayName()); result.setFileSize(attachment.fileSize());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public DownloadFileResult download(Long taskId, Long attachmentRefId, String ticketNo) {
        tasks.detail(taskId); requireLinked(taskId, attachmentRefId);
        var rows = jdbc.query("""
            SELECT id, requester_type, requester_id, status, expire_at, download_url FROM sys_download_ticket
            WHERE ticket_no = ? AND attachment_ref_id = ? AND download_type = 'ATTACHMENT'
            """, (rs, n) -> new Ticket(rs.getLong("id"), rs.getString("requester_type"), rs.getLong("requester_id"),
                rs.getString("status"), rs.getTimestamp("expire_at").toLocalDateTime(), rs.getString("download_url")), ticketNo, attachmentRefId);
        var ticket = rows.stream().findFirst().orElseThrow(() -> new BizException(404, "下载票据不存在"));
        String expectedUrl = "/api/employee/team-tasks/" + taskId + "/attachments/" + attachmentRefId + "/download/" + ticketNo;
        if (!"USER".equals(ticket.requesterType()) || !CurrentUserHolder.requireUserId().equals(ticket.requesterId()) || !expectedUrl.equals(ticket.url())) throw new BizException(403, "无权使用该下载票据");
        if (!"ACTIVE".equals(ticket.status())) throw new BizException(409, "下载票据已失效");
        if (ticket.expireAt().isBefore(LocalDateTime.now())) throw new BizException(410, "下载链接已过期");
        var attachment = attachments.getDownloadAttachmentOrThrow(attachmentRefId); downloadable(attachment);
        tickets.claimActiveTicket(ticket.id());
        var resource = storage.require(attachment.storageProvider()).loadObject(attachment.bucketName(), attachment.objectKey());
        return new DownloadFileResult(resource.resource(), attachment.displayName(), attachment.mimeType(), resource.size());
    }

    private record Ticket(Long id, String requesterType, Long requesterId, String status, LocalDateTime expireAt, String url) {}
    private void lock(Long taskId) { jdbc.queryForList("SELECT task_id FROM team_task WHERE task_id = ? FOR UPDATE", taskId); }
    private void requireLinked(Long taskId, Long attachmentRefId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM team_task_attachment WHERE task_id = ? AND attachment_ref_id = ?", Integer.class, taskId, attachmentRefId) == 0) throw new BizException(404, "任务附件不存在");
    }
    private void downloadable(DownloadAttachmentRow attachment) {
        if (attachment.deletedAt() != null || attachment.attachmentDeleted() != 0 || attachment.fileDeleted() != 0) throw new BizException(409, "文件已删除");
        if (!"CLEAN".equals(attachment.scanStatus())) throw new BizException(409, "文件尚未通过安全检查");
    }
}
