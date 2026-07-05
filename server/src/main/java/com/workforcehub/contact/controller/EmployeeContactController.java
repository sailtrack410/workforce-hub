package com.workforcehub.contact.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.contact.dto.FriendRequest;
import com.workforcehub.contact.service.ContactFriendService;
import com.workforcehub.org.dto.OrgDepartmentTreeNodeResponse;
import com.workforcehub.org.dto.OrgUserSummaryResponse;
import com.workforcehub.org.service.OrgDirectoryService;
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

    @GetMapping("/friends")
    public R<List<OrgUserSummaryResponse>> listFriends() {
        return R.ok(contactFriendService.listCurrentFriends());
    }

    @GetMapping("/friends/requests")
    public R<List<OrgUserSummaryResponse>> listFriendRequests() {
        return R.ok(contactFriendService.listCurrentFriendRequests());
    }

    @PostMapping("/friends/{userId}")
    public R<OrgUserSummaryResponse> addFriend(@PathVariable Long userId,
                                               @Valid @RequestBody(required = false) FriendRequest request) {
        String remark = request == null ? null : request.getRemark();
        return R.ok(contactFriendService.addCurrentFriend(userId, remark));
    }

    @PostMapping("/friends/{userId}/requests")
    public R<OrgUserSummaryResponse> sendFriendRequest(@PathVariable Long userId,
                                                       @Valid @RequestBody(required = false) FriendRequest request) {
        String remark = request == null ? null : request.getRemark();
        return R.ok(contactFriendService.addCurrentFriend(userId, remark));
    }

    @PostMapping("/friends/requests/{userId}/accept")
    public R<OrgUserSummaryResponse> acceptFriendRequest(@PathVariable Long userId) {
        return R.ok(contactFriendService.acceptFriendRequest(userId));
    }

    @DeleteMapping("/friends/{userId}")
    public R<Void> removeFriend(@PathVariable Long userId) {
        contactFriendService.removeCurrentFriend(userId);
        return R.ok(null);
    }

    @PutMapping("/friends/{userId}")
    public R<OrgUserSummaryResponse> updateFriendRemark(@PathVariable Long userId,
                                                        @Valid @RequestBody FriendRequest request) {
        return R.ok(contactFriendService.updateCurrentFriendRemark(userId, request.getRemark()));
    }

    @GetMapping("/dept-tree")
    public R<List<OrgDepartmentTreeNodeResponse>> deptTree() {
        return R.ok(orgDirectoryService.getDepartmentTree());
    }
}
