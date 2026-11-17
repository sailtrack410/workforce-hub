package com.workforcehub.ragflow.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RagflowSearchTestResponse {
    private String query;
    private List<Hit> hits = List.of();

    @Data
    public static class Hit {
        private Long chunkId;
        private Long documentId;
        private String documentTitle;
        private Integer chunkNo;
        private String contentText;
        private Integer pageNo;
        private BigDecimal score;
    }
}
