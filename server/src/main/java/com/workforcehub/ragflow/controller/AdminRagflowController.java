package com.workforcehub.ragflow.controller;

import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.response.R;
import com.workforcehub.ragflow.dto.AddRagflowDocumentRequest;
import com.workforcehub.ragflow.dto.CreateRagflowSpaceRequest;
import com.workforcehub.ragflow.dto.RagflowAclResponse;
import com.workforcehub.ragflow.dto.RagflowDocumentResponse;
import com.workforcehub.ragflow.dto.RagflowGlobalSettingResponse;
import com.workforcehub.ragflow.dto.RagflowSearchTestRequest;
import com.workforcehub.ragflow.dto.RagflowSearchTestResponse;
import com.workforcehub.ragflow.dto.RagflowSpaceResponse;
import com.workforcehub.ragflow.dto.SaveRagflowAclRequest;
import com.workforcehub.ragflow.dto.UpdateRagflowGlobalSettingRequest;
import com.workforcehub.ragflow.dto.UpdateRagflowSpaceRequest;
import com.workforcehub.ragflow.service.RagflowAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ragflow")
public class AdminRagflowController {

    private final RagflowAdminService ragflowAdminService;

    public AdminRagflowController(RagflowAdminService ragflowAdminService) {
        this.ragflowAdminService = ragflowAdminService;
    }

    @GetMapping("/settings")
    public R<RagflowGlobalSettingResponse> getSettings() {
        return R.ok(ragflowAdminService.getGlobalSetting());
    }

    @PutMapping("/settings")
    public R<RagflowGlobalSettingResponse> updateSettings(@Valid @RequestBody UpdateRagflowGlobalSettingRequest request) {
        return R.ok(ragflowAdminService.updateGlobalSetting(request));
    }

    @GetMapping("/spaces")
    public R<PageResult<RagflowSpaceResponse>> listSpaces(@RequestParam(defaultValue = "1") long pageNo,
                                                          @RequestParam(defaultValue = "20") long pageSize,
                                                          @RequestParam(required = false) String keyword) {
        return R.ok(ragflowAdminService.pageSpaces(pageNo, pageSize, keyword));
    }

    @PostMapping("/spaces")
    public R<RagflowSpaceResponse> createSpace(@Valid @RequestBody CreateRagflowSpaceRequest request) {
        return R.ok(ragflowAdminService.createSpace(request));
    }

    @GetMapping("/spaces/{spaceId}")
    public R<RagflowSpaceResponse> getSpace(@PathVariable Long spaceId) {
        return R.ok(ragflowAdminService.getSpace(spaceId));
    }

    @PutMapping("/spaces/{spaceId}")
    public R<RagflowSpaceResponse> updateSpace(@PathVariable Long spaceId,
                                               @Valid @RequestBody UpdateRagflowSpaceRequest request) {
        return R.ok(ragflowAdminService.updateSpace(spaceId, request));
    }

    @GetMapping("/spaces/{spaceId}/acl")
    public R<List<RagflowAclResponse>> listAcl(@PathVariable Long spaceId) {
        return R.ok(ragflowAdminService.listAcl(spaceId));
    }

    @PutMapping("/spaces/{spaceId}/acl")
    public R<List<RagflowAclResponse>> saveAcl(@PathVariable Long spaceId,
                                               @Valid @RequestBody SaveRagflowAclRequest request) {
        return R.ok(ragflowAdminService.saveAcl(spaceId, request));
    }

    @GetMapping("/spaces/{spaceId}/documents")
    public R<List<RagflowDocumentResponse>> listDocuments(@PathVariable Long spaceId) {
        return R.ok(ragflowAdminService.listDocuments(spaceId));
    }

    @PostMapping("/spaces/{spaceId}/documents")
    public R<RagflowDocumentResponse> addDocument(@PathVariable Long spaceId,
                                                  @Valid @RequestBody AddRagflowDocumentRequest request) {
        return R.ok(ragflowAdminService.addDocument(spaceId, request));
    }

    @PostMapping("/spaces/{spaceId}/documents/{documentId}/reingest")
    public R<RagflowDocumentResponse> reingestDocument(@PathVariable Long spaceId,
                                                       @PathVariable Long documentId) {
        return R.ok(ragflowAdminService.reingestDocument(spaceId, documentId));
    }

    @DeleteMapping("/spaces/{spaceId}/documents/{documentId}")
    public R<Void> deleteDocument(@PathVariable Long spaceId,
                                  @PathVariable Long documentId) {
        ragflowAdminService.deleteDocument(spaceId, documentId);
        return R.ok();
    }

    @PostMapping("/spaces/{spaceId}/search-test")
    public R<RagflowSearchTestResponse> searchTest(@PathVariable Long spaceId,
                                                   @Valid @RequestBody RagflowSearchTestRequest request) {
        return R.ok(ragflowAdminService.searchTest(spaceId, request));
    }
}
