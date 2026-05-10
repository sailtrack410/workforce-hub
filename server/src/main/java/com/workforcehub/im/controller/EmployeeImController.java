package com.workforcehub.im.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.im.dto.CreateGroupConversationRequest;
import com.workforcehub.im.dto.GroupMemberUserIdsRequest;
import com.workforcehub.im.dto.ImConversationMemberResponse;
import com.workforcehub.im.dto.ImConversationSummaryResponse;
import com.workforcehub.im.dto.ImMessageResponse;
import com.workforcehub.im.dto.SendImMessageRequest;
import com.workforcehub.im.dto.StartPrivateConversationRequest;
import com.workforcehub.im.dto.UpdateReadPositionRequest;
import com.workforcehub.im.service.ImService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 员工端 IM 会话接口。
 */
@RestController
@RequestMapping("/api/employee/im")
public class EmployeeImController {

    private final ImService imService;

    public EmployeeImController(ImService imService) {
        this.imService = imService;
    }

    @GetMapping("/conversations")
    public R<List<ImConversationSummaryResponse>> conversations() {
        return R.ok(imService.listCurrentEmployeeConversations());
    }

    @PostMapping("/private-conversations")
    public R<ImConversationSummaryResponse> startPrivateConversation(
        @Valid @RequestBody StartPrivateConversationRequest request
    ) {
        return R.ok(imService.startCurrentEmployeePrivateConversation(request));
    }

    @PostMapping("/group-conversations")
    public R<ImConversationSummaryResponse> createGroupConversation(
        @Valid @RequestBody CreateGroupConversationRequest request
    ) {
        return R.ok(imService.createCurrentEmployeeGroupConversation(request));
    }

    @PostMapping("/group-conversations/{conversationId}/members")
    public R<List<ImConversationMemberResponse>> addGroupMembers(
        @PathVariable Long conversationId,
        @Valid @RequestBody GroupMemberUserIdsRequest request
    ) {
        return R.ok(imService.addCurrentEmployeeGroupMembers(conversationId, request));
    }

    @PostMapping("/group-conversations/{conversationId}/members/remove")
    public R<List<ImConversationMemberResponse>> removeGroupMembers(
        @PathVariable Long conversationId,
        @Valid @RequestBody GroupMemberUserIdsRequest request
    ) {
        return R.ok(imService.removeCurrentEmployeeGroupMembers(conversationId, request));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public R<List<ImMessageResponse>> messages(@PathVariable Long conversationId,
                                                @RequestParam(required = false) Long beforeSeq,
                                                @RequestParam(required = false) Integer size) {
        return R.ok(imService.listCurrentEmployeeMessages(conversationId, beforeSeq, size));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public R<ImMessageResponse> sendMessage(@PathVariable Long conversationId,
                                            @Valid @RequestBody SendImMessageRequest request) {
        return R.ok(imService.sendCurrentEmployeeMessage(conversationId, request));
    }

    @PostMapping("/conversations/{conversationId}/read-position")
    public R<ImConversationSummaryResponse> updateReadPosition(@PathVariable Long conversationId,
                                                               @Valid @RequestBody UpdateReadPositionRequest request) {
        return R.ok(imService.updateCurrentEmployeeReadPosition(conversationId, request));
    }
}
