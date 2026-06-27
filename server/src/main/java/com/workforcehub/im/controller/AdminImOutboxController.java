package com.workforcehub.im.controller;

import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.response.R;
import com.workforcehub.im.dto.AdminImOutboxAuditQuery;
import com.workforcehub.im.dto.AdminImOutboxAuditResponse;
import com.workforcehub.im.dto.AdminImOutboxBatchRetryRequest;
import com.workforcehub.im.dto.AdminImOutboxBatchRetryResponse;
import com.workforcehub.im.dto.AdminImOutboxDispatchRequest;
import com.workforcehub.im.dto.AdminImOutboxDispatchResponse;
import com.workforcehub.im.dto.AdminImOutboxEventQuery;
import com.workforcehub.im.dto.AdminImOutboxEventResponse;
import com.workforcehub.im.dto.AdminImOutboxFailureSummaryResponse;
import com.workforcehub.im.dto.AdminImOutboxSummaryResponse;
import com.workforcehub.im.service.ImOutboxAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台 IM WebSocket outbox 治理接口。
 */
@RestController
@RequestMapping("/api/admin/im/outbox-events")
public class AdminImOutboxController {

    private final ImOutboxAdminService imOutboxAdminService;

    public AdminImOutboxController(ImOutboxAdminService imOutboxAdminService) {
        this.imOutboxAdminService = imOutboxAdminService;
    }

    @GetMapping
    public R<PageResult<AdminImOutboxEventResponse>> listEvents(@Valid AdminImOutboxEventQuery query) {
        return R.ok(imOutboxAdminService.pageOutboxEvents(query));
    }

    @GetMapping("/summary")
    public R<AdminImOutboxSummaryResponse> summarizeEvents() {
        return R.ok(imOutboxAdminService.summarizeOutboxEvents());
    }

    @GetMapping("/failure-summary")
    public R<List<AdminImOutboxFailureSummaryResponse>> failureSummary() {
        return R.ok(imOutboxAdminService.listOutboxFailureSummary());
    }

    @GetMapping("/audits")
    public R<PageResult<AdminImOutboxAuditResponse>> listAudits(@Valid AdminImOutboxAuditQuery query) {
        return R.ok(imOutboxAdminService.pageOutboxAudits(query));
    }

    @PostMapping("/batch-retry")
    public R<AdminImOutboxBatchRetryResponse> batchRetryEvents(
            @Valid @RequestBody AdminImOutboxBatchRetryRequest request) {
        return R.ok(imOutboxAdminService.batchRetryOutboxEvents(request));
    }

    @PostMapping("/dispatch")
    public R<AdminImOutboxDispatchResponse> dispatchDueEvents(
            @Valid @RequestBody(required = false) AdminImOutboxDispatchRequest request) {
        int batchSize = request == null ? 50 : request.safeBatchSize();
        return R.ok(imOutboxAdminService.dispatchDueEvents(batchSize));
    }

    @GetMapping("/{eventId}")
    public R<AdminImOutboxEventResponse> getEvent(@PathVariable Long eventId) {
        return R.ok(imOutboxAdminService.getOutboxEvent(eventId));
    }

    @PostMapping("/{eventId}/retry")
    public R<AdminImOutboxEventResponse> retryEvent(@PathVariable Long eventId) {
        return R.ok(imOutboxAdminService.retryOutboxEvent(eventId));
    }
}
