package com.hify.knowledge.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.knowledge.api.KnowledgeDocumentResp;
import com.hify.knowledge.api.KnowledgeChunkUpsertReq;
import com.hify.knowledge.api.KnowledgeSearchReq;
import com.hify.knowledge.api.KnowledgeSearchResp;
import com.hify.knowledge.infra.KnowledgeBaseMapper;
import com.hify.knowledge.infra.KnowledgeDocumentMapper;
import com.hify.knowledge.infra.RagRetrievalTraceMapper;
import com.hify.model.api.EmbeddingService;
import com.hify.model.api.ModelConfigService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
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
    void extractCsvTextFormatsRowsWithHeaderNames() {
        String csv = """
                name,age,city
                Alice,18,Shanghai
                Bob,20,Beijing
                """;

        String text = KnowledgeServiceImpl.extractCsvText(csv);

        assertThat(text).contains("name: Alice | age: 18 | city: Shanghai");
        assertThat(text).contains("name: Bob | age: 20 | city: Beijing");
    }

    @Test
    void processTextSegmentsEmbedsAndSavesChunksInBatches() {
        FakeKnowledgeVectorRepository repository = new FakeKnowledgeVectorRepository();
        FakeEmbeddingService embeddingService = new FakeEmbeddingService();
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(
                mock(KnowledgeBaseMapper.class),
                mock(KnowledgeDocumentMapper.class),
                repository,
                new ObjectMapper(),
                mock(ThreadPoolExecutor.class),
                embeddingService,
                mock(ModelConfigService.class),
                mock(RagRetrievalTraceMapper.class));
        KnowledgeDocumentPo document = new KnowledgeDocumentPo();
        document.setId(9L);
        document.setKnowledgeBaseId(1L);
        document.setName("large.txt");
        document.setFileType("txt");

        List<String> segments = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            segments.add(("word" + i + " ").repeat(140));
        }

        int chunkCount = service.processTextSegments(document, 11L, segments);

        assertThat(chunkCount).isGreaterThan(32);
        assertThat(embeddingService.batchSizes).hasSizeGreaterThan(1);
        assertThat(embeddingService.batchSizes).allMatch(size -> size <= 32);
        assertThat(repository.savedBatches).hasSizeGreaterThan(1);
        assertThat(repository.savedBatches.stream().mapToInt(List::size).sum()).isEqualTo(chunkCount);
    }

    @Test
    void processTextSegmentsScalesChunkSizeForLargeDocuments() throws Exception {
        FakeKnowledgeVectorRepository repository = new FakeKnowledgeVectorRepository();
        FakeEmbeddingService embeddingService = new FakeEmbeddingService();
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(
                mock(KnowledgeBaseMapper.class),
                mock(KnowledgeDocumentMapper.class),
                repository,
                new ObjectMapper(),
                mock(ThreadPoolExecutor.class),
                embeddingService,
                mock(ModelConfigService.class),
                mock(RagRetrievalTraceMapper.class));
        setIntField(service, "maxChunksPerDocument", 10);
        setIntField(service, "maxSplitSteps", 20);

        KnowledgeDocumentPo document = new KnowledgeDocumentPo();
        document.setId(9L);
        document.setKnowledgeBaseId(1L);
        document.setName("large.txt");
        document.setFileType("txt");
        document.setFileSize(20_000L);

        List<String> segments = List.of("alpha beta gamma delta ".repeat(1000));

        int chunkCount = service.processTextSegments(document, 11L, segments);

        assertThat(chunkCount).isLessThanOrEqualTo(10);
        assertThat(repository.savedBatches.stream().mapToInt(List::size).sum()).isEqualTo(chunkCount);
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

    @Test
    void getDocumentReturnsStructuredProcessingErrorFields() {
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeDocumentPo document = new KnowledgeDocumentPo();
        document.setId(7L);
        document.setKnowledgeBaseId(1L);
        document.setName("failed.txt");
        document.setFileType("txt");
        document.setParseStatus("FAILED");
        document.setProcessStage("FAILED");
        document.setErrorCode("EMBEDDING_CALL_FAILED");
        document.setFailedStage("EMBEDDING");
        document.setRetryable(1);
        document.setCancelRequested(0);
        document.setRetryCount(2);
        document.setErrorMessage("embedding timeout");
        when(documentMapper.selectById(7L)).thenReturn(document);
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(
                mock(KnowledgeBaseMapper.class),
                documentMapper,
                new FakeKnowledgeVectorRepository(),
                new ObjectMapper(),
                mock(ThreadPoolExecutor.class),
                mock(EmbeddingService.class),
                mock(ModelConfigService.class),
                mock(RagRetrievalTraceMapper.class));

        KnowledgeDocumentResp resp = service.getDocument(7L);

        assertThat(resp.getErrorCode()).isEqualTo("EMBEDDING_CALL_FAILED");
        assertThat(resp.getFailedStage()).isEqualTo("EMBEDDING");
        assertThat(resp.getRetryable()).isEqualTo(1);
        assertThat(resp.getCancelRequested()).isEqualTo(0);
        assertThat(resp.getRetryCount()).isEqualTo(2);
    }

    @Test
    void retryDocumentClearsOldChunksAndRequeuesFailedDocument() {
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        ThreadPoolExecutor executor = mock(ThreadPoolExecutor.class);
        FakeKnowledgeVectorRepository repository = new FakeKnowledgeVectorRepository();
        KnowledgeDocumentPo document = new KnowledgeDocumentPo();
        document.setId(7L);
        document.setKnowledgeBaseId(1L);
        document.setName("failed.txt");
        document.setFileType("txt");
        document.setParseStatus("FAILED");
        document.setChunkCount(0);
        document.setRetryable(1);
        when(documentMapper.selectById(7L)).thenReturn(document);
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(
                mock(KnowledgeBaseMapper.class),
                documentMapper,
                repository,
                new ObjectMapper(),
                executor,
                mock(EmbeddingService.class),
                mock(ModelConfigService.class),
                mock(RagRetrievalTraceMapper.class));

        service.retryDocument(7L);

        assertThat(repository.deletedDocumentIds).containsExactly(7L);
        verify(executor).execute(any(Runnable.class));
    }

    @Test
    void cancelPendingDocumentMarksCanceledAndDeletesChunks() {
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        FakeKnowledgeVectorRepository repository = new FakeKnowledgeVectorRepository();
        KnowledgeDocumentPo document = new KnowledgeDocumentPo();
        document.setId(8L);
        document.setKnowledgeBaseId(1L);
        document.setName("pending.txt");
        document.setFileType("txt");
        document.setParseStatus("PENDING");
        when(documentMapper.selectById(8L)).thenReturn(document);
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(
                mock(KnowledgeBaseMapper.class),
                documentMapper,
                repository,
                new ObjectMapper(),
                mock(ThreadPoolExecutor.class),
                mock(EmbeddingService.class),
                mock(ModelConfigService.class),
                mock(RagRetrievalTraceMapper.class));

        service.cancelDocument(8L);

        assertThat(repository.deletedDocumentIds).containsExactly(8L);
        verify(documentMapper).updateById(org.mockito.ArgumentMatchers.<KnowledgeDocumentPo>argThat(updated ->
                "CANCELED".equals(updated.getParseStatus())
                        && "CANCELED_BY_USER".equals(updated.getErrorCode())
                        && Integer.valueOf(0).equals(updated.getRetryable())
                        && Integer.valueOf(1).equals(updated.getCancelRequested())));
    }

    private static class FakeKnowledgeVectorRepository implements KnowledgeVectorRepository {
        private KnowledgeChunk savedChunk;
        private List<Double> savedEmbedding;
        private List<KnowledgeSearchHit> hits = new ArrayList<>();
        private List<List<KnowledgeChunk>> savedBatches = new ArrayList<>();
        private List<Long> deletedDocumentIds = new ArrayList<>();
        private int lastTopK;

        @Override
        public Long upsert(KnowledgeChunk chunk, List<Double> embedding) {
            this.savedChunk = chunk;
            this.savedEmbedding = embedding;
            return 100L;
        }

        @Override
        public void saveDocumentChunks(List<KnowledgeChunk> chunks) {
            savedBatches.add(new ArrayList<>(chunks));
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
            deletedDocumentIds.add(documentId);
        }
    }

    private static class FakeEmbeddingService implements EmbeddingService {
        private final List<Integer> batchSizes = new ArrayList<>();

        @Override
        public List<List<Double>> embed(Long modelConfigId, List<String> inputs) {
            batchSizes.add(inputs.size());
            return inputs.stream().map(input -> List.of(0.1, 0.2, 0.3)).toList();
        }
    }

    private static void setIntField(Object target, String fieldName, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }
}
