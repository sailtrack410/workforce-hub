package com.workforcehub.leave.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.leave.dto.LeaveAccountResponse;
import com.workforcehub.leave.service.LeaveService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 员工端假期账户接口。
 */
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
}
