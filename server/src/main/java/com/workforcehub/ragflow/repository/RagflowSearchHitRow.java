package com.workforcehub.ragflow.repository;

import java.math.BigDecimal;

public record RagflowSearchHitRow(
    Long chunkId,
    Long documentId,
    String documentTitle,
    Integer chunkNo,
    String contentText,
    Integer pageNo,
    BigDecimal score
) {
    public RagflowSearchHitRow withDocumentTitle(String title) {
        return new RagflowSearchHitRow(chunkId, documentId, title, chunkNo, contentText, pageNo, score);
    }
}
