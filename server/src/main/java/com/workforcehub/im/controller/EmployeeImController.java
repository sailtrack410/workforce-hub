package com.workforcehub.im.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.file.controller.FileDownloadResponseFactory;
import com.workforcehub.file.dto.CreateDownloadTicketRequest;
import com.workforcehub.file.dto.DownloadFileResult;
import com.workforcehub.file.dto.DownloadTicketResponse;
import com.workforcehub.file.dto.FileTrashRequest;
import com.workforcehub.file.dto.FileTrashResponse;
import com.workforcehub.im.dto.CreateGroupConversationRequest;
import com.workforcehub.im.dto.EditImMessageRequest;
import com.workforcehub.im.dto.ForwardImMessageRequest;
import com.workforcehub.im.dto.GroupMemberUserIdsRequest;
import com.workforcehub.im.dto.ImConversationDetailResponse;
import com.workforcehub.im.dto.ImConversationMemberResponse;
import com.workforcehub.im.dto.ImConversationSummaryResponse;
import com.workforcehub.im.dto.ImGroupFileResponse;
import com.workforcehub.im.dto.ImGroupProfileResponse;
import com.workforcehub.im.dto.ImMessageActionResponse;
import com.workforcehub.im.dto.ImMessageResponse;
import com.workforcehub.im.dto.RemindImMessageRequest;
import com.workforcehub.im.dto.SendImMessageRequest;
import com.workforcehub.im.dto.StartAgentConversationRequest;
import com.workforcehub.im.dto.StartPrivateConversationRequest;
import com.workforcehub.im.dto.TranslateImMessageResponse;
import com.workforcehub.im.dto.UpdateConversationPreferenceRequest;
import com.workforcehub.im.dto.UpdateGroupConversationRequest;
import com.workforcehub.im.dto.UpdateReadPositionRequest;
import com.workforcehub.im.service.ImGroupService;
import com.workforcehub.im.service.ImService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
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
// IM REST 接口：会话 CRUD、消息收发/撤回/编辑、群成员管理、群文件、已读位置
@RestController
@RequestMapping("/api/employee/im")
public class EmployeeImController {

    private final ImService imService;
    private final ImGroupService imGroupService;
    private final FileDownloadResponseFactory downloadResponseFactory;

    public EmployeeImController(ImService imService,
                                ImGroupService imGroupService,
                                FileDownloadResponseFactory downloadResponseFactory) {
        this.imService = imService;
        this.imGroupService = imGroupService;
        this.downloadResponseFactory = downloadResponseFactory;
    }

    @Operation(summary = "查询会话列表", operationId = "listImConversations")
    @GetMapping("/conversations")
    public R<List<ImConversationSummaryResponse>> conversations(@RequestParam(required = false) String beforeLastMessageAt,
                                                                @RequestParam(required = false) Long beforeConversationId,
                                                                @RequestParam(required = false) Integer size) {
        return R.ok(imService.listCurrentEmployeeConversations(beforeLastMessageAt, beforeConversationId, size));
    }

    @Operation(summary = "发起私聊会话", operationId = "startPrivateConversation")
    @PostMapping("/private-conversations")
    public R<ImConversationSummaryResponse> startPrivateConversation(
        @Valid @RequestBody StartPrivateConversationRequest request
    ) {
        return R.ok(imService.startCurrentEmployeePrivateConversation(request));
    }

    @Operation(summary = "创建群聊会话", operationId = "createGroupConversation")
    @PostMapping("/group-conversations")
    public R<ImConversationSummaryResponse> createGroupConversation(
        @Valid @RequestBody CreateGroupConversationRequest request
    ) {
        return R.ok(imGroupService.createCurrentEmployeeGroupConversation(request));
    }

    @Operation(summary = "发起 Agent 会话", operationId = "startAgentConversation")
    @PostMapping("/agent-conversations")
    public R<ImConversationSummaryResponse> startAgentConversation(
        @Valid @RequestBody StartAgentConversationRequest request
    ) {
        return R.ok(imService.startCurrentEmployeeAgentConversation(request));
    }

    @Operation(summary = "查询会话详情", operationId = "getImConversationDetail")
    @GetMapping("/conversations/{conversationId}")
    public R<ImConversationDetailResponse> conversationDetail(@PathVariable Long conversationId) {
        return R.ok(imService.getCurrentEmployeeConversationDetail(conversationId));
    }

    @Operation(summary = "查询群成员列表", operationId = "listImGroupMembers")
    @GetMapping("/group-conversations/{conversationId}/members")
    public R<List<ImConversationMemberResponse>> groupMembers(@PathVariable Long conversationId) {
        return R.ok(imGroupService.listCurrentEmployeeGroupMembers(conversationId));
    }

    @Operation(summary = "查询群资料", operationId = "getImGroupProfile")
    @GetMapping("/group-conversations/{conversationId}/profile")
    public R<ImGroupProfileResponse> groupProfile(@PathVariable Long conversationId) {
        return R.ok(imGroupService.getCurrentEmployeeGroupProfile(conversationId));
    }

    @Operation(summary = "查询群文件列表", operationId = "listImGroupFiles")
    @GetMapping("/group-conversations/{conversationId}/files")
    public R<List<ImGroupFileResponse>> groupFiles(@PathVariable Long conversationId) {
        return R.ok(imGroupService.listCurrentEmployeeGroupFiles(conversationId));
    }

    @Operation(summary = "创建群文件下载凭证", operationId = "createGroupFileDownloadTicket")
    @PostMapping("/group-conversations/{conversationId}/files/{attachmentRefId}/download-tickets")
    public R<DownloadTicketResponse> createGroupFileDownloadTicket(
        @PathVariable Long conversationId,
        @PathVariable Long attachmentRefId,
        @Valid @RequestBody CreateDownloadTicketRequest request
    ) {
        return R.ok(imGroupService.createCurrentEmployeeGroupFileDownloadTicket(
            conversationId,
            attachmentRefId,
            request
        ));
    }

    @Operation(summary = "群文件移入回收站", operationId = "trashGroupFile")
    @PostMapping("/group-conversations/{conversationId}/files/{attachmentRefId}/trash")
    public R<FileTrashResponse> trashGroupFile(
        @PathVariable Long conversationId,
        @PathVariable Long attachmentRefId,
        @Valid @RequestBody FileTrashRequest request
    ) {
        return R.ok(imGroupService.trashCurrentEmployeeGroupFile(conversationId, attachmentRefId, request));
    }

    @Operation(summary = "更新群资料", operationId = "updateGroupProfile")
    @PostMapping("/group-conversations/{conversationId}/profile")
    public R<ImGroupProfileResponse> updateGroupProfile(
        @PathVariable Long conversationId,
        @Valid @RequestBody UpdateGroupConversationRequest request
    ) {
        return R.ok(imGroupService.updateCurrentEmployeeGroupProfile(conversationId, request));
    }

    @Operation(summary = "添加群成员", operationId = "addGroupMembers")
    @PostMapping("/group-conversations/{conversationId}/members")
    public R<List<ImConversationMemberResponse>> addGroupMembers(
        @PathVariable Long conversationId,
        @Valid @RequestBody GroupMemberUserIdsRequest request
    ) {
        return R.ok(imGroupService.addCurrentEmployeeGroupMembers(conversationId, request));
    }

    @Operation(summary = "移除群成员", operationId = "removeGroupMembers")
    @PostMapping("/group-conversations/{conversationId}/members/remove")
    public R<List<ImConversationMemberResponse>> removeGroupMembers(
        @PathVariable Long conversationId,
        @Valid @RequestBody GroupMemberUserIdsRequest request
    ) {
        return R.ok(imGroupService.removeCurrentEmployeeGroupMembers(conversationId, request));
    }

    @Operation(summary = "退出群聊", operationId = "leaveGroup")
    @PostMapping("/group-conversations/{conversationId}/leave")
    public R<List<ImConversationMemberResponse>> leaveGroup(@PathVariable Long conversationId) {
        return R.ok(imGroupService.leaveCurrentEmployeeGroupConversation(conversationId));
    }

    @Operation(summary = "解散群聊", operationId = "dismissGroup")
    @PostMapping("/group-conversations/{conversationId}/dismiss")
    public R<Void> dismissGroup(@PathVariable Long conversationId) {
        imGroupService.dismissCurrentEmployeeGroupConversation(conversationId);
        return R.ok();
    }

    @Operation(summary = "查询消息列表", operationId = "listImMessages")
    @GetMapping("/conversations/{conversationId}/messages")
    public R<List<ImMessageResponse>> messages(@PathVariable Long conversationId,
                                                @RequestParam(required = false) Long beforeSeq,
                                                @RequestParam(required = false) Integer size) {
        return R.ok(imService.listCurrentEmployeeMessages(conversationId, beforeSeq, size));
    }

    @Operation(summary = "发送消息", operationId = "sendImMessage")
    @PostMapping("/conversations/{conversationId}/messages")
    public R<ImMessageResponse> sendMessage(@PathVariable Long conversationId,
                                            @Valid @RequestBody SendImMessageRequest request) {
        return R.ok(imService.sendCurrentEmployeeMessage(conversationId, request));
    }

    @Operation(summary = "编辑消息", operationId = "editImMessage")
    @PostMapping("/conversations/{conversationId}/messages/{messageId}/edit")
    public R<ImMessageResponse> editMessage(@PathVariable Long conversationId,
                                            @PathVariable Long messageId,
                                            @Valid @RequestBody EditImMessageRequest request) {
        return R.ok(imService.editCurrentEmployeeMessage(conversationId, messageId, request));
    }

    @Operation(summary = "撤回消息", operationId = "recallImMessage")
    @PostMapping("/conversations/{conversationId}/messages/{messageId}/recall")
    public R<ImMessageResponse> recallMessage(@PathVariable Long conversationId,
                                              @PathVariable Long messageId) {
        return R.ok(imService.recallCurrentEmployeeMessage(conversationId, messageId));
    }

    @Operation(summary = "翻译消息", operationId = "translateImMessage")
    @PostMapping("/conversations/{conversationId}/messages/{messageId}/translate")
    public R<TranslateImMessageResponse> translateMessage(@PathVariable Long conversationId,
                                                          @PathVariable Long messageId) {
        return R.ok(imService.translateCurrentEmployeeMessage(conversationId, messageId));
    }

    @Operation(summary = "转发消息", operationId = "forwardImMessage")
    @PostMapping("/conversations/{conversationId}/messages/{messageId}/forward")
    public R<ImMessageResponse> forwardMessage(@PathVariable Long conversationId,
                                               @PathVariable Long messageId,
                                               @Valid @RequestBody ForwardImMessageRequest request) {
        return R.ok(imService.forwardCurrentEmployeeMessage(conversationId, messageId, request));
    }

    @Operation(summary = "收藏消息", operationId = "favoriteImMessage")
    @PostMapping("/conversations/{conversationId}/messages/{messageId}/favorite")
    public R<ImMessageActionResponse> favoriteMessage(@PathVariable Long conversationId,
                                                      @PathVariable Long messageId) {
        return R.ok(imService.favoriteCurrentEmployeeMessage(conversationId, messageId));
    }

    @Operation(summary = "设置消息提醒", operationId = "remindImMessage")
    @PostMapping("/conversations/{conversationId}/messages/{messageId}/reminder")
    public R<ImMessageActionResponse> remindMessage(@PathVariable Long conversationId,
                                                    @PathVariable Long messageId,
                                                    @RequestBody(required = false) RemindImMessageRequest request) {
        return R.ok(imService.remindCurrentEmployeeMessage(
            conversationId,
            messageId,
            request == null ? new RemindImMessageRequest() : request
        ));
    }

    @Operation(summary = "删除消息", operationId = "deleteImMessage")
    @PostMapping("/conversations/{conversationId}/messages/{messageId}/delete")
    public R<ImMessageActionResponse> deleteMessage(@PathVariable Long conversationId,
                                                    @PathVariable Long messageId) {
        return R.ok(imService.deleteCurrentEmployeeMessage(conversationId, messageId));
    }

    @Operation(summary = "创建消息附件下载凭证", operationId = "createMessageAttachmentDownloadTicket")
    @PostMapping("/conversations/{conversationId}/messages/{messageId}/attachments/{attachmentRefId}/download-tickets")
    public R<DownloadTicketResponse> createMessageAttachmentDownloadTicket(
        @PathVariable Long conversationId,
        @PathVariable Long messageId,
        @PathVariable Long attachmentRefId,
        @Valid @RequestBody CreateDownloadTicketRequest request
    ) {
        return R.ok(imService.createCurrentEmployeeMessageAttachmentDownloadTicket(
            conversationId,
            messageId,
            attachmentRefId,
            request
        ));
    }

    @Operation(summary = "下载附件", operationId = "downloadImAttachment")
    @GetMapping("/attachments/download/{ticketNo}")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable String ticketNo) {
        DownloadFileResult result = imService.downloadCurrentEmployeeAttachment(ticketNo);
        return downloadResponseFactory.attachment(result);
    }

    @Operation(summary = "更新已读位置", operationId = "updateImReadPosition")
    @PostMapping("/conversations/{conversationId}/read-position")
    public R<ImConversationSummaryResponse> updateReadPosition(@PathVariable Long conversationId,
                                                               @Valid @RequestBody UpdateReadPositionRequest request) {
        return R.ok(imService.updateCurrentEmployeeReadPosition(conversationId, request));
    }

    @Operation(summary = "更新会话设置", operationId = "updateImConversationPreference")
    @PostMapping("/conversations/{conversationId}/preference")
    public R<ImConversationSummaryResponse> updatePreference(
        @PathVariable Long conversationId,
        @Valid @RequestBody UpdateConversationPreferenceRequest request
    ) {
        return R.ok(imService.updateCurrentEmployeeConversationPreference(conversationId, request));
    }
}
