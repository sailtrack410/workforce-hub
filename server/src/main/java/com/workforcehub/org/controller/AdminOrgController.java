package com.workforcehub.org.controller;

import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.response.R;
import com.workforcehub.org.dto.OrgDepartmentTreeNodeResponse;
import com.workforcehub.org.dto.OrgPositionResponse;
import com.workforcehub.org.dto.OrgUserDetailResponse;
import com.workforcehub.org.dto.OrgUserListQuery;
import com.workforcehub.org.dto.OrgUserSummaryResponse;
import com.workforcehub.org.service.OrgDirectoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台组织与人员查询接口
 */
@RestController
@RequestMapping("/api/admin")
public class AdminOrgController {

    private final OrgDirectoryService orgDirectoryService;

    public AdminOrgController(OrgDirectoryService orgDirectoryService) {
        this.orgDirectoryService = orgDirectoryService;
    }

    @GetMapping("/departments/tree")
    public R<List<OrgDepartmentTreeNodeResponse>> departmentTree() {
        return R.ok(orgDirectoryService.getDepartmentTree());
    }

    @GetMapping("/positions")
    public R<List<OrgPositionResponse>> positions() {
        return R.ok(orgDirectoryService.listPositions());
    }

    @GetMapping("/users")
    public R<PageResult<OrgUserSummaryResponse>> users(@Valid OrgUserListQuery query) {
        return R.ok(orgDirectoryService.pageUsers(query));
    }

    @GetMapping("/users/{userId}")
    public R<OrgUserDetailResponse> userDetail(@PathVariable Long userId) {
        return R.ok(orgDirectoryService.getUserDetail(userId));
    }

    @GetMapping("/users/{userId}/manager")
    public R<OrgUserSummaryResponse> userManager(@PathVariable Long userId) {
        return R.ok(orgDirectoryService.getManager(userId));
    }

    @GetMapping("/users/{userId}/subordinates")
    public R<List<OrgUserSummaryResponse>> userSubordinates(@PathVariable Long userId) {
        return R.ok(orgDirectoryService.listDirectSubordinates(userId));
    }
}
