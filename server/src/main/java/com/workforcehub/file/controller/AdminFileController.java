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
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "分页查询企业文件", operationId = "pageAdminFiles")
    @GetMapping
    public R<PageResult<AdminFileResponse>> listFiles(@RequestParam(defaultValue = "1") long pageNo,
                                                      @RequestParam(defaultValue = "20") long pageSize,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String ownerType,
                                                      @RequestParam(required = false) String uploaderType,
                                                      @RequestParam(required = false) Long uploaderId,
                                                      @RequestParam(required = false) String scanStatus,
                                                      @RequestParam(defaultValue = "ACTIVE") String lifecycleStatus) {
        return R.ok(fileQueryService.listEnterpriseFiles(
            pageNo, pageSize, keyword, ownerType, uploaderType, uploaderId, scanStatus, lifecycleStatus
        ));
    }

    @Operation(summary = "分页查询回收站文件", operationId = "pageAdminRecycleFiles")
    @GetMapping("/recycle-bin")
    public R<PageResult<AdminFileResponse>> listRecycleBin(@RequestParam(defaultValue = "1") long pageNo,
                                                           @RequestParam(defaultValue = "20") long pageSize) {
        return R.ok(fileQueryService.listAdminFiles(pageNo, pageSize, true));
    }

    @Operation(summary = "创建上传凭证", operationId = "createAdminUploadTicket")
    @PostMapping("/upload-tickets")
    public R<UploadTicketResponse> createUploadTicket(@Valid @RequestBody CreateUploadTicketRequest request) {
        return R.ok(fileUploadTicketService.createAdminUploadTicket(request));
    }

    @Operation(summary = "确认文件上传", operationId = "confirmAdminUpload")
    @PostMapping("/confirm-upload")
    public R<AttachmentRefResponse> confirmUpload(@Valid @RequestBody ConfirmUploadRequest request) {
        return R.ok(fileUploadConfirmService.confirmAdminUpload(request));
    }

    @Operation(summary = "查询存储摘要", operationId = "getAdminStorageSummary")
    @GetMapping("/storage-summary")
    public R<StorageSummaryResponse> storageSummary() {
        return R.ok(fileQuotaPolicyService.getStorageSummary());
    }

    @Operation(summary = "创建下载凭证", operationId = "createAdminDownloadTicket")
    @PostMapping("/{attachmentRefId}/download-tickets")
    public R<DownloadTicketResponse> createDownloadTicket(@PathVariable Long attachmentRefId,
                                                          @Valid @RequestBody CreateDownloadTicketRequest request) {
        return R.ok(fileDownloadService.createAdminDownloadTicket(attachmentRefId, request));
    }

    @Operation(summary = "下载文件", operationId = "downloadAdminFile")
    @GetMapping("/download/{ticketNo}")
    public ResponseEntity<Resource> download(@PathVariable String ticketNo) {
        DownloadFileResult result = fileDownloadService.downloadAdminFile(ticketNo);
        return downloadResponseFactory.attachment(result);
    }

    @Operation(summary = "文件移入回收站", operationId = "trashAdminFile")
    @PostMapping("/{attachmentRefId}/trash")
    public R<FileTrashResponse> trash(@PathVariable Long attachmentRefId,
                                      @Valid @RequestBody FileTrashRequest request) {
        return R.ok(fileLifecycleService.trashAdminFile(attachmentRefId, request));
    }

    @Operation(summary = "恢复回收站文件", operationId = "restoreAdminFile")
    @PostMapping("/{attachmentRefId}/restore")
    public R<FileRestoreResponse> restore(@PathVariable Long attachmentRefId,
                                          @Valid @RequestBody FileTrashRequest request) {
        return R.ok(fileLifecycleService.restoreAdminFile(attachmentRefId, request));
    }

    @Operation(summary = "彻底删除文件", operationId = "purgeAdminFile")
    @DeleteMapping("/{attachmentRefId}")
    public R<FilePurgeResponse> purge(@PathVariable Long attachmentRefId) {
        return R.ok(fileLifecycleService.purgeAdminFile(attachmentRefId));
    }

    @Operation(summary = "执行文件清理任务", operationId = "runCleanupJob")
    @PostMapping("/cleanup-jobs/run")
    public R<CleanupJobRunResponse> runCleanupJob(@Valid @RequestBody RunCleanupJobRequest request) {
        return R.ok(fileCleanupJobService.runCleanupJob(request));
    }

    @Operation(summary = "查询配额策略列表", operationId = "listQuotaPolicies")
    @GetMapping("/quota-policies")
    public R<List<QuotaPolicyResponse>> listQuotaPolicies(@RequestParam(required = false) String scopeType) {
        return R.ok(fileQuotaPolicyService.listQuotaPolicies(scopeType));
    }

    @Operation(summary = "创建配额策略", operationId = "createQuotaPolicy")
    @PostMapping("/quota-policies")
    public R<QuotaPolicyResponse> createQuotaPolicy(@Valid @RequestBody CreateQuotaPolicyRequest request) {
        return R.ok(fileQuotaPolicyService.createQuotaPolicy(request));
    }

    @Operation(summary = "更新配额策略", operationId = "updateQuotaPolicy")
    @PutMapping("/quota-policies/{policyId}")
    public R<QuotaPolicyResponse> updateQuotaPolicy(@PathVariable Long policyId,
                                                    @Valid @RequestBody UpdateQuotaPolicyRequest request) {
        return R.ok(fileQuotaPolicyService.updateQuotaPolicy(policyId, request));
    }
}
