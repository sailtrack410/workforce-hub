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

    @GetMapping("/conversations")
    public R<List<ImConversationSummaryResponse>> conversations(@RequestParam(required = false) String beforeLastMessageAt,
                                                                @RequestParam(required = false) Long beforeConversationId,
                                                                @RequestParam(required = false) Integer size) {
        return R.ok(imService.listCurrentEmployeeConversations(beforeLastMessageAt, beforeConversationId, size));
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
        return R.ok(imGroupService.createCurrentEmployeeGroupConversation(request));
    }

    @PostMapping("/agent-conversations")
    public R<ImConversationSummaryResponse> startAgentConversation(
        @Valid @RequestBody StartAgentConversationRequest request
    ) {
        return R.ok(imService.startCurrentEmployeeAgentConversation(request));
    }

    @GetMapping("/conversations/{conversationId}")
    public R<ImConversationDetailResponse> conversationDetail(@PathVariable Long conversationId) {
        return R.ok(imService.getCurrentEmployeeConversationDetail(conversationId));
    }

    @GetMapping("/group-conversations/{conversationId}/members")
    public R<List<ImConversationMemberResponse>> groupMembers(@PathVariable Long conversationId) {
        return R.ok(imGroupService.listCurrentEmployeeGroupMembers(conversationId));
    }

    @GetMapping("/group-conversations/{conversationId}/profile")
    public R<ImGroupProfileResponse> groupProfile(@PathVariable Long conversationId) {
        return R.ok(imGroupService.getCurrentEmployeeGroupProfile(conversationId));
    }

    @GetMapping("/group-conversations/{conversationId}/files")
    public R<List<ImGroupFileResponse>> groupFiles(@PathVariable Long conversationId) {
        return R.ok(imGroupService.listCurrentEmployeeGroupFiles(conversationId));
    }

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

    @PostMapping("/group-conversations/{conversationId}/files/{attachmentRefId}/trash")
    public R<FileTrashResponse> trashGroupFile(
        @PathVariable Long conversationId,
        @PathVariable Long attachmentRefId,
        @Valid @RequestBody FileTrashRequest request
    ) {
        return R.ok(imGroupService.trashCurrentEmployeeGroupFile(conversationId, attachmentRefId, request));
    }

    @PostMapping("/group-conversations/{conversationId}/profile")
    public R<ImGroupProfileResponse> updateGroupProfile(
        @PathVariable Long conversationId,
        @Valid @RequestBody UpdateGroupConversationRequest request
    ) {
        return R.ok(imGroupService.updateCurrentEmployeeGroupProfile(conversationId, request));
    }

    @PostMapping("/group-conversations/{conversationId}/members")
    public R<List<ImConversationMemberResponse>> addGroupMembers(
        @PathVariable Long conversationId,
        @Valid @RequestBody GroupMemberUserIdsRequest request
    ) {
        return R.ok(imGroupService.addCurrentEmployeeGroupMembers(conversationId, request));
    }

    @PostMapping("/group-conversations/{conversationId}/members/remove")
    public R<List<ImConversationMemberResponse>> removeGroupMembers(
        @PathVariable Long conversationId,
        @Valid @RequestBody GroupMemberUserIdsRequest request
    ) {
        return R.ok(imGroupService.removeCurrentEmployeeGroupMembers(conversationId, request));
    }

    @PostMapping("/group-conversations/{conversationId}/leave")
    public R<List<ImConversationMemberResponse>> leaveGroup(@PathVariable Long conversationId) {
        return R.ok(imGroupService.leaveCurrentEmployeeGroupConversation(conversationId));
    }

    @PostMapping("/group-conversations/{conversationId}/dismiss")
    public R<Void> dismissGroup(@PathVariable Long conversationId) {
        imGroupService.dismissCurrentEmployeeGroupConversation(conversationId);
        return R.ok();
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

    @PostMapping("/conversations/{conversationId}/messages/{messageId}/edit")
    public R<ImMessageResponse> editMessage(@PathVariable Long conversationId,
                                            @PathVariable Long messageId,
                                            @Valid @RequestBody EditImMessageRequest request) {
        return R.ok(imService.editCurrentEmployeeMessage(conversationId, messageId, request));
    }

    @PostMapping("/conversations/{conversationId}/messages/{messageId}/recall")
    public R<ImMessageResponse> recallMessage(@PathVariable Long conversationId,
                                              @PathVariable Long messageId) {
        return R.ok(imService.recallCurrentEmployeeMessage(conversationId, messageId));
    }

    @PostMapping("/conversations/{conversationId}/messages/{messageId}/translate")
    public R<TranslateImMessageResponse> translateMessage(@PathVariable Long conversationId,
                                                          @PathVariable Long messageId) {
        return R.ok(imService.translateCurrentEmployeeMessage(conversationId, messageId));
    }

    @PostMapping("/conversations/{conversationId}/messages/{messageId}/forward")
    public R<ImMessageResponse> forwardMessage(@PathVariable Long conversationId,
                                               @PathVariable Long messageId,
                                               @Valid @RequestBody ForwardImMessageRequest request) {
        return R.ok(imService.forwardCurrentEmployeeMessage(conversationId, messageId, request));
    }

    @PostMapping("/conversations/{conversationId}/messages/{messageId}/favorite")
    public R<ImMessageActionResponse> favoriteMessage(@PathVariable Long conversationId,
                                                      @PathVariable Long messageId) {
        return R.ok(imService.favoriteCurrentEmployeeMessage(conversationId, messageId));
    }

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

    @PostMapping("/conversations/{conversationId}/messages/{messageId}/delete")
    public R<ImMessageActionResponse> deleteMessage(@PathVariable Long conversationId,
                                                    @PathVariable Long messageId) {
        return R.ok(imService.deleteCurrentEmployeeMessage(conversationId, messageId));
    }

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

    @GetMapping("/attachments/download/{ticketNo}")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable String ticketNo) {
        DownloadFileResult result = imService.downloadCurrentEmployeeAttachment(ticketNo);
        return downloadResponseFactory.attachment(result);
    }

    @PostMapping("/conversations/{conversationId}/read-position")
    public R<ImConversationSummaryResponse> updateReadPosition(@PathVariable Long conversationId,
                                                               @Valid @RequestBody UpdateReadPositionRequest request) {
        return R.ok(imService.updateCurrentEmployeeReadPosition(conversationId, request));
    }

    @PostMapping("/conversations/{conversationId}/preference")
    public R<ImConversationSummaryResponse> updatePreference(
        @PathVariable Long conversationId,
        @Valid @RequestBody UpdateConversationPreferenceRequest request
    ) {
        return R.ok(imService.updateCurrentEmployeeConversationPreference(conversationId, request));
    }
}
