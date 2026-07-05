package com.workforcehub.ragflow.service;

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
    private final RagflowVectorRepository vectorRepository;

    public RagflowKnowledgeSearchService(RagflowRepository ragflowRepository,
                                         RagflowEmbeddingClient embeddingClient,
                                         RagflowVectorRepository vectorRepository) {
        this.ragflowRepository = ragflowRepository;
        this.embeddingClient = embeddingClient;
        this.vectorRepository = vectorRepository;
    }

    public List<String> searchChunks(Long spaceId, String query, int topK) {
        return searchRows(spaceId, query, topK).stream()
            .map(RagflowSearchHitRow::contentText)
            .toList();
    }

    public List<String> searchChunks(List<Long> spaceIds, String query, int topK) {
        return spaceIds.stream()
            .flatMap(spaceId -> searchChunks(spaceId, query, topK).stream())
            .limit(Math.max(topK, 1))
            .toList();
    }

    // 向量检索：先 embed 查询文本，再按相似度阈值过滤，最后补上文档标题
    public List<RagflowSearchHitRow> searchRows(Long spaceId, String query, int topK) {
        RagflowSpaceRow space = ragflowRepository.getSpaceOrThrow(spaceId);
        vectorRepository.requireAvailable();
        List<RagflowSearchHitRow> rows = vectorRepository.search(
            spaceId,
            embeddingClient.embedOne(query),
            Math.max(topK, 1),
            space.scoreThreshold()
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
