package com.workforcehub.ragflow.service;

import com.workforcehub.auth.security.AdminUserDetails;
import com.workforcehub.auth.security.EmployeeUserDetails;
import com.workforcehub.ragflow.repository.RagflowGlobalSettingRow;
import com.workforcehub.ragflow.repository.RagflowRepository;
import com.workforcehub.ragflow.repository.RagflowSearchHitRow;
import com.workforcehub.ragflow.repository.RagflowVectorRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public final class KnowledgeAccessTestFixture {
    public final JdbcTemplate jdbc;
    public final RagflowRepository repository;
    public final RagflowConfigResolver resolver = mock(RagflowConfigResolver.class);
    public final RagflowEmbeddingClient embedding = mock(RagflowEmbeddingClient.class);
    public final RagflowVectorRepository vectors = mock(RagflowVectorRepository.class);
    public final RagflowKnowledgeSearchService search;

    public KnowledgeAccessTestFixture() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:knowledge_acl_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
            CREATE TABLE org_user (id BIGINT PRIMARY KEY, primary_dept_id BIGINT, position_id BIGINT,
                manager_user_id BIGINT, status VARCHAR(32) DEFAULT 'ACTIVE',
                employment_status VARCHAR(32) DEFAULT 'ACTIVE', is_deleted INT DEFAULT 0)
            """);
        jdbc.execute("""
            CREATE TABLE auth_user_account (id BIGINT PRIMARY KEY, user_id BIGINT,
                account_status VARCHAR(32) DEFAULT 'ACTIVE', is_deleted INT DEFAULT 0)
            """);
        jdbc.execute("CREATE TABLE org_department (id BIGINT PRIMARY KEY, status VARCHAR(32) DEFAULT 'ACTIVE', is_deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE org_position (id BIGINT PRIMARY KEY, status VARCHAR(32) DEFAULT 'ACTIVE', is_deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE admin_role (id BIGINT PRIMARY KEY, status VARCHAR(32) DEFAULT 'ACTIVE', is_deleted INT DEFAULT 0)");
        jdbc.execute("""
            CREATE TABLE kb_space (id BIGINT PRIMARY KEY, space_name VARCHAR(128), space_code VARCHAR(64),
                description VARCHAR(512), owner_type VARCHAR(32), owner_id BIGINT, created_by BIGINT,
                visibility VARCHAR(32), status VARCHAR(32) DEFAULT 'ACTIVE', is_deleted INT DEFAULT 0,
                document_count INT DEFAULT 1, chunk_count INT DEFAULT 1, embedding_provider VARCHAR(32),
                embedding_model VARCHAR(64), embedding_model_id BIGINT, embedding_dimension INT,
                rerank_enabled BOOLEAN, rerank_provider VARCHAR(32), rerank_model VARCHAR(64), rerank_model_id BIGINT,
                chunk_size INT, chunk_overlap INT, retrieval_top_k INT, score_threshold DECIMAL(8,4),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
            """);
        jdbc.execute("""
            CREATE TABLE kb_space_acl (id BIGINT AUTO_INCREMENT PRIMARY KEY, space_id BIGINT,
                subject_type VARCHAR(32), subject_id BIGINT, perm_code VARCHAR(32), status VARCHAR(32) DEFAULT 'ACTIVE')
            """);
        jdbc.execute("CREATE TABLE kb_document (id BIGINT PRIMARY KEY, title VARCHAR(128), is_deleted INT DEFAULT 0)");
        jdbc.update("INSERT INTO org_department (id) VALUES (10), (20)");
        jdbc.update("INSERT INTO org_position (id) VALUES (100), (200)");
        jdbc.update("INSERT INTO admin_role (id) VALUES (1), (2), (3)");
        jdbc.update("INSERT INTO org_user (id, primary_dept_id, position_id) VALUES (7, 10, 100), (8, 20, 200)");
        jdbc.update("INSERT INTO auth_user_account (id, user_id) VALUES (70, 7), (80, 8)");
        jdbc.update("INSERT INTO kb_space (id, space_name, space_code, visibility, created_by) VALUES (1, '制度', 'POLICY', 'PRIVATE', 7), (2, '公共', 'PUBLIC', 'PUBLIC', 99)");
        jdbc.update("INSERT INTO kb_document (id, title) VALUES (11, '制度正文')");
        repository = spy(new RagflowRepository(jdbc));
        doReturn(mock(RagflowGlobalSettingRow.class)).when(repository).getGlobalSetting();
        stubRetrievalConfig(resolver);
        when(embedding.embedOne(any(), anyLong(), anyInt())).thenReturn(List.of(0.1, 0.2));
        when(vectors.search(anyLong(), anyList(), anyInt(), any())).thenReturn(List.of(
            new RagflowSearchHitRow(111L, 11L, null, 1, "授权知识正文", 1, BigDecimal.ONE)));
        search = new RagflowKnowledgeSearchService(repository, embedding, resolver, vectors);
    }

    public static void stubRetrievalConfig(RagflowConfigResolver resolver) {
        when(resolver.resolve(any(), any())).thenReturn(new RagflowEffectiveConfig(9L, 2, 800, 100, 5, BigDecimal.ZERO));
    }

    public void grant(String type, long subjectId, String permission) {
        jdbc.update("INSERT INTO kb_space_acl (space_id, subject_type, subject_id, perm_code) VALUES (1, ?, ?, ?)", type, subjectId, permission);
    }

    public void employee(long userId) {
        EmployeeUserDetails principal = new EmployeeUserDetails(userId, userId * 10, "FIXTURE", "FIXTURE", "FIXTURE", "ACTIVE");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    public void admin(List<String> roles, List<String> permissions) {
        AdminUserDetails principal = new AdminUserDetails(7L, "FIXTURE", "FIXTURE", "FIXTURE", "ACTIVE", roles, permissions);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
