package com.workforcehub.ragflow.service;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.ragflow.repository.RagflowSearchHitRow;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/** Standalone evidence recorder; copied only into disposable baseline/candidate/rollback runners. */
class KnowledgeAclEvidenceProbe {
    @Test
    void recordLegacySearchBoundary() throws Exception {
        KnowledgeAccessTestFixture f = new KnowledgeAccessTestFixture();
        f.jdbc.update("INSERT INTO org_user (id, primary_dept_id, position_id) VALUES (2003, 20, 200)");
        f.jdbc.update("INSERT INTO auth_user_account (id, user_id) VALUES (20030, 2003)");
        f.jdbc.update("UPDATE kb_space SET owner_type='USER', owner_id=7, created_by=7, visibility='PRIVATE' WHERE id=1");
        when(f.vectors.search(anyLong(), anyList(), anyInt(), any())).thenReturn(List.of(
            new RagflowSearchHitRow(111L, 11L, null, 1, "SECRET", 1, BigDecimal.ONE)));
        f.employee(2003);
        clearInvocations(f.embedding, f.vectors);
        String status;
        String content;
        try {
            Object result = RagflowKnowledgeSearchService.class
                .getMethod("searchRows", Long.class, String.class, int.class)
                .invoke(f.search, 1L, "制度", 5);
            List<?> rows = (List<?>) result;
            status = "HTTP_200";
            content = rows.stream().map(row -> ((RagflowSearchHitRow) row).contentText()).toList().toString();
        } catch (InvocationTargetException error) {
            if (!(error.getCause() instanceof BizException denied)) throw error;
            status = "HTTP_" + denied.getHttpStatus().value();
            content = "[]";
        } finally {
            SecurityContextHolder.clearContext();
        }
        long embeddingCalls = mockingDetails(f.embedding).getInvocations().stream()
            .filter(invocation -> invocation.getMethod().getName().equals("embedOne")).count();
        long vectorCalls = mockingDetails(f.vectors).getInvocations().stream()
            .filter(invocation -> invocation.getMethod().getName().equals("search")).count();
        System.out.println("INPUT operator_type=USER operator_id=2003 space_id=1 visibility=PRIVATE query=制度 top_k=5 acl=[]");
        System.out.println("RESULT status=" + status + " content=" + content
            + " embedding_calls=" + embeddingCalls + " vector_calls=" + vectorCalls);
    }
}
