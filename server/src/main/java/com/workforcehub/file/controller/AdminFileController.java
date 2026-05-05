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
import com.workforcehub.file.dto.StorageSummaryResponse;
import com.workforcehub.file.dto.UpdateQuotaPolicyRequest;
import com.workforcehub.file.dto.UploadTicketResponse;
import com.workforcehub.file.service.FileCenterService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
@RestController
@RequestMapping("/api/admin/files")
public class AdminFileController {

    private final FileCenterService fileCenterService;

    public AdminFileController(FileCenterService fileCenterService) {
        this.fileCenterService = fileCenterService;
    }

    @GetMapping
    public R<PageResult<AdminFileResponse>> listFiles(@RequestParam(defaultValue = "1") long pageNo,
                                                      @RequestParam(defaultValue = "20") long pageSize) {
        return R.ok(fileCenterService.listAdminFiles(pageNo, pageSize, false));
    }

    @GetMapping("/recycle-bin")
    public R<PageResult<AdminFileResponse>> listRecycleBin(@RequestParam(defaultValue = "1") long pageNo,
                                                           @RequestParam(defaultValue = "20") long pageSize) {
        return R.ok(fileCenterService.listAdminFiles(pageNo, pageSize, true));
    }

    @PostMapping("/upload-tickets")
    public R<UploadTicketResponse> createUploadTicket(@Valid @RequestBody CreateUploadTicketRequest request) {
        return R.ok(fileCenterService.createAdminUploadTicket(request));
    }

    @PostMapping("/confirm-upload")
    public R<AttachmentRefResponse> confirmUpload(@Valid @RequestBody ConfirmUploadRequest request) {
        return R.ok(fileCenterService.confirmAdminUpload(request));
    }

    @GetMapping("/storage-summary")
    public R<StorageSummaryResponse> storageSummary() {
        return R.ok(fileCenterService.getStorageSummary());
    }

    @PostMapping("/{attachmentRefId}/download-tickets")
    public R<DownloadTicketResponse> createDownloadTicket(@PathVariable Long attachmentRefId,
                                                          @Valid @RequestBody CreateDownloadTicketRequest request) {
        return R.ok(fileCenterService.createAdminDownloadTicket(attachmentRefId, request));
    }

    @GetMapping("/download/{ticketNo}")
    public ResponseEntity<Resource> download(@PathVariable String ticketNo) {
        DownloadFileResult result = fileCenterService.downloadAdminFile(ticketNo);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (result.mimeType() != null && !result.mimeType().isBlank()) {
            mediaType = MediaType.parseMediaType(result.mimeType());
        }
        return ResponseEntity.ok()
            .contentType(mediaType)
            .contentLength(result.fileSize())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(result.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                .build()
                .toString())
            .body(result.resource());
    }

    @PostMapping("/{attachmentRefId}/trash")
    public R<FileTrashResponse> trash(@PathVariable Long attachmentRefId,
                                      @Valid @RequestBody FileTrashRequest request) {
        return R.ok(fileCenterService.trashAdminFile(attachmentRefId, request));
    }

    @PostMapping("/{attachmentRefId}/restore")
    public R<FileRestoreResponse> restore(@PathVariable Long attachmentRefId,
                                          @Valid @RequestBody FileTrashRequest request) {
        return R.ok(fileCenterService.restoreAdminFile(attachmentRefId, request));
    }

    @DeleteMapping("/{attachmentRefId}")
    public R<FilePurgeResponse> purge(@PathVariable Long attachmentRefId) {
        return R.ok(fileCenterService.purgeAdminFile(attachmentRefId));
    }

    @GetMapping("/quota-policies")
    public R<List<QuotaPolicyResponse>> listQuotaPolicies(@RequestParam(required = false) String scopeType) {
        return R.ok(fileCenterService.listQuotaPolicies(scopeType));
    }

    @PostMapping("/quota-policies")
    public R<QuotaPolicyResponse> createQuotaPolicy(@Valid @RequestBody CreateQuotaPolicyRequest request) {
        return R.ok(fileCenterService.createQuotaPolicy(request));
    }

    @PutMapping("/quota-policies/{policyId}")
    public R<QuotaPolicyResponse> updateQuotaPolicy(@PathVariable Long policyId,
                                                    @Valid @RequestBody UpdateQuotaPolicyRequest request) {
        return R.ok(fileCenterService.updateQuotaPolicy(policyId, request));
    }
}
