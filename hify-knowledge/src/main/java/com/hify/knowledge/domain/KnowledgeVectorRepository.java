package com.hify.knowledge.domain;

import com.hify.knowledge.api.KnowledgeSearchFilter;

import java.util.List;

public interface KnowledgeVectorRepository {

    Long upsert(KnowledgeChunk chunk, List<Double> embedding);

    void saveDocumentChunks(List<KnowledgeChunk> chunks);

    List<KnowledgeSearchHit> search(List<Long> knowledgeBaseIds, List<Double> queryEmbedding, int topK);

    default List<KnowledgeSearchHit> search(List<Long> knowledgeBaseIds, List<Double> queryEmbedding,
                                            int topK, KnowledgeSearchFilter filter) {
        return search(knowledgeBaseIds, queryEmbedding, topK);
    }

    default List<KnowledgeSearchHit> keywordSearch(List<Long> knowledgeBaseIds, String queryText,
                                                   int topK, KnowledgeSearchFilter filter) {
        return List.of();
    }

    List<KnowledgeChunk> listByDocumentId(Long documentId);

    void deleteByKnowledgeBaseId(Long knowledgeBaseId);

    void deleteByDocumentId(Long documentId);

    default void deleteByDocumentIdAndIndexVersion(Long documentId, Long indexVersion) {
        deleteByDocumentId(documentId);
    }

    default void activateIndexVersion(Long knowledgeBaseId, Long indexVersion) {
    }
}
