package com.workforcehub.org.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.org.dto.OrgUserDetailResponse;
import com.workforcehub.org.dto.OrgUserSummaryResponse;
import com.workforcehub.org.dto.UpdateEmployeeProfileRequest;
import com.workforcehub.org.service.OrgDirectoryService;
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
@RestController
@RequestMapping("/api/employee/me")
public class EmployeeOrgController {

    private final OrgDirectoryService orgDirectoryService;

    public EmployeeOrgController(OrgDirectoryService orgDirectoryService) {
        this.orgDirectoryService = orgDirectoryService;
    }

    @GetMapping("/profile")
    public R<OrgUserDetailResponse> profile() {
        return R.ok(orgDirectoryService.getCurrentEmployeeProfile());
    }

    @PutMapping("/profile")
    public R<OrgUserDetailResponse> updateProfile(@Valid @RequestBody UpdateEmployeeProfileRequest request) {
        return R.ok(orgDirectoryService.updateCurrentEmployeeProfile(request));
    }

    @GetMapping("/manager")
    public R<OrgUserSummaryResponse> manager() {
        return R.ok(orgDirectoryService.getCurrentEmployeeManager());
    }

    @GetMapping("/subordinates")
    public R<List<OrgUserSummaryResponse>> subordinates() {
        return R.ok(orgDirectoryService.getCurrentEmployeeSubordinates());
    }

    @GetMapping("/colleagues")
    public R<List<OrgUserSummaryResponse>> colleagues() {
        return R.ok(orgDirectoryService.getCurrentEmployeeColleagues());
    }
}
