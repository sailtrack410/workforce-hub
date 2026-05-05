package com.workforcehub.file.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.file.dto.AttachmentRefResponse;
import com.workforcehub.file.dto.ConfirmUploadRequest;
import com.workforcehub.file.dto.CreateDownloadTicketRequest;
import com.workforcehub.file.dto.CreateUploadTicketRequest;
import com.workforcehub.file.dto.DownloadFileResult;
import com.workforcehub.file.dto.DownloadTicketResponse;
import com.workforcehub.file.dto.FilePurgeResponse;
import com.workforcehub.file.dto.FileRestoreResponse;
import com.workforcehub.file.dto.FileTrashRequest;
import com.workforcehub.file.dto.FileTrashResponse;
import com.workforcehub.file.dto.QuotaResponse;
import com.workforcehub.file.dto.UploadTicketResponse;
import com.workforcehub.file.service.FileCenterService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工端文件中心接口。
 */
@RestController
@RequestMapping("/api/employee/files")
public class EmployeeFileController {

    private final FileCenterService fileCenterService;
    private final FileDownloadResponseFactory downloadResponseFactory;

    public EmployeeFileController(FileCenterService fileCenterService,
                                  FileDownloadResponseFactory downloadResponseFactory) {
        this.fileCenterService = fileCenterService;
        this.downloadResponseFactory = downloadResponseFactory;
    }

    @PostMapping("/upload-tickets")
    public R<UploadTicketResponse> createUploadTicket(@Valid @RequestBody CreateUploadTicketRequest request) {
        return R.ok(fileCenterService.createEmployeeUploadTicket(request));
    }

    @PostMapping("/confirm-upload")
    public R<AttachmentRefResponse> confirmUpload(@Valid @RequestBody ConfirmUploadRequest request) {
        return R.ok(fileCenterService.confirmEmployeeUpload(request));
    }

    @GetMapping("/quota")
    public R<QuotaResponse> quota() {
        return R.ok(fileCenterService.getEmployeeQuota());
    }

    @PostMapping("/{attachmentRefId}/download-tickets")
    public R<DownloadTicketResponse> createDownloadTicket(@PathVariable Long attachmentRefId,
                                                          @Valid @RequestBody CreateDownloadTicketRequest request) {
        return R.ok(fileCenterService.createEmployeeDownloadTicket(attachmentRefId, request));
    }

    @GetMapping("/download/{ticketNo}")
    public ResponseEntity<Resource> download(@PathVariable String ticketNo) {
        DownloadFileResult result = fileCenterService.downloadEmployeeFile(ticketNo);
        return downloadResponseFactory.attachment(result);
    }

    @PostMapping("/{attachmentRefId}/trash")
    public R<FileTrashResponse> trash(@PathVariable Long attachmentRefId,
                                      @Valid @RequestBody FileTrashRequest request) {
        return R.ok(fileCenterService.trashEmployeeFile(attachmentRefId, request));
    }

    @PostMapping("/{attachmentRefId}/restore")
    public R<FileRestoreResponse> restore(@PathVariable Long attachmentRefId,
                                          @Valid @RequestBody FileTrashRequest request) {
        return R.ok(fileCenterService.restoreEmployeeFile(attachmentRefId, request));
    }

    @DeleteMapping("/{attachmentRefId}")
    public R<FilePurgeResponse> purge(@PathVariable Long attachmentRefId) {
        return R.ok(fileCenterService.purgeEmployeeFile(attachmentRefId));
    }
}
