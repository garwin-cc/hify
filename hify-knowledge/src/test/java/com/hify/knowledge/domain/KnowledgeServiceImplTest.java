package com.hify.knowledge.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hify.knowledge.api.KnowledgeDocumentResp;
import com.hify.knowledge.api.KnowledgeChunkUpsertReq;
import com.hify.knowledge.api.KnowledgeRebuildReq;
import com.hify.knowledge.api.KnowledgeSearchFilter;
import com.hify.knowledge.api.KnowledgeSearchReq;
import com.hify.knowledge.api.KnowledgeSearchResp;
import com.hify.knowledge.api.UpdateKnowledgeRetrievalConfigReq;
import com.hify.knowledge.infra.KnowledgeBaseMapper;
import com.hify.knowledge.infra.KnowledgeDocumentMapper;
import com.hify.knowledge.infra.KnowledgeTaskMapper;
import com.hify.knowledge.infra.RagRetrievalTraceMapper;
import com.hify.model.api.EmbeddingService;
import com.hify.model.api.ModelConfigService;
import com.hify.model.api.RerankRequest;
import com.hify.model.api.RerankResult;
import com.hify.model.api.RerankService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeServiceImplTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), KnowledgeBasePo.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), KnowledgeDocumentPo.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), KnowledgeTaskPo.class);
    }

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
    void readTextFileDecodesGb18030CsvWithoutMalformedInputError() throws Exception {
        Path csv = tempDir.resolve("gb18030.csv");
        Files.write(csv, "名称,说明\n蘑菇,中文知识库\n".getBytes(Charset.forName("GB18030")));

        String text = KnowledgeServiceImpl.readTextFile(csv);

        assertThat(text).contains("名称,说明");
        assertThat(text).contains("蘑菇,中文知识库");
    }

    @Test
    void processTextSegmentsEmbedsAndSavesChunksInBatches() {
        FakeKnowledgeVectorRepository repository = new FakeKnowledgeVectorRepository();
        FakeEmbeddingService embeddingService = new FakeEmbeddingService();
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(
                mock(KnowledgeBaseMapper.class),
                mock(KnowledgeDocumentMapper.class),
                mock(KnowledgeTaskMapper.class),
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
                mock(KnowledgeTaskMapper.class),
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
    void processTextSegmentsCapsEmbeddingChunkAndBatchSizeForLargeDocuments() throws Exception {
        FakeKnowledgeVectorRepository repository = new FakeKnowledgeVectorRepository();
        FakeEmbeddingService embeddingService = new FakeEmbeddingService();
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(
                mock(KnowledgeBaseMapper.class),
                mock(KnowledgeDocumentMapper.class),
                mock(KnowledgeTaskMapper.class),
                repository,
                new ObjectMapper(),
                mock(ThreadPoolExecutor.class),
                embeddingService,
                mock(ModelConfigService.class),
                mock(RagRetrievalTraceMapper.class));
        setIntField(service, "maxChunksPerDocument", 50_000);

        KnowledgeDocumentPo document = new KnowledgeDocumentPo();
        document.setId(12L);
        document.setKnowledgeBaseId(1L);
        document.setName("large.csv");
        document.setFileType("csv");
        document.setFileSize(51L * 1024 * 1024);

        int chunkCount = service.processTextSegments(document, 11L, List.of("a".repeat(80_000)));

        assertThat(chunkCount).isGreaterThan(1);
        assertThat(repository.savedBatches.stream()
                .flatMap(List::stream)
                .map(KnowledgeChunk::getContent))
                .allMatch(content -> content.length() <= 4000);
        assertThat(embeddingService.batchCharLengths).allMatch(length -> length <= 24_000);
    }

    @Test
    void upsertChunkPersistsChunkWithMetadataJson() {
        FakeKnowledgeVectorRepository repository = new FakeKnowledgeVectorRepository();
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(
                mock(KnowledgeBaseMapper.class),
                mock(KnowledgeDocumentMapper.class),
                mock(KnowledgeTaskMapper.class),
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
                mock(KnowledgeTaskMapper.class),
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
                mock(KnowledgeTaskMapper.class),
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
                mock(KnowledgeTaskMapper.class),
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
                mock(KnowledgeTaskMapper.class),
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
                mock(KnowledgeTaskMapper.class),
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

    @Test
    void revectorizeDocumentCreatesPersistentTaskAndQueuesDocument() {
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeTaskMapper taskMapper = mock(KnowledgeTaskMapper.class);
        ThreadPoolExecutor executor = mock(ThreadPoolExecutor.class);
        FakeKnowledgeVectorRepository repository = new FakeKnowledgeVectorRepository();
        KnowledgeBasePo knowledgeBase = knowledgeBase(1L, 1L, 11L);
        KnowledgeDocumentPo document = document(7L, 1L, "DONE");
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(knowledgeBase);
        when(documentMapper.selectById(7L)).thenReturn(document);
        doAnswer(invocation -> {
            KnowledgeTaskPo task = invocation.getArgument(0);
            task.setId(99L);
            return 1;
        }).when(taskMapper).insert(any(KnowledgeTaskPo.class));
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(
                knowledgeBaseMapper,
                documentMapper,
                taskMapper,
                repository,
                new ObjectMapper(),
                executor,
                mock(EmbeddingService.class),
                mock(ModelConfigService.class),
                mock(RagRetrievalTraceMapper.class));

        Long taskId = service.revectorizeDocument(7L, new KnowledgeRebuildReq());

        assertThat(taskId).isEqualTo(99L);
        assertThat(repository.deletedDocumentIds).containsExactly(7L);
        verify(taskMapper).insert(org.mockito.ArgumentMatchers.<KnowledgeTaskPo>argThat(task ->
                "REVECTORIZE".equals(task.getTaskType())
                        && Long.valueOf(7L).equals(task.getDocumentId())
                        && "DOCUMENT_UPDATED".equals(task.getReason())));
        verify(documentMapper).updateById(org.mockito.ArgumentMatchers.<KnowledgeDocumentPo>argThat(updated ->
                Long.valueOf(99L).equals(updated.getProcessingTaskId())
                        && "PENDING".equals(updated.getParseStatus())));
        verify(executor).execute(any(Runnable.class));
    }

    @Test
    void rebuildKnowledgeBaseCreatesDocumentTasks() {
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeTaskMapper taskMapper = mock(KnowledgeTaskMapper.class);
        ThreadPoolExecutor executor = mock(ThreadPoolExecutor.class);
        FakeKnowledgeVectorRepository repository = new FakeKnowledgeVectorRepository();
        KnowledgeBasePo knowledgeBase = knowledgeBase(1L, 1L, 11L);
        KnowledgeDocumentPo first = document(7L, 1L, "DONE");
        KnowledgeDocumentPo second = document(8L, 1L, "FAILED");
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(knowledgeBase);
        when(documentMapper.selectList(any())).thenReturn(List.of(first, second));
        final long[] id = {100L};
        doAnswer(invocation -> {
            KnowledgeTaskPo task = invocation.getArgument(0);
            task.setId(id[0]++);
            return 1;
        }).when(taskMapper).insert(any(KnowledgeTaskPo.class));
        KnowledgeRebuildReq req = new KnowledgeRebuildReq();
        req.setReason("MANUAL_REBUILD");
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(
                knowledgeBaseMapper,
                documentMapper,
                taskMapper,
                repository,
                new ObjectMapper(),
                executor,
                mock(EmbeddingService.class),
                mock(ModelConfigService.class),
                mock(RagRetrievalTraceMapper.class));

        Long taskId = service.rebuildKnowledgeBaseIndex(1L, req);

        assertThat(taskId).isEqualTo(100L);
        assertThat(repository.deletedDocumentIds).isEmpty();
        assertThat(repository.deletedVersionedDocumentIds).containsExactly(7L, 8L);
        verify(knowledgeBaseMapper).updateById(org.mockito.ArgumentMatchers.<KnowledgeBasePo>argThat(updated ->
                "REBUILDING".equals(updated.getIndexStatus())
                        && Long.valueOf(2L).equals(updated.getBuildingIndexVersion())));
        verify(executor, times(2)).execute(any(Runnable.class));
        verify(taskMapper).insert(org.mockito.ArgumentMatchers.<KnowledgeTaskPo>argThat(task ->
                "REBUILD_INDEX".equals(task.getTaskType())
                        && "KNOWLEDGE_BASE".equals(task.getTargetType())));
        verify(taskMapper).insert(org.mockito.ArgumentMatchers.<KnowledgeTaskPo>argThat(task ->
                "DOCUMENT_PROCESS".equals(task.getTaskType())
                        && Long.valueOf(7L).equals(task.getDocumentId())));
    }

    @Test
    void updateRetrievalConfigAcceptsFulltextHybridAlphaAndMetadataDefaults() {
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        KnowledgeBasePo knowledgeBase = knowledgeBase(1L, 1L, 11L);
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(knowledgeBase);
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(
                knowledgeBaseMapper,
                mock(KnowledgeDocumentMapper.class),
                mock(KnowledgeTaskMapper.class),
                new FakeKnowledgeVectorRepository(),
                new ObjectMapper(),
                mock(ThreadPoolExecutor.class),
                mock(EmbeddingService.class),
                mock(ModelConfigService.class),
                mock(RagRetrievalTraceMapper.class));
        UpdateKnowledgeRetrievalConfigReq req = new UpdateKnowledgeRetrievalConfigReq();
        req.setRetrievalMode("FULLTEXT");
        req.setHybridAlpha(0.35D);
        req.setMetadataFilterEnabled(1);
        req.setDefaultMetadataFilter(Map.of("department", "finance"));

        service.updateRetrievalConfig(1L, req);

        verify(knowledgeBaseMapper).updateById(org.mockito.ArgumentMatchers.<KnowledgeBasePo>argThat(updated ->
                "FULLTEXT".equals(updated.getRetrievalMode())
                        && Double.valueOf(0.35D).equals(updated.getHybridAlpha())
                        && Integer.valueOf(1).equals(updated.getMetadataFilterEnabled())
                        && updated.getDefaultMetadataFilterJson().contains("finance")));
    }

    @Test
    void searchSimilarAppliesProjectFilterBeforeRepositorySearch() {
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        FakeKnowledgeVectorRepository repository = new FakeKnowledgeVectorRepository();
        KnowledgeBasePo own = knowledgeBase(1L, 10L, 11L);
        KnowledgeBasePo other = knowledgeBase(2L, 20L, 11L);
        other.setVisibility("PROJECT");
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(own);
        when(knowledgeBaseMapper.selectById(2L)).thenReturn(other);
        KnowledgeSearchHit hit = hit(7L, 1L, "own", 0.9);
        repository.hits = List.of(hit);
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(
                knowledgeBaseMapper,
                mock(KnowledgeDocumentMapper.class),
                mock(KnowledgeTaskMapper.class),
                repository,
                new ObjectMapper(),
                mock(ThreadPoolExecutor.class),
                mock(EmbeddingService.class),
                mock(ModelConfigService.class),
                mock(RagRetrievalTraceMapper.class));
        KnowledgeSearchReq req = new KnowledgeSearchReq();
        req.setKnowledgeBaseIds(List.of(1L, 2L));
        req.setQueryEmbedding(List.of(0.1, 0.2, 0.3));
        req.setProjectId(10L);
        req.setDepartment("finance");

        List<KnowledgeSearchResp> results = service.searchSimilar(req);

        assertThat(results).hasSize(1);
        assertThat(repository.lastKnowledgeBaseIds).containsExactly(1L);
        assertThat(repository.lastFilter.getProjectId()).isEqualTo(10L);
        assertThat(repository.lastFilter.getDepartment()).isEqualTo("finance");
    }

    @Test
    void hybridSearchMergesVectorAndKeywordScores() {
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        FakeKnowledgeVectorRepository repository = new FakeKnowledgeVectorRepository();
        KnowledgeBasePo knowledgeBase = knowledgeBase(1L, 1L, 9L);
        knowledgeBase.setRetrievalMode("HYBRID");
        knowledgeBase.setHybridAlpha(0.2D);
        knowledgeBase.setScoreThreshold(0D);
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(knowledgeBase);
        when(embeddingService.embed(9L, List.of("报销流程"))).thenReturn(List.of(List.of(0.1, 0.2, 0.3)));
        KnowledgeSearchHit vectorHit = hit(7L, 1L, "vector", 0.8);
        vectorHit.setVectorScore(0.8);
        KnowledgeSearchHit keywordHit = hit(7L, 1L, "vector", 0.5);
        keywordHit.setKeywordScore(0.5);
        repository.hits = List.of(vectorHit);
        repository.keywordHits = List.of(keywordHit);
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(
                knowledgeBaseMapper,
                mock(KnowledgeDocumentMapper.class),
                mock(KnowledgeTaskMapper.class),
                repository,
                new ObjectMapper(),
                mock(ThreadPoolExecutor.class),
                embeddingService,
                mock(ModelConfigService.class),
                mock(RagRetrievalTraceMapper.class));
        KnowledgeSearchReq req = new KnowledgeSearchReq();
        req.setKnowledgeBaseIds(List.of(1L));
        req.setQueryText("报销流程");

        List<KnowledgeSearchResp> results = service.searchSimilar(req);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getVectorScore()).isEqualTo(0.8);
        assertThat(results.get(0).getKeywordScore()).isEqualTo(0.5);
        assertThat(results.get(0).getFinalScore()).isEqualTo(0.56);
        assertThat(results.get(0).getRetrievalMode()).isEqualTo("HYBRID");
    }

    @Test
    void fulltextSearchUsesKeywordOnlyWithoutEmbedding() {
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        FakeKnowledgeVectorRepository repository = new FakeKnowledgeVectorRepository();
        KnowledgeBasePo knowledgeBase = knowledgeBase(1L, 1L, 9L);
        knowledgeBase.setRetrievalMode("FULLTEXT");
        knowledgeBase.setScoreThreshold(0D);
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(knowledgeBase);
        repository.keywordHits = List.of(hit(9L, 1L, "keyword", 0.6));
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(
                knowledgeBaseMapper,
                mock(KnowledgeDocumentMapper.class),
                mock(KnowledgeTaskMapper.class),
                repository,
                new ObjectMapper(),
                mock(ThreadPoolExecutor.class),
                embeddingService,
                mock(ModelConfigService.class),
                mock(RagRetrievalTraceMapper.class));
        KnowledgeSearchReq req = new KnowledgeSearchReq();
        req.setKnowledgeBaseIds(List.of(1L));
        req.setQueryText("报销流程");

        List<KnowledgeSearchResp> results = service.searchSimilar(req);

        assertThat(results).extracting(KnowledgeSearchResp::getId).containsExactly(9L);
        assertThat(results.get(0).getKeywordScore()).isEqualTo(0.6);
        assertThat(results.get(0).getRetrievalMode()).isEqualTo("FULLTEXT");
        verify(embeddingService, never()).embed(any(), any());
    }

    @Test
    void searchSimilarReranksWhenEnabledAndFallsBackOnFailure() {
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        FakeKnowledgeVectorRepository repository = new FakeKnowledgeVectorRepository();
        KnowledgeBasePo knowledgeBase = knowledgeBase(1L, 1L, 9L);
        knowledgeBase.setScoreThreshold(0D);
        knowledgeBase.setRerankEnabled(1);
        knowledgeBase.setRerankModelConfigId(88L);
        knowledgeBase.setRerankTopN(2);
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(knowledgeBase);
        repository.hits = List.of(hit(7L, 1L, "first", 0.7), hit(8L, 1L, "second", 0.9));
        FakeRerankService rerankService = new FakeRerankService();
        rerankService.results = List.of(new RerankResult(1, 0.99D), new RerankResult(0, 0.1D));
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(
                knowledgeBaseMapper,
                mock(KnowledgeDocumentMapper.class),
                mock(KnowledgeTaskMapper.class),
                repository,
                new ObjectMapper(),
                mock(ThreadPoolExecutor.class),
                mock(EmbeddingService.class),
                mock(ModelConfigService.class),
                mock(RagRetrievalTraceMapper.class));
        service.setRerankService(rerankService);
        KnowledgeSearchReq req = new KnowledgeSearchReq();
        req.setKnowledgeBaseIds(List.of(1L));
        req.setQueryEmbedding(List.of(0.1, 0.2, 0.3));
        req.setQueryText("报销流程");

        List<KnowledgeSearchResp> results = service.searchSimilar(req);

        assertThat(results).extracting(KnowledgeSearchResp::getId).containsExactly(7L, 8L);
        assertThat(results.get(0).getRerankScore()).isEqualTo(0.99D);

        rerankService.fail = true;
        List<KnowledgeSearchResp> fallback = service.searchSimilar(req);

        assertThat(fallback).extracting(KnowledgeSearchResp::getId).containsExactly(8L, 7L);
    }

    private static class FakeKnowledgeVectorRepository implements KnowledgeVectorRepository {
        private KnowledgeChunk savedChunk;
        private List<Double> savedEmbedding;
        private List<KnowledgeSearchHit> hits = new ArrayList<>();
        private List<KnowledgeSearchHit> keywordHits = new ArrayList<>();
        private List<List<KnowledgeChunk>> savedBatches = new ArrayList<>();
        private List<Long> deletedDocumentIds = new ArrayList<>();
        private List<Long> deletedVersionedDocumentIds = new ArrayList<>();
        private List<Long> lastKnowledgeBaseIds = new ArrayList<>();
        private KnowledgeSearchFilter lastFilter;
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
            this.lastKnowledgeBaseIds = knowledgeBaseIds;
            return hits;
        }

        @Override
        public List<KnowledgeSearchHit> search(List<Long> knowledgeBaseIds, List<Double> queryEmbedding,
                                               int topK, KnowledgeSearchFilter filter) {
            this.lastTopK = topK;
            this.lastKnowledgeBaseIds = knowledgeBaseIds;
            this.lastFilter = filter;
            return hits;
        }

        @Override
        public List<KnowledgeSearchHit> keywordSearch(List<Long> knowledgeBaseIds, String queryText,
                                                      int topK, KnowledgeSearchFilter filter) {
            this.lastTopK = topK;
            this.lastKnowledgeBaseIds = knowledgeBaseIds;
            this.lastFilter = filter;
            return keywordHits;
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

        @Override
        public void deleteByDocumentIdAndIndexVersion(Long documentId, Long indexVersion) {
            deletedVersionedDocumentIds.add(documentId);
        }
    }

    private static class FakeRerankService implements RerankService {
        private List<RerankResult> results = List.of();
        private boolean fail;

        @Override
        public List<RerankResult> rerank(RerankRequest req) {
            if (fail) {
                throw new RuntimeException("rerank timeout");
            }
            return results;
        }
    }

    private static class FakeEmbeddingService implements EmbeddingService {
        private final List<Integer> batchSizes = new ArrayList<>();
        private final List<Integer> batchCharLengths = new ArrayList<>();

        @Override
        public List<List<Double>> embed(Long modelConfigId, List<String> inputs) {
            batchSizes.add(inputs.size());
            batchCharLengths.add(inputs.stream().mapToInt(String::length).sum());
            return inputs.stream().map(input -> List.of(0.1, 0.2, 0.3)).toList();
        }
    }

    private static void setIntField(Object target, String fieldName, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static KnowledgeBasePo knowledgeBase(Long id, Long projectId, Long embeddingModelConfigId) {
        KnowledgeBasePo po = new KnowledgeBasePo();
        po.setId(id);
        po.setWorkspaceId(1L);
        po.setProjectId(projectId);
        po.setVisibility("PROJECT");
        po.setShareScope("PROJECT");
        po.setEmbeddingModelConfigId(embeddingModelConfigId);
        po.setEnabled(1);
        po.setTopK(5);
        po.setCandidateTopK(20);
        po.setScoreThreshold(0.65D);
        po.setRetrievalMode("VECTOR");
        po.setHybridAlpha(0.7D);
        po.setActiveIndexVersion(1L);
        po.setIndexStatus("READY");
        return po;
    }

    private static KnowledgeDocumentPo document(Long id, Long knowledgeBaseId, String status) {
        KnowledgeDocumentPo document = new KnowledgeDocumentPo();
        document.setId(id);
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setName("doc-" + id + ".txt");
        document.setFileType("txt");
        document.setParseStatus(status);
        document.setChunkCount(2);
        document.setRetryable(1);
        document.setTagsJson("[]");
        document.setPermissionScope("PROJECT");
        return document;
    }

    private static KnowledgeSearchHit hit(Long id, Long knowledgeBaseId, String content, double score) {
        KnowledgeSearchHit hit = new KnowledgeSearchHit();
        hit.setId(id);
        hit.setKnowledgeBaseId(knowledgeBaseId);
        hit.setDocumentId("doc-" + id);
        hit.setChunkIndex(0);
        hit.setContent(content);
        hit.setMetadataJson("{}");
        hit.setScore(score);
        return hit;
    }
}
