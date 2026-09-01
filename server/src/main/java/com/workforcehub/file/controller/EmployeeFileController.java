package com.workforcehub.file.controller;

import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.response.R;
import com.workforcehub.file.dto.AttachmentRefResponse;
import com.workforcehub.file.dto.ConfirmUploadRequest;
import com.workforcehub.file.dto.CopyEmployeeFileRequest;
import com.workforcehub.file.dto.CreateEmployeeFileFolderRequest;
import com.workforcehub.file.dto.CreateDownloadTicketRequest;
import com.workforcehub.file.dto.CreateUploadTicketRequest;
import com.workforcehub.file.dto.DownloadFileResult;
import com.workforcehub.file.dto.DownloadTicketResponse;
import com.workforcehub.file.dto.EmployeeFileBrowserResponse;
import com.workforcehub.file.dto.EmployeeFileFolderResponse;
import com.workforcehub.file.dto.EmployeeFileResponse;
import com.workforcehub.file.dto.FilePurgeResponse;
import com.workforcehub.file.dto.FileRestoreResponse;
import com.workforcehub.file.dto.FileTrashRequest;
import com.workforcehub.file.dto.FileTrashResponse;
import com.workforcehub.file.dto.MoveEmployeeFileRequest;
import com.workforcehub.file.dto.QuotaResponse;
import com.workforcehub.file.dto.UploadTicketResponse;
import com.workforcehub.file.service.FileDownloadService;
import com.workforcehub.file.service.FileLifecycleService;
import com.workforcehub.file.service.FileQueryService;
import com.workforcehub.file.service.FileQuotaPolicyService;
import com.workforcehub.file.service.FileUploadConfirmService;
import com.workforcehub.file.service.FileUploadTicketService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 员工端文件中心接口。
 */
// 员工文件接口：上传凭证/确认、下载凭证、文件列表/回收站、文件夹、配额查询
@RestController
@RequestMapping("/api/employee/files")
public class EmployeeFileController {

    private final FileQueryService fileQueryService;
    private final FileUploadTicketService fileUploadTicketService;
    private final FileUploadConfirmService fileUploadConfirmService;
    private final FileDownloadService fileDownloadService;
    private final FileQuotaPolicyService fileQuotaPolicyService;
    private final FileLifecycleService fileLifecycleService;
    private final FileDownloadResponseFactory downloadResponseFactory;

    public EmployeeFileController(FileQueryService fileQueryService,
                                  FileUploadTicketService fileUploadTicketService,
                                  FileUploadConfirmService fileUploadConfirmService,
                                  FileDownloadService fileDownloadService,
                                  FileQuotaPolicyService fileQuotaPolicyService,
                                  FileLifecycleService fileLifecycleService,
                                  FileDownloadResponseFactory downloadResponseFactory) {
        this.fileQueryService = fileQueryService;
        this.fileUploadTicketService = fileUploadTicketService;
        this.fileUploadConfirmService = fileUploadConfirmService;
        this.fileDownloadService = fileDownloadService;
        this.fileQuotaPolicyService = fileQuotaPolicyService;
        this.fileLifecycleService = fileLifecycleService;
        this.downloadResponseFactory = downloadResponseFactory;
    }

    @Operation(summary = "分页查询我的文件", operationId = "pageEmployeeFiles")
    @GetMapping
    public R<PageResult<EmployeeFileResponse>> listFiles(@RequestParam(defaultValue = "1") long pageNo,
                                                         @RequestParam(defaultValue = "20") long pageSize) {
        return R.ok(fileQueryService.listEmployeeFiles(pageNo, pageSize, false));
    }

    @Operation(summary = "分页查询回收站文件", operationId = "pageEmployeeRecycleFiles")
    @GetMapping("/recycle-bin")
    public R<PageResult<EmployeeFileResponse>> listRecycleBin(@RequestParam(defaultValue = "1") long pageNo,
                                                              @RequestParam(defaultValue = "20") long pageSize) {
        return R.ok(fileQueryService.listEmployeeFiles(pageNo, pageSize, true));
    }

    @Operation(summary = "浏览文件目录", operationId = "browseEmployeeFiles")
    @GetMapping("/browser")
    public R<EmployeeFileBrowserResponse> browser(@RequestParam(required = false) Long folderId,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String fileType) {
        return R.ok(fileQueryService.browseEmployeeFiles(folderId, keyword, fileType));
    }

    @Operation(summary = "查询文件夹列表", operationId = "listEmployeeFolders")
    @GetMapping("/folders")
    public R<List<EmployeeFileFolderResponse>> listFolders() {
        return R.ok(fileQueryService.listEmployeeFolders());
    }

    @Operation(summary = "创建文件夹", operationId = "createEmployeeFolder")
    @PostMapping("/folders")
    public R<EmployeeFileFolderResponse> createFolder(@RequestBody CreateEmployeeFileFolderRequest request) {
        return R.ok(fileQueryService.createEmployeeFolder(request));
    }

    @Operation(summary = "创建上传凭证", operationId = "createEmployeeUploadTicket")
    @PostMapping("/upload-tickets")
    public R<UploadTicketResponse> createUploadTicket(@Valid @RequestBody CreateUploadTicketRequest request) {
        return R.ok(fileUploadTicketService.createEmployeeUploadTicket(request));
    }

    @Operation(summary = "确认文件上传", operationId = "confirmEmployeeUpload")
    @PostMapping("/confirm-upload")
    public R<AttachmentRefResponse> confirmUpload(@Valid @RequestBody ConfirmUploadRequest request) {
        return R.ok(fileUploadConfirmService.confirmEmployeeUpload(request));
    }

    @Operation(summary = "查询个人配额", operationId = "getEmployeeQuota")
    @GetMapping("/quota")
    public R<QuotaResponse> quota() {
        return R.ok(fileQuotaPolicyService.getEmployeeQuota());
    }

    @Operation(summary = "创建下载凭证", operationId = "createEmployeeDownloadTicket")
    @PostMapping("/{attachmentRefId}/download-tickets")
    public R<DownloadTicketResponse> createDownloadTicket(@PathVariable Long attachmentRefId,
                                                          @Valid @RequestBody CreateDownloadTicketRequest request) {
        return R.ok(fileDownloadService.createEmployeeDownloadTicket(attachmentRefId, request));
    }

    @Operation(summary = "移动文件", operationId = "moveEmployeeFile")
    @PostMapping("/{attachmentRefId}/move")
    public R<EmployeeFileResponse> move(@PathVariable Long attachmentRefId,
                                        @RequestBody MoveEmployeeFileRequest request) {
        return R.ok(fileQueryService.moveEmployeeFile(attachmentRefId, request));
    }

    @Operation(summary = "复制文件", operationId = "copyEmployeeFile")
    @PostMapping("/{attachmentRefId}/copy")
    public R<EmployeeFileResponse> copy(@PathVariable Long attachmentRefId,
                                        @RequestBody CopyEmployeeFileRequest request) {
        return R.ok(fileQueryService.copyEmployeeFile(attachmentRefId, request));
    }

    @Operation(summary = "下载文件", operationId = "downloadEmployeeFile")
    @GetMapping("/download/{ticketNo}")
    public ResponseEntity<Resource> download(@PathVariable String ticketNo) {
        DownloadFileResult result = fileDownloadService.downloadEmployeeFile(ticketNo);
        return downloadResponseFactory.attachment(result);
    }

    @Operation(summary = "文件移入回收站", operationId = "trashEmployeeFile")
    @PostMapping("/{attachmentRefId}/trash")
    public R<FileTrashResponse> trash(@PathVariable Long attachmentRefId,
                                      @Valid @RequestBody FileTrashRequest request) {
        return R.ok(fileLifecycleService.trashEmployeeFile(attachmentRefId, request));
    }

    @Operation(summary = "恢复回收站文件", operationId = "restoreEmployeeFile")
    @PostMapping("/{attachmentRefId}/restore")
    public R<FileRestoreResponse> restore(@PathVariable Long attachmentRefId,
                                          @Valid @RequestBody FileTrashRequest request) {
        return R.ok(fileLifecycleService.restoreEmployeeFile(attachmentRefId, request));
    }

    @Operation(summary = "彻底删除文件", operationId = "purgeEmployeeFile")
    @DeleteMapping("/{attachmentRefId}")
    public R<FilePurgeResponse> purge(@PathVariable Long attachmentRefId) {
        return R.ok(fileLifecycleService.purgeEmployeeFile(attachmentRefId));
    }
}
