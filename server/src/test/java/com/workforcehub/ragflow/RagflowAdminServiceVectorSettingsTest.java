package com.workforcehub.ragflow;

import com.workforcehub.auth.security.WfhUserDetails;
import com.workforcehub.common.enums.UserType;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.file.repository.AttachmentRefRepository;
import com.workforcehub.file.repository.AttachmentRow;
import com.workforcehub.ragflow.dto.AddRagflowDocumentRequest;
import com.workforcehub.ragflow.dto.CreateRagflowSpaceRequest;
import com.workforcehub.ragflow.dto.RagflowDocumentResponse;
import com.workforcehub.ragflow.dto.UpdateRagflowGlobalSettingRequest;
import com.workforcehub.ragflow.dto.UpdateRagflowSpaceRequest;
import com.workforcehub.ragflow.repository.RagflowDocumentRow;
import com.workforcehub.ragflow.repository.RagflowGlobalSettingRow;
import com.workforcehub.ragflow.repository.RagflowRepository;
import com.workforcehub.ragflow.repository.RagflowSpaceRow;
import com.workforcehub.ragflow.repository.RagflowVectorRepository;
import com.workforcehub.ragflow.service.RagflowAdminService;
import com.workforcehub.ragflow.service.RagflowConfigResolver;
import com.workforcehub.ragflow.service.RagflowEmbeddingClient;
import com.workforcehub.ragflow.service.RagflowIngestService;
import com.workforcehub.ragflow.service.RagflowKnowledgeSearchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RagflowAdminServiceVectorSettingsTest {

    private final RagflowRepository ragflowRepository = mock(RagflowRepository.class);
    private final RagflowEmbeddingClient embeddingClient = mock(RagflowEmbeddingClient.class);
    private final AttachmentRefRepository attachmentRefRepository = mock(AttachmentRefRepository.class);
    private final RagflowIngestService ingestService = mock(RagflowIngestService.class);
    private final List<Runnable> ingestTasks = new ArrayList<>();
    private final Executor kbIngestExecutor = ingestTasks::add;
    private final RagflowAdminService service = new RagflowAdminService(
        ragflowRepository,
        attachmentRefRepository,
        ingestService,
        mock(RagflowKnowledgeSearchService.class),
        mock(RagflowConfigResolver.class),
        embeddingClient,
        mock(RagflowVectorRepository.class),
        kbIngestExecutor
    );

    @Test
    void shouldDetectAndStoreEmbeddingDimensionWhenCreatingSpace() {
        authenticate();
        when(ragflowRepository.existsSpaceCode("POLICY")).thenReturn(false);
        when(ragflowRepository.getGlobalSetting()).thenReturn(globalSetting());
        when(embeddingClient.detectDimension(2001L)).thenReturn(1024);
        when(ragflowRepository.getSpaceOrThrow(anyLong())).thenReturn(spaceWithCreatedVectorSettings());
        CreateRagflowSpaceRequest request = new CreateRagflowSpaceRequest();
        request.setSpaceName("制度知识库");
        request.setSpaceCode("policy");
        request.setVisibility("PRIVATE");

        service.createSpace(request);

        verify(ragflowRepository).insertSpace(
            anyLong(),
            eq("制度知识库"),
            eq("POLICY"),
            isNull(),
            eq("PRIVATE"),
            eq("ACTIVE"),
            isNull(),
            isNull(),
            eq(2001L),
            eq(1024),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(800),
            eq(100),
            eq(5),
            isNull(),
            eq(8001L)
        );
    }

    @Test
    void shouldRejectWrongEmbeddingDimensionWhenCreatingSpace() {
        authenticate();
        when(ragflowRepository.existsSpaceCode("POLICY")).thenReturn(false);
        when(ragflowRepository.getGlobalSetting()).thenReturn(globalSetting());
        when(embeddingClient.detectDimension(2001L)).thenReturn(1024);
        CreateRagflowSpaceRequest request = new CreateRagflowSpaceRequest();
        request.setSpaceName("制度知识库");
        request.setSpaceCode("policy");
        request.setEmbeddingDimension(4096);

        assertThrows(BizException.class, () -> service.createSpace(request));

        verify(ragflowRepository).existsSpaceCode("POLICY");
        verify(ragflowRepository).getGlobalSetting();
        verify(embeddingClient).detectDimension(2001L);
        verifyNoMoreInteractions(ragflowRepository);
    }

    @Test
    void shouldDetectAndStoreEmbeddingDimensionWhenUpdatingGlobalSetting() {
        authenticate();
        when(ragflowRepository.getGlobalSetting()).thenReturn(globalSetting(), globalSettingWithDimension(1024));
        when(embeddingClient.detectDimension(2001L)).thenReturn(1024);
        UpdateRagflowGlobalSettingRequest request = new UpdateRagflowGlobalSettingRequest();
        request.setEmbeddingModelId(2001L);
        request.setChunkSize(900);
        request.setChunkOverlap(120);
        request.setRetrievalTopK(8);

        service.updateGlobalSetting(request);

        verify(ragflowRepository).updateGlobalSetting(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(2001L),
            eq(1024),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(900),
            eq(120),
            eq(8),
            isNull(),
            eq(8001L)
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRejectEmbeddingDimensionChangeEvenWhenSpaceOnlyHasFailedDocument() {
        authenticate();
        when(ragflowRepository.getSpaceOrThrow(9003L)).thenReturn(spaceWithFailedDocument());
        UpdateRagflowSpaceRequest request = new UpdateRagflowSpaceRequest();
        request.setSpaceName("制度知识库");
        request.setVisibility("PUBLIC");
        request.setStatus("ACTIVE");
        request.setEmbeddingModelId(2002L);
        request.setEmbeddingDimension(1024);
        request.setRerankEnabled(false);
        request.setChunkSize(800);
        request.setChunkOverlap(100);
        request.setRetrievalTopK(5);

        assertThrows(BizException.class, () -> service.updateSpace(9003L, request));

        verify(ragflowRepository).getSpaceOrThrow(9003L);
        verifyNoMoreInteractions(ragflowRepository);
    }

    @Test
    void shouldRejectEmbeddingModelChangeWhenSpaceHasChunks() {
        authenticate();
        when(ragflowRepository.getSpaceOrThrow(9001L)).thenReturn(spaceWithContent());
        UpdateRagflowSpaceRequest request = new UpdateRagflowSpaceRequest();
        request.setSpaceName("制度知识库");
        request.setVisibility("PUBLIC");
        request.setStatus("ACTIVE");
        request.setEmbeddingModelId(2002L);
        request.setEmbeddingDimension(1024);
        request.setChunkSize(800);
        request.setChunkOverlap(100);
        request.setRetrievalTopK(5);

        assertThrows(BizException.class, () -> service.updateSpace(9001L, request));

        verify(ragflowRepository).getSpaceOrThrow(9001L);
        verifyNoMoreInteractions(ragflowRepository);
    }

    @Test
    void shouldQueueDocumentIngestAfterDocumentRegistered() {
        authenticate();
        AddRagflowDocumentRequest request = new AddRagflowDocumentRequest();
        request.setAttachmentRefId(3001L);
        Long[] insertedDocumentId = new Long[1];
        when(ragflowRepository.getSpaceOrThrow(9001L)).thenReturn(spaceWithCreatedVectorSettings());
        when(attachmentRefRepository.getAttachmentOrThrow(3001L)).thenReturn(attachment());
        when(ragflowRepository.existsDocumentByAttachment(9001L, 3001L)).thenReturn(false);
        doAnswer(invocation -> {
            insertedDocumentId[0] = invocation.getArgument(0);
            return null;
        }).when(ragflowRepository).insertDocument(anyLong(), anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any(), anyLong());
        when(ragflowRepository.listDocuments(9001L)).thenAnswer(invocation -> List.of(pendingDocument(insertedDocumentId[0])));

        RagflowDocumentResponse response = service.addDocument(9001L, request);

        assertEquals("PENDING", response.getIngestStatus());
        assertEquals("PENDING", response.getVectorStatus());
        assertEquals(1, ingestTasks.size());
        verifyNoInteractions(ingestService);

        ingestTasks.get(0).run();

        verify(ingestService).ingest(
            eq(9001L),
            anyLong(),
            anyLong(),
            eq(4001L),
            eq("制度.docx"),
            eq("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            eq("WORD")
        );
        verify(ragflowRepository).insertDocument(
            anyLong(),
            eq(9001L),
            eq(3001L),
            eq(4001L),
            eq("制度.docx"),
            eq("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            eq(1234L),
            eq("hash"),
            eq("WORD"),
            eq(8001L)
        );
        verify(ragflowRepository).insertIngestJob(anyLong(), anyLong(), eq(9001L), eq(4001L), eq(8001L));
        verify(ragflowRepository).bindLatestIngestJob(anyLong(), anyLong(), eq(8001L));
        verify(ragflowRepository).refreshSpaceCounts(9001L);
    }

    private void authenticate() {
        WfhUserDetails user = mock(WfhUserDetails.class);
        when(user.getUserId()).thenReturn(8001L);
        when(user.getUserType()).thenReturn(UserType.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private RagflowSpaceRow spaceWithContent() {
        LocalDateTime now = LocalDateTime.now();
        return new RagflowSpaceRow(
            9001L,
            "制度知识库",
            "POLICY",
            null,
            "PUBLIC",
            1,
            12,
            null,
            null,
            2001L,
            1024,
            false,
            null,
            null,
            null,
            800,
            100,
            5,
            null,
            "ACTIVE",
            now,
            now
        );
    }

    private RagflowGlobalSettingRow globalSetting() {
        return globalSettingWithDimension(null);
    }

    private RagflowGlobalSettingRow globalSettingWithDimension(Integer embeddingDimension) {
        LocalDateTime now = LocalDateTime.now();
        return new RagflowGlobalSettingRow(
            1L,
            null,
            false,
            null,
            null,
            null,
            false,
            null,
            2001L,
            embeddingDimension,
            false,
            null,
            false,
            null,
            null,
            800,
            100,
            5,
            null,
            now,
            now
        );
    }

    private RagflowSpaceRow spaceWithCreatedVectorSettings() {
        LocalDateTime now = LocalDateTime.now();
        return new RagflowSpaceRow(
            9002L,
            "制度知识库",
            "POLICY",
            null,
            "PRIVATE",
            0,
            0,
            null,
            null,
            2001L,
            1024,
            null,
            null,
            null,
            null,
            800,
            100,
            5,
            null,
            "ACTIVE",
            now,
            now
        );
    }

    private RagflowSpaceRow spaceWithFailedDocument() {
        LocalDateTime now = LocalDateTime.now();
        return new RagflowSpaceRow(
            9003L,
            "制度知识库",
            "POLICY_FAILED",
            null,
            "PUBLIC",
            1,
            0,
            null,
            null,
            2001L,
            4096,
            false,
            null,
            null,
            null,
            800,
            100,
            5,
            null,
            "ACTIVE",
            now,
            now
        );
    }

    private AttachmentRow attachment() {
        LocalDateTime now = LocalDateTime.now();
        return new AttachmentRow(
            3001L,
            4001L,
            "RAGFLOW_DOCUMENT",
            9001L,
            "制度.docx",
            "ADMIN",
            8001L,
            null,
            null,
            now,
            0,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            1234L,
            "hash",
            "CLEAN"
        );
    }

    private RagflowDocumentRow pendingDocument(Long documentId) {
        LocalDateTime now = LocalDateTime.now();
        return new RagflowDocumentRow(
            documentId,
            9001L,
            3001L,
            4001L,
            "制度.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            1234L,
            "hash",
            "WORD",
            "PENDING",
            6001L,
            "PENDING",
            "PENDING",
            0,
            null,
            now,
            now
        );
    }

}
