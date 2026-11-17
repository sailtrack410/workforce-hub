package com.workforcehub.contact.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.contact.dto.FriendRequest;
import com.workforcehub.contact.service.ContactFriendService;
import com.workforcehub.org.dto.OrgDepartmentTreeNodeResponse;
import com.workforcehub.org.dto.OrgUserSummaryResponse;
import com.workforcehub.org.service.OrgDirectoryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 员工端通讯录接口：好友关系 + 部门树
 */
// 通讯录接口：好友列表、好友请求发送/接受/拒绝、部门树查询
@RestController
@RequestMapping("/api/employee/contacts")
public class EmployeeContactController {

    private final ContactFriendService contactFriendService;
    private final OrgDirectoryService orgDirectoryService;

    public EmployeeContactController(ContactFriendService contactFriendService,
                                     OrgDirectoryService orgDirectoryService) {
        this.contactFriendService = contactFriendService;
        this.orgDirectoryService = orgDirectoryService;
    }

    @Operation(summary = "查询好友列表", operationId = "listContactFriends")
    @GetMapping("/friends")
    public R<List<OrgUserSummaryResponse>> listFriends() {
        return R.ok(contactFriendService.listCurrentFriends());
    }

    @Operation(summary = "查询好友请求列表", operationId = "listFriendRequests")
    @GetMapping("/friends/requests")
    public R<List<OrgUserSummaryResponse>> listFriendRequests() {
        return R.ok(contactFriendService.listCurrentFriendRequests());
    }

    @Operation(summary = "添加好友", operationId = "addFriend")
    @PostMapping("/friends/{userId}")
    public R<OrgUserSummaryResponse> addFriend(@PathVariable Long userId,
                                               @Valid @RequestBody(required = false) FriendRequest request) {
        String remark = request == null ? null : request.getRemark();
        return R.ok(contactFriendService.addCurrentFriend(userId, remark));
    }

    @Operation(summary = "发送好友请求", operationId = "sendFriendRequest")
    @PostMapping("/friends/{userId}/requests")
    public R<OrgUserSummaryResponse> sendFriendRequest(@PathVariable Long userId,
                                                       @Valid @RequestBody(required = false) FriendRequest request) {
        String remark = request == null ? null : request.getRemark();
        return R.ok(contactFriendService.addCurrentFriend(userId, remark));
    }

    @Operation(summary = "接受好友请求", operationId = "acceptFriendRequest")
    @PostMapping("/friends/requests/{userId}/accept")
    public R<OrgUserSummaryResponse> acceptFriendRequest(@PathVariable Long userId) {
        return R.ok(contactFriendService.acceptFriendRequest(userId));
    }

    @Operation(summary = "删除好友", operationId = "removeFriend")
    @DeleteMapping("/friends/{userId}")
    public R<Void> removeFriend(@PathVariable Long userId) {
        contactFriendService.removeCurrentFriend(userId);
        return R.ok(null);
    }

    @Operation(summary = "修改好友备注", operationId = "updateFriendRemark")
    @PutMapping("/friends/{userId}")
    public R<OrgUserSummaryResponse> updateFriendRemark(@PathVariable Long userId,
                                                        @Valid @RequestBody FriendRequest request) {
        return R.ok(contactFriendService.updateCurrentFriendRemark(userId, request.getRemark()));
    }

    @Operation(summary = "查询部门树", operationId = "getContactDepartmentTree")
    @GetMapping("/dept-tree")
    public R<List<OrgDepartmentTreeNodeResponse>> deptTree() {
        return R.ok(orgDirectoryService.getDepartmentTree());
    }
}
