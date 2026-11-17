package com.workforcehub.ragflow;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.file.dto.AttachmentRefResponse;
import com.workforcehub.file.dto.ConfirmUploadRequest;
import com.workforcehub.file.dto.CreateUploadTicketRequest;
import com.workforcehub.file.dto.UploadTicketResponse;
import com.workforcehub.file.service.FileUploadConfirmService;
import com.workforcehub.file.service.FileUploadTicketService;
import com.workforcehub.ragflow.controller.AdminRagflowController;
import com.workforcehub.ragflow.service.RagflowAdminService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminRagflowControllerTest {

    private final RagflowAdminService ragflowAdminService = mock(RagflowAdminService.class);
    private final FileUploadTicketService fileUploadTicketService = mock(FileUploadTicketService.class);
    private final FileUploadConfirmService fileUploadConfirmService = mock(FileUploadConfirmService.class);
    private final AdminRagflowController controller = new AdminRagflowController(
        ragflowAdminService,
        fileUploadTicketService,
        fileUploadConfirmService
    );

    @Test
    void shouldCreateDocumentUploadTicketThroughRagflowScope() {
        CreateUploadTicketRequest request = new CreateUploadTicketRequest();
        request.setOwnerType("RAGFLOW_DOCUMENT");
        UploadTicketResponse response = new UploadTicketResponse();
        when(fileUploadTicketService.createAdminUploadTicket(request)).thenReturn(response);

        assertSame(response, controller.createDocumentUploadTicket(request).getData());
        verify(fileUploadTicketService).createAdminUploadTicket(request);
    }

    @Test
    void shouldRejectNonRagflowDocumentUploadTicket() {
        CreateUploadTicketRequest request = new CreateUploadTicketRequest();
        request.setOwnerType("ADMIN_FILE");

        assertThrows(BizException.class, () -> controller.createDocumentUploadTicket(request));
    }

    @Test
    void shouldConfirmDocumentUploadThroughRagflowScope() {
        ConfirmUploadRequest request = new ConfirmUploadRequest();
        request.setOwnerType("RAGFLOW_DOCUMENT");
        AttachmentRefResponse response = new AttachmentRefResponse();
        when(fileUploadConfirmService.confirmAdminUpload(request)).thenReturn(response);

        assertSame(response, controller.confirmDocumentUpload(request).getData());
        verify(fileUploadConfirmService).confirmAdminUpload(request);
    }

    @Test
    void shouldRejectNonRagflowDocumentConfirmUpload() {
        ConfirmUploadRequest request = new ConfirmUploadRequest();
        request.setOwnerType("ADMIN_FILE");

        assertThrows(BizException.class, () -> controller.confirmDocumentUpload(request));
    }

    @Test
    void shouldDeleteSpaceThroughRagflowScope() {
        controller.deleteSpace(9001L);

        verify(ragflowAdminService).deleteSpace(9001L);
    }
}
