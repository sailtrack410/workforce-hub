package com.workforcehub.leave.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.leave.dto.AdjustLeaveAccountRequest;
import com.workforcehub.leave.dto.CreateLeaveTypeRequest;
import com.workforcehub.leave.dto.LeaveAccountResponse;
import com.workforcehub.leave.dto.LeaveTypeResponse;
import com.workforcehub.leave.service.LeaveService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
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
 * 后台假期治理接口。
 */
// 假期后台接口：假期类型 CRUD、余额调整、账户查询
@RestController
@RequestMapping("/api/admin/leave")
public class AdminLeaveController {

    private final LeaveService leaveService;

    public AdminLeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    @Operation(summary = "查询假期类型列表", operationId = "listAdminLeaveTypes")
    @GetMapping("/types")
    public R<List<LeaveTypeResponse>> listTypes() {
        return R.ok(leaveService.listLeaveTypes());
    }

    @Operation(summary = "查询假期账户列表", operationId = "listAdminLeaveAccounts")
    @GetMapping("/accounts")
    public R<List<LeaveAccountResponse>> listAccounts(@RequestParam(required = false) Long userId,
                                                      @RequestParam(required = false) Long leaveTypeId) {
        return R.ok(leaveService.listAdminAccounts(userId, leaveTypeId));
    }

    @Operation(summary = "创建假期类型", operationId = "createLeaveType")
    @PostMapping("/types")
    public R<LeaveTypeResponse> createLeaveType(@Valid @RequestBody CreateLeaveTypeRequest request) {
        return R.ok(leaveService.createLeaveType(request));
    }

    @Operation(summary = "更新假期类型", operationId = "updateLeaveType")
    @PutMapping("/types/{leaveTypeId}")
    public R<LeaveTypeResponse> updateLeaveType(@PathVariable Long leaveTypeId,
                                                @Valid @RequestBody CreateLeaveTypeRequest request) {
        return R.ok(leaveService.updateLeaveType(leaveTypeId, request));
    }

    @Operation(summary = "删除假期类型", operationId = "deleteLeaveType")
    @DeleteMapping("/types/{leaveTypeId}")
    public R<Void> deleteLeaveType(@PathVariable Long leaveTypeId) {
        leaveService.deleteLeaveType(leaveTypeId);
        return R.ok(null);
    }

    @Operation(summary = "调整假期余额", operationId = "adjustLeaveAccount")
    @PostMapping("/accounts/adjustments")
    public R<LeaveAccountResponse> adjustAccount(@Valid @RequestBody AdjustLeaveAccountRequest request) {
        return R.ok(leaveService.adjustAccount(request));
    }
}
