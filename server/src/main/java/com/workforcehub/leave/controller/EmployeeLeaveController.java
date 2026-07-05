package com.workforcehub.leave.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.leave.dto.LeaveAccountResponse;
import com.workforcehub.leave.dto.LeaveTypeResponse;
import com.workforcehub.leave.service.LeaveService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 员工端假期账户接口。
 */
// 员工假期接口：假期类型列表、余额查询
@RestController
@RequestMapping("/api/employee/leave")
public class EmployeeLeaveController {

    private final LeaveService leaveService;

    public EmployeeLeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    @GetMapping("/accounts")
    public R<List<LeaveAccountResponse>> accounts() {
        return R.ok(leaveService.listCurrentEmployeeAccounts());
    }

    @GetMapping("/types")
    public R<List<LeaveTypeResponse>> types() {
        return R.ok(leaveService.listLeaveTypes());
    }
}
