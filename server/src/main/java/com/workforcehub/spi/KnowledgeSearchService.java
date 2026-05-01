package com.workforcehub.spi;

import java.util.List;

/**
 * 知识库检索接口
 * 由 module-kb 实现，module-agent 通过 SPI 调用
 */
public interface KnowledgeSearchService {

    /**
     * 语义检索知识切片
     *
     * @param spaceId 知识空间ID
     * @param query 查询文本
     * @param topK 返回数量
     * @return 相关切片文本列表
     */
    List<String> searchChunks(Long spaceId, String query, int topK);

    /**
     * 语义检索（多空间）
     *
     * @param spaceIds 知识空间ID列表
     * @param query 查询文本
     * @param topK 每个空间返回数量
     * @return 相关切片文本列表
     */
    List<String> searchChunks(List<Long> spaceIds, String query, int topK);
}
