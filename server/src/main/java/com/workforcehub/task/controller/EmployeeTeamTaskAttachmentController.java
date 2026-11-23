package com.workforcehub.task.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.file.dto.DownloadTicketResponse;
import com.workforcehub.task.service.TeamTaskAttachmentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@ConditionalOnProperty(prefix = "workforce.task", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/employee/team-tasks/{taskId}/attachments")
public class EmployeeTeamTaskAttachmentController {
    private final TeamTaskAttachmentService service;
    public EmployeeTeamTaskAttachmentController(TeamTaskAttachmentService service) { this.service = service; }

    @GetMapping
    public R<List<TeamTaskAttachmentService.Attachment>> list(@PathVariable Long taskId) { return R.ok(service.list(taskId)); }
    @PutMapping("/{attachmentRefId}")
    public R<List<TeamTaskAttachmentService.Attachment>> attach(@PathVariable Long taskId, @PathVariable Long attachmentRefId) { return R.ok(service.attach(taskId, attachmentRefId)); }
    @DeleteMapping("/{attachmentRefId}")
    public R<Void> remove(@PathVariable Long taskId, @PathVariable Long attachmentRefId) { service.remove(taskId, attachmentRefId); return R.ok(null); }
    @PostMapping("/{attachmentRefId}/download-tickets")
    public R<DownloadTicketResponse> ticket(@PathVariable Long taskId, @PathVariable Long attachmentRefId) { return R.ok(service.createTicket(taskId, attachmentRefId)); }
    @GetMapping("/{attachmentRefId}/download/{ticketNo}")
    public ResponseEntity<Resource> download(@PathVariable Long taskId, @PathVariable Long attachmentRefId, @PathVariable String ticketNo) {
        var file = service.download(taskId, attachmentRefId, ticketNo);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
            .contentLength(file.fileSize()).body(file.resource());
    }
}
