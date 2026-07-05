package com.workforcehub.org.controller;

import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.response.R;
import com.workforcehub.common.exception.BusinessErrorCode;
import com.workforcehub.org.dto.OrgDepartmentTreeNodeResponse;
import com.workforcehub.org.dto.OrgDepartmentSaveRequest;
import com.workforcehub.org.dto.OrgEmploymentStatusUpdateRequest;
import com.workforcehub.org.dto.OrgPositionResponse;
import com.workforcehub.org.dto.OrgPositionSaveRequest;
import com.workforcehub.org.dto.OrgUserAccountUpsertRequest;
import com.workforcehub.org.dto.OrgUserCreateRequest;
import com.workforcehub.org.dto.OrgUserDetailResponse;
import com.workforcehub.org.dto.OrgUserListQuery;
import com.workforcehub.org.dto.OrgUserSummaryResponse;
import com.workforcehub.org.dto.OrgUserUpdateRequest;
import com.workforcehub.org.service.OrgDirectoryService;
import com.workforcehub.org.service.OrgManagementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台组织与人员查询接口
 */
// 组织后台接口：部门/岗位/员工 CRUD，员工状态变更，账号管理
@RestController
@RequestMapping("/api/admin")
public class AdminOrgController {

    private final OrgDirectoryService orgDirectoryService;
    private final OrgManagementService orgManagementService;

    public AdminOrgController(OrgDirectoryService orgDirectoryService,
                              OrgManagementService orgManagementService) {
        this.orgDirectoryService = orgDirectoryService;
        this.orgManagementService = orgManagementService;
    }

    @GetMapping("/departments/tree")
    public R<List<OrgDepartmentTreeNodeResponse>> departmentTree() {
        return R.ok(orgDirectoryService.getDepartmentTree());
    }

    @PostMapping("/departments")
    public R<Long> createDepartment(@Valid @RequestBody OrgDepartmentSaveRequest request) {
        return R.ok(orgManagementService.createDepartment(request));
    }

    @PutMapping("/departments/{deptId}")
    public R<Void> updateDepartment(@PathVariable Long deptId,
                                    @Valid @RequestBody OrgDepartmentSaveRequest request) {
        orgManagementService.updateDepartment(deptId, request);
        return R.ok();
    }

    @DeleteMapping("/departments/{deptId}")
    public ResponseEntity<R<Void>> deleteDepartment(@PathVariable Long deptId) {
        if (!orgManagementService.deleteDepartment(deptId)) {
            return ResponseEntity.status(404).body(R.fail(BusinessErrorCode.NOT_FOUND.code(), "部门不存在"));
        }
        return ResponseEntity.ok(R.ok());
    }

    @GetMapping("/positions")
    public R<List<OrgPositionResponse>> positions() {
        return R.ok(orgDirectoryService.listPositions());
    }

    @PostMapping("/positions")
    public R<Long> createPosition(@Valid @RequestBody OrgPositionSaveRequest request) {
        return R.ok(orgManagementService.createPosition(request));
    }

    @PutMapping("/positions/{positionId}")
    public R<Void> updatePosition(@PathVariable Long positionId,
                                  @Valid @RequestBody OrgPositionSaveRequest request) {
        orgManagementService.updatePosition(positionId, request);
        return R.ok();
    }

    @GetMapping("/users")
    public R<PageResult<OrgUserSummaryResponse>> users(@Valid OrgUserListQuery query) {
        return R.ok(orgDirectoryService.pageUsers(query));
    }

    @GetMapping("/users/org-chart")
    public R<List<OrgUserSummaryResponse>> orgChartUsers() {
        return R.ok(orgDirectoryService.listUsersForOrgChart());
    }

    @PostMapping("/users")
    public R<Long> createUser(@Valid @RequestBody OrgUserCreateRequest request) {
        return R.ok(orgManagementService.createUser(request));
    }

    @PutMapping("/users/{userId}")
    public R<Void> updateUser(@PathVariable Long userId,
                              @Valid @RequestBody OrgUserUpdateRequest request) {
        orgManagementService.updateUser(userId, request);
        return R.ok();
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

    @PutMapping("/users/{userId}/employment-status")
    public R<Void> updateEmploymentStatus(@PathVariable Long userId,
                                          @Valid @RequestBody OrgEmploymentStatusUpdateRequest request) {
        orgManagementService.updateEmploymentStatus(userId, request);
        return R.ok();
    }

    @PutMapping("/users/{userId}/account")
    public R<Void> upsertUserAccount(@PathVariable Long userId,
                                     @Valid @RequestBody OrgUserAccountUpsertRequest request) {
        orgManagementService.upsertUserAccount(userId, request);
        return R.ok();
    }
}
