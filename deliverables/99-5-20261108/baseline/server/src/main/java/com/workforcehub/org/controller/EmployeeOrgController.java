package com.workforcehub.org.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.org.dto.OrgUserDetailResponse;
import com.workforcehub.org.dto.OrgUserSummaryResponse;
import com.workforcehub.org.dto.UpdateEmployeeProfileRequest;
import com.workforcehub.org.service.OrgDirectoryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 员工端组织与个人档案接口
 */
// 员工组织接口：部门树查询、个人档案查看/更新
@RestController
@RequestMapping("/api/employee/me")
public class EmployeeOrgController {

    private final OrgDirectoryService orgDirectoryService;

    public EmployeeOrgController(OrgDirectoryService orgDirectoryService) {
        this.orgDirectoryService = orgDirectoryService;
    }

    @Operation(summary = "查询个人档案", operationId = "getMyProfile")
    @GetMapping("/profile")
    public R<OrgUserDetailResponse> profile() {
        return R.ok(orgDirectoryService.getCurrentEmployeeProfile());
    }

    @Operation(summary = "更新个人档案", operationId = "updateMyProfile")
    @PutMapping("/profile")
    public R<OrgUserDetailResponse> updateProfile(@Valid @RequestBody UpdateEmployeeProfileRequest request) {
        return R.ok(orgDirectoryService.updateCurrentEmployeeProfile(request));
    }

    @Operation(summary = "查询我的直属上级", operationId = "getMyManager")
    @GetMapping("/manager")
    public R<OrgUserSummaryResponse> manager() {
        return R.ok(orgDirectoryService.getCurrentEmployeeManager());
    }

    @Operation(summary = "查询我的下属", operationId = "listMySubordinates")
    @GetMapping("/subordinates")
    public R<List<OrgUserSummaryResponse>> subordinates() {
        return R.ok(orgDirectoryService.getCurrentEmployeeSubordinates());
    }

    @Operation(summary = "查询我的同事", operationId = "listMyColleagues")
    @GetMapping("/colleagues")
    public R<List<OrgUserSummaryResponse>> colleagues() {
        return R.ok(orgDirectoryService.getCurrentEmployeeColleagues());
    }
}
