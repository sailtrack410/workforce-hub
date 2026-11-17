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
import io.swagger.v3.oas.annotations.Operation;
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
// Outbox 治理接口：投递状态摘要、事件列表、审计日志、批量重试
@RestController
@RequestMapping("/api/admin/im/outbox-events")
public class AdminImOutboxController {

    private final ImOutboxAdminService imOutboxAdminService;

    public AdminImOutboxController(ImOutboxAdminService imOutboxAdminService) {
        this.imOutboxAdminService = imOutboxAdminService;
    }

    @Operation(summary = "分页查询 Outbox 事件", operationId = "pageImOutboxEvents")
    @GetMapping
    public R<PageResult<AdminImOutboxEventResponse>> listEvents(@Valid AdminImOutboxEventQuery query) {
        return R.ok(imOutboxAdminService.pageOutboxEvents(query));
    }

    @Operation(summary = "查询 Outbox 投递摘要", operationId = "getImOutboxSummary")
    @GetMapping("/summary")
    public R<AdminImOutboxSummaryResponse> summarizeEvents() {
        return R.ok(imOutboxAdminService.summarizeOutboxEvents());
    }

    @Operation(summary = "查询 Outbox 失败汇总", operationId = "listImOutboxFailures")
    @GetMapping("/failure-summary")
    public R<List<AdminImOutboxFailureSummaryResponse>> failureSummary() {
        return R.ok(imOutboxAdminService.listOutboxFailureSummary());
    }

    @Operation(summary = "分页查询 Outbox 审计日志", operationId = "pageImOutboxAudits")
    @GetMapping("/audits")
    public R<PageResult<AdminImOutboxAuditResponse>> listAudits(@Valid AdminImOutboxAuditQuery query) {
        return R.ok(imOutboxAdminService.pageOutboxAudits(query));
    }

    @Operation(summary = "批量重试 Outbox 事件", operationId = "batchRetryImOutboxEvents")
    @PostMapping("/batch-retry")
    public R<AdminImOutboxBatchRetryResponse> batchRetryEvents(
            @Valid @RequestBody AdminImOutboxBatchRetryRequest request) {
        return R.ok(imOutboxAdminService.batchRetryOutboxEvents(request));
    }

    @Operation(summary = "手动投递到期事件", operationId = "dispatchImOutboxEvents")
    @PostMapping("/dispatch")
    public R<AdminImOutboxDispatchResponse> dispatchDueEvents(
            @Valid @RequestBody(required = false) AdminImOutboxDispatchRequest request) {
        int batchSize = request == null ? 50 : request.safeBatchSize();
        return R.ok(imOutboxAdminService.dispatchDueEvents(batchSize));
    }

    @Operation(summary = "查询 Outbox 事件详情", operationId = "getImOutboxEvent")
    @GetMapping("/{eventId}")
    public R<AdminImOutboxEventResponse> getEvent(@PathVariable Long eventId) {
        return R.ok(imOutboxAdminService.getOutboxEvent(eventId));
    }

    @Operation(summary = "重试单条 Outbox 事件", operationId = "retryImOutboxEvent")
    @PostMapping("/{eventId}/retry")
    public R<AdminImOutboxEventResponse> retryEvent(@PathVariable Long eventId) {
        return R.ok(imOutboxAdminService.retryOutboxEvent(eventId));
    }
}
