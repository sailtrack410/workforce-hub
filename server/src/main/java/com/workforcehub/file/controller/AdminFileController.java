package com.workforcehub.file.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.common.response.PageResult;
import com.workforcehub.file.dto.AdminFileResponse;
import com.workforcehub.file.dto.AttachmentRefResponse;
import com.workforcehub.file.dto.ConfirmUploadRequest;
import com.workforcehub.file.dto.CreateDownloadTicketRequest;
import com.workforcehub.file.dto.CreateQuotaPolicyRequest;
import com.workforcehub.file.dto.CreateUploadTicketRequest;
import com.workforcehub.file.dto.DownloadFileResult;
import com.workforcehub.file.dto.DownloadTicketResponse;
import com.workforcehub.file.dto.FilePurgeResponse;
import com.workforcehub.file.dto.FileRestoreResponse;
import com.workforcehub.file.dto.FileTrashRequest;
import com.workforcehub.file.dto.FileTrashResponse;
import com.workforcehub.file.dto.QuotaPolicyResponse;
import com.workforcehub.file.dto.CleanupJobRunResponse;
import com.workforcehub.file.dto.RunCleanupJobRequest;
import com.workforcehub.file.dto.StorageSummaryResponse;
import com.workforcehub.file.dto.UpdateQuotaPolicyRequest;
import com.workforcehub.file.dto.UploadTicketResponse;
import com.workforcehub.file.service.FileCleanupJobService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台文件中心治理接口。
 */
// 后台文件接口：配额策略 CRUD、清理任务触发、存储摘要、后台文件列表
@RestController
@RequestMapping("/api/admin/files")
public class AdminFileController {

    private final FileQueryService fileQueryService;
    private final FileUploadTicketService fileUploadTicketService;
    private final FileUploadConfirmService fileUploadConfirmService;
    private final FileDownloadService fileDownloadService;
    private final FileQuotaPolicyService fileQuotaPolicyService;
    private final FileLifecycleService fileLifecycleService;
    private final FileCleanupJobService fileCleanupJobService;
    private final FileDownloadResponseFactory downloadResponseFactory;

    public AdminFileController(FileQueryService fileQueryService,
                               FileUploadTicketService fileUploadTicketService,
                               FileUploadConfirmService fileUploadConfirmService,
                               FileDownloadService fileDownloadService,
                               FileQuotaPolicyService fileQuotaPolicyService,
                               FileLifecycleService fileLifecycleService,
                               FileCleanupJobService fileCleanupJobService,
                               FileDownloadResponseFactory downloadResponseFactory) {
        this.fileQueryService = fileQueryService;
        this.fileUploadTicketService = fileUploadTicketService;
        this.fileUploadConfirmService = fileUploadConfirmService;
        this.fileDownloadService = fileDownloadService;
        this.fileQuotaPolicyService = fileQuotaPolicyService;
        this.fileLifecycleService = fileLifecycleService;
        this.fileCleanupJobService = fileCleanupJobService;
        this.downloadResponseFactory = downloadResponseFactory;
    }

    @GetMapping
    public R<PageResult<AdminFileResponse>> listFiles(@RequestParam(defaultValue = "1") long pageNo,
                                                      @RequestParam(defaultValue = "20") long pageSize) {
        return R.ok(fileQueryService.listAdminFiles(pageNo, pageSize, false));
    }

    @GetMapping("/recycle-bin")
    public R<PageResult<AdminFileResponse>> listRecycleBin(@RequestParam(defaultValue = "1") long pageNo,
                                                           @RequestParam(defaultValue = "20") long pageSize) {
        return R.ok(fileQueryService.listAdminFiles(pageNo, pageSize, true));
    }

    @PostMapping("/upload-tickets")
    public R<UploadTicketResponse> createUploadTicket(@Valid @RequestBody CreateUploadTicketRequest request) {
        return R.ok(fileUploadTicketService.createAdminUploadTicket(request));
    }

    @PostMapping("/confirm-upload")
    public R<AttachmentRefResponse> confirmUpload(@Valid @RequestBody ConfirmUploadRequest request) {
        return R.ok(fileUploadConfirmService.confirmAdminUpload(request));
    }

    @GetMapping("/storage-summary")
    public R<StorageSummaryResponse> storageSummary() {
        return R.ok(fileQuotaPolicyService.getStorageSummary());
    }

    @PostMapping("/{attachmentRefId}/download-tickets")
    public R<DownloadTicketResponse> createDownloadTicket(@PathVariable Long attachmentRefId,
                                                          @Valid @RequestBody CreateDownloadTicketRequest request) {
        return R.ok(fileDownloadService.createAdminDownloadTicket(attachmentRefId, request));
    }

    @GetMapping("/download/{ticketNo}")
    public ResponseEntity<Resource> download(@PathVariable String ticketNo) {
        DownloadFileResult result = fileDownloadService.downloadAdminFile(ticketNo);
        return downloadResponseFactory.attachment(result);
    }

    @PostMapping("/{attachmentRefId}/trash")
    public R<FileTrashResponse> trash(@PathVariable Long attachmentRefId,
                                      @Valid @RequestBody FileTrashRequest request) {
        return R.ok(fileLifecycleService.trashAdminFile(attachmentRefId, request));
    }

    @PostMapping("/{attachmentRefId}/restore")
    public R<FileRestoreResponse> restore(@PathVariable Long attachmentRefId,
                                          @Valid @RequestBody FileTrashRequest request) {
        return R.ok(fileLifecycleService.restoreAdminFile(attachmentRefId, request));
    }

    @DeleteMapping("/{attachmentRefId}")
    public R<FilePurgeResponse> purge(@PathVariable Long attachmentRefId) {
        return R.ok(fileLifecycleService.purgeAdminFile(attachmentRefId));
    }

    @PostMapping("/cleanup-jobs/run")
    public R<CleanupJobRunResponse> runCleanupJob(@Valid @RequestBody RunCleanupJobRequest request) {
        return R.ok(fileCleanupJobService.runCleanupJob(request));
    }

    @GetMapping("/quota-policies")
    public R<List<QuotaPolicyResponse>> listQuotaPolicies(@RequestParam(required = false) String scopeType) {
        return R.ok(fileQuotaPolicyService.listQuotaPolicies(scopeType));
    }

    @PostMapping("/quota-policies")
    public R<QuotaPolicyResponse> createQuotaPolicy(@Valid @RequestBody CreateQuotaPolicyRequest request) {
        return R.ok(fileQuotaPolicyService.createQuotaPolicy(request));
    }

    @PutMapping("/quota-policies/{policyId}")
    public R<QuotaPolicyResponse> updateQuotaPolicy(@PathVariable Long policyId,
                                                    @Valid @RequestBody UpdateQuotaPolicyRequest request) {
        return R.ok(fileQuotaPolicyService.updateQuotaPolicy(policyId, request));
    }
}
