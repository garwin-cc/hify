package com.hify.knowledge.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.knowledge.api.KnowledgeChunkUpsertReq;
import com.hify.knowledge.api.KnowledgeSearchReq;
import com.hify.knowledge.api.KnowledgeSearchResp;
import com.hify.knowledge.infra.KnowledgeBaseMapper;
import com.hify.knowledge.infra.KnowledgeDocumentMapper;
import com.hify.model.api.EmbeddingService;
import com.hify.model.api.ModelConfigService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KnowledgeServiceImplTest {

    @Test
    void upsertChunkPersistsChunkWithMetadataJson() {
        FakeKnowledgeVectorRepository repository = new FakeKnowledgeVectorRepository();
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(
                mock(KnowledgeBaseMapper.class),
                mock(KnowledgeDocumentMapper.class),
                repository,
                new ObjectMapper(),
                mock(ThreadPoolExecutor.class),
                mock(EmbeddingService.class),
                mock(ModelConfigService.class));

        KnowledgeChunkUpsertReq req = new KnowledgeChunkUpsertReq();
        req.setKnowledgeBaseId(1L);
        req.setDocumentId("doc-1");
        req.setChunkIndex(0);
        req.setContent("hello vector");
        req.setEmbedding(List.of(0.1, 0.2, 0.3));
        req.setMetadata(Map.of("source", "manual"));

        Long id = service.upsertChunk(req);

        assertThat(id).isEqualTo(100L);
        assertThat(repository.savedChunk.getKnowledgeBaseId()).isEqualTo(1L);
        assertThat(repository.savedChunk.getDocumentId()).isEqualTo("doc-1");
        assertThat(repository.savedChunk.getMetadataJson()).contains("\"source\":\"manual\"");
        assertThat(repository.savedEmbedding).containsExactly(0.1, 0.2, 0.3);
    }

    @Test
    void searchSimilarMapsRepositoryHits() {
        FakeKnowledgeVectorRepository repository = new FakeKnowledgeVectorRepository();
        KnowledgeSearchHit hit = new KnowledgeSearchHit();
        hit.setId(7L);
        hit.setKnowledgeBaseId(1L);
        hit.setDocumentId("doc-1");
        hit.setChunkIndex(2);
        hit.setContent("matched chunk");
        hit.setMetadataJson("{\"page\":3}");
        hit.setScore(0.91);
        repository.hits = List.of(hit);
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(
                mock(KnowledgeBaseMapper.class),
                mock(KnowledgeDocumentMapper.class),
                repository,
                new ObjectMapper(),
                mock(ThreadPoolExecutor.class),
                mock(EmbeddingService.class),
                mock(ModelConfigService.class));

        KnowledgeSearchReq req = new KnowledgeSearchReq();
        req.setKnowledgeBaseIds(List.of(1L));
        req.setQueryEmbedding(List.of(0.1, 0.2, 0.3));
        req.setTopK(3);

        List<KnowledgeSearchResp> results = service.searchSimilar(req);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(7L);
        assertThat(results.get(0).getScore()).isEqualTo(0.91);
        assertThat(results.get(0).getMetadata()).containsEntry("page", 3);
    }

    private static class FakeKnowledgeVectorRepository implements KnowledgeVectorRepository {
        private KnowledgeChunk savedChunk;
        private List<Double> savedEmbedding;
        private List<KnowledgeSearchHit> hits = new ArrayList<>();

        @Override
        public Long upsert(KnowledgeChunk chunk, List<Double> embedding) {
            this.savedChunk = chunk;
            this.savedEmbedding = embedding;
            return 100L;
        }

        @Override
        public void saveDocumentChunks(List<KnowledgeChunk> chunks) {
        }

        @Override
        public List<KnowledgeSearchHit> search(List<Long> knowledgeBaseIds, List<Double> queryEmbedding, int topK) {
            return hits;
        }

        @Override
        public List<KnowledgeChunk> listByDocumentId(Long documentId) {
            return List.of();
        }

        @Override
        public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        }

        @Override
        public void deleteByDocumentId(Long documentId) {
        }
    }
}
