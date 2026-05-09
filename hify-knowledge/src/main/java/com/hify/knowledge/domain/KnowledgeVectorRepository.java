package com.hify.knowledge.domain;

import java.util.List;

public interface KnowledgeVectorRepository {

    Long upsert(KnowledgeChunk chunk, List<Double> embedding);

    void saveDocumentChunks(List<KnowledgeChunk> chunks);

    List<KnowledgeSearchHit> search(List<Long> knowledgeBaseIds, List<Double> queryEmbedding, int topK);

    List<KnowledgeChunk> listByDocumentId(Long documentId);

    void deleteByKnowledgeBaseId(Long knowledgeBaseId);

    void deleteByDocumentId(Long documentId);
}
