package com.workforcehub.ragflow.service;

import com.workforcehub.auth.security.AdminUserDetails;
import com.workforcehub.auth.service.AdminPermissionService;
import com.workforcehub.common.enums.UserType;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.ragflow.repository.RagflowRepository;
import com.workforcehub.ragflow.repository.RagflowSearchHitRow;
import com.workforcehub.ragflow.repository.RagflowSpaceRow;
import com.workforcehub.ragflow.repository.RagflowVectorRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RagflowKnowledgeSearchService {

    private final RagflowRepository ragflowRepository;
    private final RagflowEmbeddingClient embeddingClient;
    private final RagflowConfigResolver configResolver;
    private final RagflowVectorRepository vectorRepository;

    public RagflowKnowledgeSearchService(RagflowRepository ragflowRepository,
                                         RagflowEmbeddingClient embeddingClient,
                                         RagflowConfigResolver configResolver,
                                         RagflowVectorRepository vectorRepository) {
        this.ragflowRepository = ragflowRepository;
        this.embeddingClient = embeddingClient;
        this.configResolver = configResolver;
        this.vectorRepository = vectorRepository;
    }

    public List<String> searchChunks(Long spaceId, String query, int topK) {
        return searchChunksForEmployee(requireEmployeeUserId(), spaceId, query, topK);
    }

    public List<String> searchChunksForEmployee(Long operatorUserId, Long spaceId, String query, int topK) {
        return searchRowsForEmployee(operatorUserId, spaceId, query, topK).stream()
            .map(RagflowSearchHitRow::contentText)
            .toList();
    }

    public List<String> searchChunks(List<Long> spaceIds, String query, int topK) {
        Long userId = requireEmployeeUserId();
        // 全部授权后才开始检索，避免混合空间在遇到拒绝前已向模型发送查询。
        for (Long spaceId : spaceIds) requireReadableSpace(userId, spaceId);
        return spaceIds.stream()
            .flatMap(spaceId -> searchChunksForEmployee(userId, spaceId, query, topK).stream())
            .limit(Math.max(topK, 1))
            .toList();
    }

    // 向量检索：先 embed 查询文本，再按相似度阈值过滤，最后补上文档标题
    public List<RagflowSearchHitRow> searchRows(Long spaceId, String query, int topK) {
        return searchRowsForEmployee(requireEmployeeUserId(), spaceId, query, topK);
    }

    public List<RagflowSearchHitRow> searchRowsForEmployee(Long operatorUserId, Long spaceId, String query, int topK) {
        return retrieve(requireReadableSpace(operatorUserId, spaceId), query, topK);
    }

    public List<String> searchChunksForAdmin(Long spaceId, String query, int topK) {
        return searchRowsForAdmin(spaceId, query, topK).stream().map(RagflowSearchHitRow::contentText).toList();
    }

    public List<RagflowSearchHitRow> searchRowsForAdmin(Long spaceId, String query, Integer topK) {
        var user = CurrentUserHolder.current();
        if (user == null) throw new BizException(401, "未登录");
        if (!(user instanceof AdminUserDetails admin) || user.getUserType() != UserType.ADMIN || !admin.isEnabled()
            || (!AdminPermissionService.hasSuperAdminRole(admin.getRoleCodes())
                && admin.getAuthorities().stream().noneMatch(a -> "ADMIN_RAGFLOW_MANAGE".equals(a.getAuthority())))) {
            throw new BizException(403, "无权试运行知识库检索");
        }
        RagflowSpaceRow space = ragflowRepository.getSpaceOrThrow(spaceId);
        if (!"ACTIVE".equals(space.status())) throw new BizException(403, "知识库已停用");
        return retrieve(space, query, topK);
    }

    private Long requireEmployeeUserId() {
        var user = CurrentUserHolder.current();
        if (user == null) throw new BizException(401, "未登录");
        if (user.getUserType() != UserType.USER) throw new BizException(403, "仅支持员工知识库检索");
        return user.getUserId();
    }

    private RagflowSpaceRow requireReadableSpace(Long operatorUserId, Long spaceId) {
        if (operatorUserId == null) throw new BizException(401, "缺少知识库检索操作者");
        RagflowSpaceRow space = ragflowRepository.getSpaceOrThrow(spaceId);
        if (!"ACTIVE".equals(space.status()) || !ragflowRepository.canReadSpace(spaceId, operatorUserId)) {
            throw new BizException(403, "无权检索该知识库");
        }
        return space;
    }

    private List<RagflowSearchHitRow> retrieve(RagflowSpaceRow space, String query, Integer topK) {
        RagflowEffectiveConfig config = configResolver.resolve(space, ragflowRepository.getGlobalSetting());
        vectorRepository.requireAvailable();
        List<RagflowSearchHitRow> rows = vectorRepository.search(
            space.id(),
            embeddingClient.embedOne(query, config.embeddingModelId(), config.embeddingDimension()),
            Math.max(topK == null ? (config.retrievalTopK() == null ? 5 : config.retrievalTopK()) : topK, 1),
            config.scoreThreshold()
        );
        Map<Long, String> titles = ragflowRepository.documentTitles(
            rows.stream().map(RagflowSearchHitRow::documentId).distinct().toList()
        );
        return rows.stream()
            .filter(row -> titles.containsKey(row.documentId()))
            .map(row -> row.withDocumentTitle(titles.get(row.documentId())))
            .toList();
    }
}
