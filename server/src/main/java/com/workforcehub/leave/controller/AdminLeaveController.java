package com.workforcehub.leave.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.leave.dto.AdjustLeaveAccountRequest;
import com.workforcehub.leave.dto.CreateLeaveTypeRequest;
import com.workforcehub.leave.dto.LeaveAccountResponse;
import com.workforcehub.leave.dto.LeaveTypeResponse;
import com.workforcehub.leave.service.LeaveService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台假期治理接口。
 */
@RestController
@RequestMapping("/api/admin/leave")
public class AdminLeaveController {

    private final LeaveService leaveService;

    public AdminLeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    @PostMapping("/types")
    public R<LeaveTypeResponse> createLeaveType(@Valid @RequestBody CreateLeaveTypeRequest request) {
        return R.ok(leaveService.createLeaveType(request));
    }

    @PostMapping("/accounts/adjustments")
    public R<LeaveAccountResponse> adjustAccount(@Valid @RequestBody AdjustLeaveAccountRequest request) {
        return R.ok(leaveService.adjustAccount(request));
    }
}
