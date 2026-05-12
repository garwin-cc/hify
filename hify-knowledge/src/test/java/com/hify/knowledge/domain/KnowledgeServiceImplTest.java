package com.hify.knowledge.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.knowledge.api.KnowledgeChunkUpsertReq;
import com.hify.knowledge.api.KnowledgeSearchReq;
import com.hify.knowledge.api.KnowledgeSearchResp;
import com.hify.knowledge.infra.KnowledgeBaseMapper;
import com.hify.knowledge.infra.KnowledgeDocumentMapper;
import com.hify.knowledge.infra.RagRetrievalTraceMapper;
import com.hify.model.api.EmbeddingService;
import com.hify.model.api.ModelConfigService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeServiceImplTest {

    @Test
    void nextChunkStartFallsBackToEndWhenOverlapWouldMoveBackward() {
        String text = """
                Alpha beta gamma delta epsilon zeta eta theta iota kappa.
                Lambda mu nu xi omicron pi rho sigma tau upsilon.
                Phi chi psi omega alpha beta gamma delta epsilon.
                """;
        int currentStart = 80;
        int end = 120;

        int nextStart = KnowledgeServiceImpl.nextChunkStart(text, currentStart, end, 64);

        assertThat(nextStart).isEqualTo(end);
    }

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
                mock(ModelConfigService.class),
                mock(RagRetrievalTraceMapper.class));

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
                mock(ModelConfigService.class),
                mock(RagRetrievalTraceMapper.class));

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

    @Test
    void searchSimilarWithQueryTextUsesKnowledgeBaseDefaultsAndWritesTrace() {
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        RagRetrievalTraceMapper traceMapper = mock(RagRetrievalTraceMapper.class);
        FakeKnowledgeVectorRepository repository = new FakeKnowledgeVectorRepository();

        KnowledgeBasePo knowledgeBase = new KnowledgeBasePo();
        knowledgeBase.setId(1L);
        knowledgeBase.setName("研发制度");
        knowledgeBase.setEmbeddingModelConfigId(9L);
        knowledgeBase.setEnabled(1);
        knowledgeBase.setTopK(2);
        knowledgeBase.setScoreThreshold(0.8D);
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(knowledgeBase);
        when(embeddingService.embed(9L, List.of("报销流程"))).thenReturn(List.of(List.of(0.1, 0.2, 0.3)));

        KnowledgeSearchHit lowScore = new KnowledgeSearchHit();
        lowScore.setId(6L);
        lowScore.setKnowledgeBaseId(1L);
        lowScore.setDocumentId("doc-1");
        lowScore.setChunkIndex(0);
        lowScore.setContent("低相关内容");
        lowScore.setMetadataJson("{}");
        lowScore.setScore(0.79);

        KnowledgeSearchHit highScore = new KnowledgeSearchHit();
        highScore.setId(7L);
        highScore.setKnowledgeBaseId(1L);
        highScore.setDocumentId("doc-1");
        highScore.setChunkIndex(1);
        highScore.setContent("报销需要审批");
        highScore.setMetadataJson("{\"documentName\":\"制度.md\"}");
        highScore.setScore(0.91);
        repository.hits = List.of(lowScore, highScore);

        KnowledgeServiceImpl service = new KnowledgeServiceImpl(
                knowledgeBaseMapper,
                mock(KnowledgeDocumentMapper.class),
                repository,
                new ObjectMapper(),
                mock(ThreadPoolExecutor.class),
                embeddingService,
                mock(ModelConfigService.class),
                traceMapper);

        KnowledgeSearchReq req = new KnowledgeSearchReq();
        req.setKnowledgeBaseIds(List.of(1L));
        req.setQueryText("报销流程");
        req.setSourceType("TEST");
        req.setSourceId("manual");
        req.setIncludeTrace(true);

        List<KnowledgeSearchResp> results = service.searchSimilar(req);

        assertThat(repository.lastTopK).isEqualTo(2);
        assertThat(results).extracting(KnowledgeSearchResp::getId).containsExactly(7L);
        assertThat(results.get(0).getFinalScore()).isEqualTo(0.91);
        assertThat(results.get(0).getDocumentName()).isEqualTo("制度.md");
        assertThat(results.get(0).getTraceId()).isNotNull();
        verify(traceMapper).insert(org.mockito.ArgumentMatchers.<RagRetrievalTracePo>argThat(trace ->
                "TEST".equals(trace.getSourceType())
                        && "manual".equals(trace.getSourceId())
                        && "报销流程".equals(trace.getQueryText())
                        && trace.getHitCount() == 1
                        && trace.getLatencyMs() >= 0));
    }

    private static class FakeKnowledgeVectorRepository implements KnowledgeVectorRepository {
        private KnowledgeChunk savedChunk;
        private List<Double> savedEmbedding;
        private List<KnowledgeSearchHit> hits = new ArrayList<>();
        private int lastTopK;

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
            this.lastTopK = topK;
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
