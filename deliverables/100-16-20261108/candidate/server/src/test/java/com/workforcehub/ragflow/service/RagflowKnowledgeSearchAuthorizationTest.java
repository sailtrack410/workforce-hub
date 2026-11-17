package com.workforcehub.ragflow.service;

import com.workforcehub.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RagflowKnowledgeSearchAuthorizationTest {
    private KnowledgeAccessTestFixture f;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        f = new KnowledgeAccessTestFixture();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allLegacySearchEntrypointsRequireAnAuthenticatedEmployee() {
        assertThrows(BizException.class, () -> f.search.searchRows(2L, "制度", 5));
        assertThrows(BizException.class, () -> f.search.searchChunks(2L, "制度", 5));
        assertThrows(BizException.class, () -> f.search.searchChunks(List.of(2L), "制度", 5));
        f.admin(List.of("SUPER_ADMIN"), List.of());
        assertThrows(BizException.class, () -> f.search.searchRows(2L, "制度", 5));
        assertThrows(BizException.class, () -> f.search.searchChunks(2L, "制度", 5));
        assertThrows(BizException.class, () -> f.search.searchChunks(List.of(2L), "制度", 5));
        verifyNoInteractions(f.resolver, f.embedding, f.vectors);
    }

    @Test
    void employeeDenialPrecedesConfigurationEmbeddingAndVectorAccess() {
        f.employee(7);
        BizException denied = assertThrows(BizException.class, () -> f.search.searchRows(1L, "制度", 5));
        assertEquals(403, denied.getHttpStatus().value());
        assertThrows(BizException.class, () -> f.search.searchChunks(1L, "制度", 5));
        assertThrows(BizException.class, () -> f.search.searchRowsForEmployee(null, 2L, "制度", 5));
        verifyNoInteractions(f.resolver, f.embedding, f.vectors);
    }

    @Test
    void authorizedEmployeeGetsActualSearchRowsAndDocumentTitles() {
        f.grant("USER", 7, "read");
        f.employee(7);
        var rows = f.search.searchRows(1L, "制度", 5);
        assertEquals(1, rows.size());
        assertEquals("授权知识正文", rows.get(0).contentText());
        assertEquals("制度正文", rows.get(0).documentTitle());
        verify(f.embedding).embedOne("制度", 9L, 2);
        verify(f.vectors).search(eq(1L), anyList(), eq(5), any());
    }

    @Test
    void explicitEmployeeIdentityWorksWithoutThreadLocalAndRevocationStopsLaterSearch() {
        f.grant("USER", 7, "read");
        assertEquals(List.of("授权知识正文"), f.search.searchChunksForEmployee(7L, 1L, "制度", 5));
        f.jdbc.update("DELETE FROM kb_space_acl");
        BizException denied = assertThrows(BizException.class, () -> f.search.searchRowsForEmployee(7L, 1L, "制度", 5));
        assertEquals(403, denied.getHttpStatus().value());
        verify(f.embedding).embedOne("制度", 9L, 2);
        verify(f.vectors).search(eq(1L), anyList(), anyInt(), any());
    }

    @Test
    void listSearchDoesNotLetAnAuthorizedPublicSpaceAuthorizeAPrivateSpace() {
        f.employee(7);
        assertThrows(BizException.class, () -> f.search.searchChunks(List.of(1L, 2L), "制度", 5));
        verifyNoInteractions(f.resolver, f.embedding, f.vectors);
    }

    @Test
    void adminSearchRejectsMissingEmployeeAndUnprivilegedAdminPrincipals() {
        assertThrows(BizException.class, () -> f.search.searchRowsForAdmin(1L, "制度", 5));
        f.employee(7);
        assertThrows(BizException.class, () -> f.search.searchChunksForAdmin(1L, "制度", 5));
        f.admin(List.of(), List.of("ADMIN_AGENT_MANAGE"));
        assertEquals(403, assertThrows(BizException.class, () -> f.search.searchRowsForAdmin(1L, "制度", 5)).getHttpStatus().value());
        verifyNoInteractions(f.resolver, f.embedding, f.vectors);
    }

    @Test
    void authorizedKnowledgeAdminAndSuperAdminCanExplicitlyTestPrivateKnowledge() {
        f.admin(List.of(), List.of("ADMIN_RAGFLOW_MANAGE"));
        assertEquals("授权知识正文", f.search.searchRowsForAdmin(1L, "制度", 5).get(0).contentText());
        f.admin(List.of("SUPER_ADMIN"), List.of());
        assertEquals(List.of("授权知识正文"), f.search.searchChunksForAdmin(1L, "制度", 5));
    }

    @Test
    void adminAuthorityDoesNotOverrideInactiveOrDeletedSpaces() {
        f.admin(List.of("SUPER_ADMIN"), List.of());
        f.jdbc.update("UPDATE kb_space SET status='INACTIVE' WHERE id=1");
        assertThrows(BizException.class, () -> f.search.searchRowsForAdmin(1L, "制度", 5));
        f.jdbc.update("UPDATE kb_space SET status='ACTIVE', is_deleted=1 WHERE id=1");
        assertThrows(BizException.class, () -> f.search.searchRowsForAdmin(1L, "制度", 5));
        verifyNoInteractions(f.resolver, f.embedding, f.vectors);
    }
}
