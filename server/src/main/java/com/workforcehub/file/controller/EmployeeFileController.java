package com.workforcehub.file.controller;

import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.response.R;
import com.workforcehub.file.dto.AttachmentRefResponse;
import com.workforcehub.file.dto.ConfirmUploadRequest;
import com.workforcehub.file.dto.CreateDownloadTicketRequest;
import com.workforcehub.file.dto.CreateUploadTicketRequest;
import com.workforcehub.file.dto.DownloadFileResult;
import com.workforcehub.file.dto.DownloadTicketResponse;
import com.workforcehub.file.dto.EmployeeFileResponse;
import com.workforcehub.file.dto.FilePurgeResponse;
import com.workforcehub.file.dto.FileRestoreResponse;
import com.workforcehub.file.dto.FileTrashRequest;
import com.workforcehub.file.dto.FileTrashResponse;
import com.workforcehub.file.dto.QuotaResponse;
import com.workforcehub.file.dto.UploadTicketResponse;
import com.workforcehub.file.service.FileDownloadService;
import com.workforcehub.file.service.FileLifecycleService;
import com.workforcehub.file.service.FileQueryService;
import com.workforcehub.file.service.FileQuotaPolicyService;
import com.workforcehub.file.service.FileUploadConfirmService;
import com.workforcehub.file.service.FileUploadTicketService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工端文件中心接口。
 */
@RestController
@RequestMapping("/api/employee/files")
public class EmployeeFileController {

    private final FileQueryService fileQueryService;
    private final FileUploadTicketService fileUploadTicketService;
    private final FileUploadConfirmService fileUploadConfirmService;
    private final FileDownloadService fileDownloadService;
    private final FileQuotaPolicyService fileQuotaPolicyService;
    private final FileLifecycleService fileLifecycleService;
    private final FileDownloadResponseFactory downloadResponseFactory;

    public EmployeeFileController(FileQueryService fileQueryService,
                                  FileUploadTicketService fileUploadTicketService,
                                  FileUploadConfirmService fileUploadConfirmService,
                                  FileDownloadService fileDownloadService,
                                  FileQuotaPolicyService fileQuotaPolicyService,
                                  FileLifecycleService fileLifecycleService,
                                  FileDownloadResponseFactory downloadResponseFactory) {
        this.fileQueryService = fileQueryService;
        this.fileUploadTicketService = fileUploadTicketService;
        this.fileUploadConfirmService = fileUploadConfirmService;
        this.fileDownloadService = fileDownloadService;
        this.fileQuotaPolicyService = fileQuotaPolicyService;
        this.fileLifecycleService = fileLifecycleService;
        this.downloadResponseFactory = downloadResponseFactory;
    }

    @GetMapping
    public R<PageResult<EmployeeFileResponse>> listFiles(@RequestParam(defaultValue = "1") long pageNo,
                                                         @RequestParam(defaultValue = "20") long pageSize) {
        return R.ok(fileQueryService.listEmployeeFiles(pageNo, pageSize, false));
    }

    @GetMapping("/recycle-bin")
    public R<PageResult<EmployeeFileResponse>> listRecycleBin(@RequestParam(defaultValue = "1") long pageNo,
                                                              @RequestParam(defaultValue = "20") long pageSize) {
        return R.ok(fileQueryService.listEmployeeFiles(pageNo, pageSize, true));
    }

    @PostMapping("/upload-tickets")
    public R<UploadTicketResponse> createUploadTicket(@Valid @RequestBody CreateUploadTicketRequest request) {
        return R.ok(fileUploadTicketService.createEmployeeUploadTicket(request));
    }

    @PostMapping("/confirm-upload")
    public R<AttachmentRefResponse> confirmUpload(@Valid @RequestBody ConfirmUploadRequest request) {
        return R.ok(fileUploadConfirmService.confirmEmployeeUpload(request));
    }

    @GetMapping("/quota")
    public R<QuotaResponse> quota() {
        return R.ok(fileQuotaPolicyService.getEmployeeQuota());
    }

    @PostMapping("/{attachmentRefId}/download-tickets")
    public R<DownloadTicketResponse> createDownloadTicket(@PathVariable Long attachmentRefId,
                                                          @Valid @RequestBody CreateDownloadTicketRequest request) {
        return R.ok(fileDownloadService.createEmployeeDownloadTicket(attachmentRefId, request));
    }

    @GetMapping("/download/{ticketNo}")
    public ResponseEntity<Resource> download(@PathVariable String ticketNo) {
        DownloadFileResult result = fileDownloadService.downloadEmployeeFile(ticketNo);
        return downloadResponseFactory.attachment(result);
    }

    @PostMapping("/{attachmentRefId}/trash")
    public R<FileTrashResponse> trash(@PathVariable Long attachmentRefId,
                                      @Valid @RequestBody FileTrashRequest request) {
        return R.ok(fileLifecycleService.trashEmployeeFile(attachmentRefId, request));
    }

    @PostMapping("/{attachmentRefId}/restore")
    public R<FileRestoreResponse> restore(@PathVariable Long attachmentRefId,
                                          @Valid @RequestBody FileTrashRequest request) {
        return R.ok(fileLifecycleService.restoreEmployeeFile(attachmentRefId, request));
    }

    @DeleteMapping("/{attachmentRefId}")
    public R<FilePurgeResponse> purge(@PathVariable Long attachmentRefId) {
        return R.ok(fileLifecycleService.purgeEmployeeFile(attachmentRefId));
    }
}
