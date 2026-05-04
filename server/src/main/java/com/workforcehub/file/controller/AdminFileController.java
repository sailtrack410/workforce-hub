package com.workforcehub.file.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.file.dto.AttachmentRefResponse;
import com.workforcehub.file.dto.ConfirmUploadRequest;
import com.workforcehub.file.dto.CreateQuotaPolicyRequest;
import com.workforcehub.file.dto.CreateUploadTicketRequest;
import com.workforcehub.file.dto.QuotaPolicyResponse;
import com.workforcehub.file.dto.StorageSummaryResponse;
import com.workforcehub.file.dto.UpdateQuotaPolicyRequest;
import com.workforcehub.file.dto.UploadTicketResponse;
import com.workforcehub.file.service.FileCenterService;
import jakarta.validation.Valid;
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
