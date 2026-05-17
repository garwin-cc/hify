package com.hify.knowledge.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.task.TaskQueue;
import com.hify.common.task.TaskRejectedException;
import com.hify.common.task.TaskRequest;
import com.hify.common.task.TaskType;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.knowledge.api.*;
import com.hify.knowledge.infra.KnowledgeBaseMapper;
import com.hify.knowledge.infra.KnowledgeDocumentMapper;
import com.hify.knowledge.infra.KnowledgeTaskMapper;
import com.hify.knowledge.infra.RagRetrievalTraceMapper;
import com.hify.model.api.EmbeddingService;
import com.hify.model.api.ModelConfigResp;
import com.hify.model.api.ModelConfigService;
import com.hify.model.api.RerankRequest;
import com.hify.model.api.RerankResult;
import com.hify.model.api.RerankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.Comparator;
import java.util.HashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final int DEFAULT_TOP_K = 5;
    private static final int DEFAULT_CANDIDATE_TOP_K = 20;
    private static final double DEFAULT_SCORE_THRESHOLD = 0.65D;
    private static final int DEFAULT_MAX_CONTEXT_TOKENS = 3000;
    private static final long DEFAULT_MAX_FILE_SIZE = 200L * 1024 * 1024;
    private static final int CHUNK_SIZE = 512;
    private static final int CHUNK_OVERLAP = 64;
    private static final int MAX_DYNAMIC_CHUNK_SIZE = 32_768;
    private static final int DEFAULT_MAX_EMBEDDING_CHUNK_CHARS = 4_000;
    private static final int DEFAULT_MAX_EMBEDDING_BATCH_CHARS = 24_000;
    private static final int EXTRACTED_TEXT_EXPANSION_SAFETY_FACTOR = 4;
    private static final int DEFAULT_EMBEDDING_BATCH_SIZE = 32;
    private static final int DEFAULT_OLLAMA_EMBEDDING_BATCH_SIZE = 64;
    private static final int DEFAULT_MAX_CHUNKS_PER_DOCUMENT = 50_000;
    private static final int DEFAULT_MAX_SPLIT_STEPS = 100_000;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;
    private static final Set<String> ALLOWED_TYPES = Set.of("txt", "md", "pdf", "csv");
    private static final List<Charset> TEXT_CHARSET_CANDIDATES = List.of(
            StandardCharsets.UTF_8,
            Charset.forName("GB18030"),
            StandardCharsets.ISO_8859_1);
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CANCELED = "CANCELED";
    private static final String STAGE_SAVED = "SAVED";
    private static final String STAGE_EXTRACTING = "EXTRACTING";
    private static final String STAGE_CHUNKING = "CHUNKING";
    private static final String STAGE_EMBEDDING = "EMBEDDING";
    private static final String STAGE_SAVING = "SAVING";
    private static final String ERROR_FILE_EMPTY = "FILE_EMPTY";
    private static final String ERROR_EMBEDDING_MODEL_MISSING = "EMBEDDING_MODEL_MISSING";
    private static final String ERROR_EMBEDDING_CALL_FAILED = "EMBEDDING_CALL_FAILED";
    private static final String ERROR_CHUNK_LIMIT_EXCEEDED = "CHUNK_LIMIT_EXCEEDED";
    private static final String ERROR_VECTOR_SAVE_FAILED = "VECTOR_SAVE_FAILED";
    private static final String ERROR_QUEUE_FULL = "QUEUE_FULL";
    private static final String ERROR_CANCELED_BY_USER = "CANCELED_BY_USER";
    private static final String ERROR_INTERNAL = "INTERNAL_ERROR";
    private static final String TASK_STATUS_PENDING = "PENDING";
    private static final String TASK_STATUS_RUNNING = "RUNNING";
    private static final String TASK_STATUS_DONE = "DONE";
    private static final String TASK_STATUS_FAILED = "FAILED";
    private static final String TASK_STATUS_CANCELED = "CANCELED";
    private static final String TASK_TYPE_DOCUMENT_PROCESS = "DOCUMENT_PROCESS";
    private static final String TASK_TYPE_REBUILD_INDEX = "REBUILD_INDEX";
    private static final String TASK_TYPE_REVECTORIZE = "REVECTORIZE";
    private static final String TARGET_DOCUMENT = "DOCUMENT";
    private static final String TARGET_KNOWLEDGE_BASE = "KNOWLEDGE_BASE";
    private static final String VISIBILITY_PROJECT = "PROJECT";
    private static final String VISIBILITY_WORKSPACE = "WORKSPACE";
    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final double DEFAULT_HYBRID_ALPHA = 0.7D;
    private static final String INDEX_STATUS_READY = "READY";
    private static final String INDEX_STATUS_REBUILDING = "REBUILDING";
    private static final String INDEX_STATUS_FAILED = "FAILED";

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeTaskMapper taskMapper;
    private final KnowledgeVectorRepository vectorRepository;
    private final ObjectMapper objectMapper;
    private final @Qualifier("knowledgeExecutor") ThreadPoolExecutor asyncExecutor;
    private final EmbeddingService embeddingService;
    private final ModelConfigService modelConfigService;
    private final RagRetrievalTraceMapper traceMapper;
    private TaskQueue knowledgeTaskQueue;
    private RerankService rerankService;

    @Autowired(required = false)
    public void setKnowledgeTaskQueue(@Qualifier("knowledgeTaskQueue") TaskQueue knowledgeTaskQueue) {
        this.knowledgeTaskQueue = knowledgeTaskQueue;
    }

    @Autowired(required = false)
    public void setRerankService(RerankService rerankService) {
        this.rerankService = rerankService;
    }

    @Value("${hify.knowledge.max-file-size-bytes:" + DEFAULT_MAX_FILE_SIZE + "}")
    private long maxFileSizeBytes = DEFAULT_MAX_FILE_SIZE;

    @Value("${hify.knowledge.embedding-batch-size:" + DEFAULT_EMBEDDING_BATCH_SIZE + "}")
    private int embeddingBatchSize = DEFAULT_EMBEDDING_BATCH_SIZE;

    @Value("${hify.knowledge.ollama-embedding-batch-size:" + DEFAULT_OLLAMA_EMBEDDING_BATCH_SIZE + "}")
    private int ollamaEmbeddingBatchSize = DEFAULT_OLLAMA_EMBEDDING_BATCH_SIZE;

    @Value("${hify.knowledge.max-chunks-per-document:" + DEFAULT_MAX_CHUNKS_PER_DOCUMENT + "}")
    private int maxChunksPerDocument = DEFAULT_MAX_CHUNKS_PER_DOCUMENT;

    @Value("${hify.knowledge.max-split-steps:" + DEFAULT_MAX_SPLIT_STEPS + "}")
    private int maxSplitSteps = DEFAULT_MAX_SPLIT_STEPS;

    @Value("${hify.knowledge.max-embedding-chunk-chars:" + DEFAULT_MAX_EMBEDDING_CHUNK_CHARS + "}")
    private int maxEmbeddingChunkChars = DEFAULT_MAX_EMBEDDING_CHUNK_CHARS;

    @Value("${hify.knowledge.max-embedding-batch-chars:" + DEFAULT_MAX_EMBEDDING_BATCH_CHARS + "}")
    private int maxEmbeddingBatchChars = DEFAULT_MAX_EMBEDDING_BATCH_CHARS;

    @Override
    @Transactional
    public KnowledgeBaseResp createKnowledgeBase(CreateKnowledgeBaseReq req) {
        requireEnabledEmbeddingModel(req.getEmbeddingModelConfigId());
        KnowledgeBasePo po = new KnowledgeBasePo();
        po.setWorkspaceId(req.getWorkspaceId() == null ? 1L : req.getWorkspaceId());
        po.setProjectId(req.getProjectId() == null ? 1L : req.getProjectId());
        po.setVisibility(normalizeVisibility(req.getVisibility()));
        po.setShareScope(normalizeVisibility(req.getShareScope()));
        po.setName(req.getName());
        po.setDescription(req.getDescription() == null ? "" : req.getDescription());
        po.setEmbeddingModelConfigId(req.getEmbeddingModelConfigId());
        po.setEnabled(1);
        po.setDocumentCount(0);
        po.setChunkCount(0);
        setDefaultRetrievalConfig(po);
        knowledgeBaseMapper.insert(po);
        log.info("created knowledge base id={} name={}", po.getId(), po.getName());
        return toKnowledgeBaseResp(po);
    }

    @Override
    @Transactional
    public KnowledgeBaseResp updateRetrievalConfig(Long id, UpdateKnowledgeRetrievalConfigReq req) {
        KnowledgeBasePo po = findKnowledgeBaseOrThrow(id);
        applyRetrievalConfig(po, req);
        knowledgeBaseMapper.updateById(po);
        log.info("updated knowledge retrieval config id={}", id);
        return toKnowledgeBaseResp(po);
    }

    @Override
    public PageResult<KnowledgeBaseResp> listKnowledgeBases(KnowledgeBaseQuery query) {
        int pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        int pageSize = query.getSize() <= 0 ? 20 : query.getSize();
        Page<KnowledgeBasePo> pageParam = PageHelper.toPage(pageNo, pageSize);
        LambdaQueryWrapper<KnowledgeBasePo> wrapper = Wrappers.lambdaQuery(KnowledgeBasePo.class)
                .like(StringUtils.hasText(query.getName()), KnowledgeBasePo::getName, query.getName())
                .orderByDesc(KnowledgeBasePo::getCreatedAt);
        return PageHelper.toPageResult(knowledgeBaseMapper.selectPage(pageParam, wrapper),
                this::toKnowledgeBaseResp);
    }

    @Override
    public KnowledgeBaseResp getKnowledgeBase(Long id) {
        return toKnowledgeBaseResp(findKnowledgeBaseOrThrow(id));
    }

    @Override
    @Transactional
    public KnowledgeBaseResp updateKnowledgeBase(Long id, UpdateKnowledgeBaseReq req) {
        KnowledgeBasePo po = findKnowledgeBaseOrThrow(id);
        if (req.getName() != null) {
            if (!StringUtils.hasText(req.getName())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "知识库名称不能为空");
            }
            po.setName(req.getName());
        }
        if (req.getDescription() != null) {
            po.setDescription(req.getDescription());
        }
        if (req.getWorkspaceId() != null) {
            po.setWorkspaceId(req.getWorkspaceId());
        }
        if (req.getProjectId() != null) {
            po.setProjectId(req.getProjectId());
        }
        if (req.getVisibility() != null) {
            po.setVisibility(normalizeVisibility(req.getVisibility()));
        }
        if (req.getShareScope() != null) {
            po.setShareScope(normalizeVisibility(req.getShareScope()));
        }
        if (req.getEmbeddingModelConfigId() != null
                && !req.getEmbeddingModelConfigId().equals(po.getEmbeddingModelConfigId())) {
            if (po.getDocumentCount() != null && po.getDocumentCount() > 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "已有文档的知识库不允许切换向量模型");
            }
            requireEnabledEmbeddingModel(req.getEmbeddingModelConfigId());
            po.setEmbeddingModelConfigId(req.getEmbeddingModelConfigId());
        }
        if (req.getEnabled() != null) {
            po.setEnabled(req.getEnabled() == 1 ? 1 : 0);
        }
        knowledgeBaseMapper.updateById(po);
        log.info("updated knowledge base id={}", id);
        return toKnowledgeBaseResp(po);
    }

    @Override
    @Transactional
    public void deleteKnowledgeBase(Long id) {
        findKnowledgeBaseOrThrow(id);
        knowledgeBaseMapper.deleteById(id);
        documentMapper.delete(Wrappers.lambdaQuery(KnowledgeDocumentPo.class)
                .eq(KnowledgeDocumentPo::getKnowledgeBaseId, id));
        vectorRepository.deleteByKnowledgeBaseId(id);
        log.info("deleted knowledge base id={}", id);
    }

    @Override
    @Transactional
    public Long uploadDocument(Long knowledgeBaseId, MultipartFile file) {
        KnowledgeBasePo knowledgeBase = findKnowledgeBaseOrThrow(knowledgeBaseId);
        validateUploadFile(file);

        String originalFilename = file.getOriginalFilename();
        String fileType = getFileType(originalFilename);
        Path filePath = saveUploadFile(knowledgeBaseId, file, fileType);

        KnowledgeDocumentPo document = new KnowledgeDocumentPo();
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setName(originalFilename);
        document.setFileKey(filePath.toString());
        document.setFileType(fileType);
        document.setFileSize(file.getSize());
        document.setDepartment("");
        document.setDocumentType(fileType);
        document.setTagsJson("[]");
        document.setPermissionScope(effectiveShareScope(knowledgeBase));
        document.setParseStatus("PENDING");
        document.setProcessStage("SAVED");
        document.setProcessProgress(0);
        document.setProcessedChunkCount(0);
        document.setChunkCount(0);
        document.setErrorMessage("");
        document.setErrorCode("");
        document.setFailedStage("");
        document.setRetryable(0);
        document.setCancelRequested(0);
        document.setRetryCount(0);
        document.setTaskStatus(TASK_STATUS_PENDING);
        document.setProgressMessage("文档已保存，等待处理");
        document.setLastProcessedChunkIndex(0);
        documentMapper.insert(document);
        KnowledgeTaskPo task = createTask(knowledgeBaseId, document.getId(), TASK_TYPE_DOCUMENT_PROCESS,
                TARGET_DOCUMENT, document.getId(), "UPLOAD");
        document.setProcessingTaskId(task.getId());
        documentMapper.updateById(document);

        knowledgeBaseMapper.update(null, Wrappers.lambdaUpdate(KnowledgeBasePo.class)
                .eq(KnowledgeBasePo::getId, knowledgeBaseId)
                .setSql("document_count = document_count + 1"));

        submitAfterCommit(task.getId(), document.getId());
        log.info("uploaded knowledge document id={} kbId={} name={}",
                document.getId(), knowledgeBaseId, originalFilename);
        return document.getId();
    }

    @Override
    public PageResult<KnowledgeDocumentResp> listDocuments(Long knowledgeBaseId, KnowledgeDocumentQuery query) {
        findKnowledgeBaseOrThrow(knowledgeBaseId);
        int pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        int pageSize = query.getSize() <= 0 ? 20 : query.getSize();
        Page<KnowledgeDocumentPo> pageParam = PageHelper.toPage(pageNo, pageSize);
        return PageHelper.toPageResult(documentMapper.selectPage(pageParam,
                Wrappers.lambdaQuery(KnowledgeDocumentPo.class)
                        .eq(KnowledgeDocumentPo::getKnowledgeBaseId, knowledgeBaseId)
                        .orderByDesc(KnowledgeDocumentPo::getCreatedAt)), this::toDocumentResp);
    }

    @Override
    public KnowledgeDocumentResp getDocument(Long id) {
        return toDocumentResp(findDocumentOrThrow(id));
    }

    @Override
    public List<KnowledgeChunkResp> listDocumentChunks(Long documentId) {
        findDocumentOrThrow(documentId);
        return vectorRepository.listByDocumentId(documentId)
                .stream()
                .map(this::toChunkResp)
                .toList();
    }

    @Override
    @Transactional
    public void deleteDocument(Long id) {
        KnowledgeDocumentPo document = findDocumentOrThrow(id);
        documentMapper.deleteById(id);
        vectorRepository.deleteByDocumentId(id);
        knowledgeBaseMapper.update(null, Wrappers.lambdaUpdate(KnowledgeBasePo.class)
                .eq(KnowledgeBasePo::getId, document.getKnowledgeBaseId())
                .setSql("document_count = GREATEST(document_count - 1, 0)")
                .setSql("chunk_count = GREATEST(chunk_count - " + safeChunkCount(document.getChunkCount()) + ", 0)"));
        log.info("deleted knowledge document id={} kbId={}", id, document.getKnowledgeBaseId());
    }

    @Override
    @Transactional
    public void retryDocument(Long id) {
        KnowledgeDocumentPo document = findDocumentOrThrow(id);
        if (!STATUS_FAILED.equals(document.getParseStatus()) && !STATUS_CANCELED.equals(document.getParseStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "只有失败或已取消的文档可以重试");
        }
        if (Integer.valueOf(0).equals(document.getRetryable()) && !STATUS_CANCELED.equals(document.getParseStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "当前失败类型不支持重试");
        }
        vectorRepository.deleteByDocumentId(id);
        int oldChunkCount = safeChunkCount(document.getChunkCount());
        if (oldChunkCount > 0) {
            knowledgeBaseMapper.update(null, Wrappers.lambdaUpdate(KnowledgeBasePo.class)
                    .eq(KnowledgeBasePo::getId, document.getKnowledgeBaseId())
                    .setSql("chunk_count = GREATEST(chunk_count - " + oldChunkCount + ", 0)"));
        }
        document.setParseStatus(STATUS_PENDING);
        document.setProcessStage(STAGE_SAVED);
        document.setProcessProgress(0);
        document.setProcessedChunkCount(0);
        document.setChunkCount(0);
        document.setErrorMessage("");
        document.setErrorCode("");
        document.setFailedStage("");
        document.setRetryable(0);
        document.setCancelRequested(0);
        document.setStartedAt(null);
        document.setFinishedAt(null);
        document.setRetryCount(safeChunkCount(document.getRetryCount()) + 1);
        document.setLastRetryAt(LocalDateTime.now());
        document.setTaskStatus(TASK_STATUS_PENDING);
        document.setProgressMessage("文档已重试，等待处理");
        document.setLastProcessedChunkIndex(0);
        KnowledgeTaskPo task = createTask(document.getKnowledgeBaseId(), id, TASK_TYPE_DOCUMENT_PROCESS,
                TARGET_DOCUMENT, id, "RETRY");
        document.setProcessingTaskId(task.getId());
        documentMapper.updateById(document);
        submitAfterCommit(task.getId(), id);
        log.info("retry knowledge document id={} kbId={}", id, document.getKnowledgeBaseId());
    }

    @Override
    @Transactional
    public void cancelDocument(Long id) {
        KnowledgeDocumentPo document = findDocumentOrThrow(id);
        if (STATUS_DONE.equals(document.getParseStatus())
                || STATUS_FAILED.equals(document.getParseStatus())
                || STATUS_CANCELED.equals(document.getParseStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "当前文档状态不支持取消");
        }
        if (STATUS_PENDING.equals(document.getParseStatus())) {
            markDocumentCanceled(id, "文档处理已取消");
            return;
        }
        documentMapper.update(null, Wrappers.lambdaUpdate(KnowledgeDocumentPo.class)
                .eq(KnowledgeDocumentPo::getId, id)
                .eq(KnowledgeDocumentPo::getParseStatus, STATUS_PROCESSING)
                .set(KnowledgeDocumentPo::getCancelRequested, 1));
        if (document.getProcessingTaskId() != null) {
            taskMapper.update(null, Wrappers.lambdaUpdate(KnowledgeTaskPo.class)
                    .eq(KnowledgeTaskPo::getId, document.getProcessingTaskId())
                    .set(KnowledgeTaskPo::getCancelRequested, 1)
                    .set(KnowledgeTaskPo::getProgressMessage, "用户请求取消"));
        }
        log.info("requested cancel knowledge document id={}", id);
    }

    @Override
    @Transactional
    public Long revectorizeDocument(Long id, KnowledgeRebuildReq req) {
        KnowledgeDocumentPo document = findDocumentOrThrow(id);
        KnowledgeBasePo knowledgeBase = findKnowledgeBaseOrThrow(document.getKnowledgeBaseId());
        applyRebuildOptions(knowledgeBase, req);
        resetDocumentForProcessing(document, "文档重向量化已排队");
        vectorRepository.deleteByDocumentId(id);
        KnowledgeTaskPo task = createTask(document.getKnowledgeBaseId(), id, TASK_TYPE_REVECTORIZE,
                TARGET_DOCUMENT, id, normalizeReason(req, "DOCUMENT_UPDATED"));
        document.setProcessingTaskId(task.getId());
        documentMapper.updateById(document);
        submitAfterCommit(task.getId(), id);
        log.info("revectorize knowledge document id={} taskId={}", id, task.getId());
        return task.getId();
    }

    @Override
    @Transactional
    public Long rebuildKnowledgeBaseIndex(Long id, KnowledgeRebuildReq req) {
        KnowledgeBasePo knowledgeBase = findKnowledgeBaseOrThrow(id);
        applyRebuildOptions(knowledgeBase, req);
        Long nextIndexVersion = effectiveActiveIndexVersion(knowledgeBase) + 1;
        knowledgeBase.setBuildingIndexVersion(nextIndexVersion);
        knowledgeBase.setIndexStatus(INDEX_STATUS_REBUILDING);
        knowledgeBaseMapper.updateById(knowledgeBase);
        KnowledgeTaskPo rootTask = createTask(id, null, TASK_TYPE_REBUILD_INDEX, TARGET_KNOWLEDGE_BASE,
                id, normalizeReason(req, "MANUAL_REBUILD"));
        List<KnowledgeDocumentPo> documents = documentMapper.selectList(Wrappers.lambdaQuery(KnowledgeDocumentPo.class)
                .eq(KnowledgeDocumentPo::getKnowledgeBaseId, id));
        knowledgeBaseMapper.update(null, Wrappers.lambdaUpdate(KnowledgeBasePo.class)
                .eq(KnowledgeBasePo::getId, id)
                .set(KnowledgeBasePo::getChunkCount, 0));
        for (KnowledgeDocumentPo document : documents) {
            resetDocumentForProcessing(document, "知识库重建索引已排队");
            vectorRepository.deleteByDocumentIdAndIndexVersion(document.getId(), nextIndexVersion);
            KnowledgeTaskPo documentTask = createTask(id, document.getId(), TASK_TYPE_DOCUMENT_PROCESS,
                    TARGET_DOCUMENT, document.getId(), rootTask.getReason());
            document.setProcessingTaskId(documentTask.getId());
            documentMapper.updateById(document);
            submitAfterCommit(documentTask.getId(), document.getId());
        }
        markTaskDone(rootTask.getId(), "已为 " + documents.size() + " 个文档创建重建任务");
        log.info("rebuild knowledge base id={} rootTaskId={} documentCount={}", id, rootTask.getId(), documents.size());
        return rootTask.getId();
    }

    @Override
    public List<KnowledgeTaskResp> listProcessingTasks(Long knowledgeBaseId, Long documentId) {
        return taskMapper.selectList(Wrappers.lambdaQuery(KnowledgeTaskPo.class)
                        .eq(knowledgeBaseId != null, KnowledgeTaskPo::getKnowledgeBaseId, knowledgeBaseId)
                        .eq(documentId != null, KnowledgeTaskPo::getDocumentId, documentId)
                        .orderByDesc(KnowledgeTaskPo::getCreatedAt)
                        .last("LIMIT 100"))
                .stream()
                .map(this::toTaskResp)
                .toList();
    }

    @Override
    public void recoverProcessingTasks() {
        List<KnowledgeTaskPo> tasks = taskMapper.selectList(Wrappers.lambdaQuery(KnowledgeTaskPo.class)
                .in(KnowledgeTaskPo::getStatus, List.of(TASK_STATUS_PENDING, TASK_STATUS_RUNNING))
                .eq(KnowledgeTaskPo::getTargetType, TARGET_DOCUMENT)
                .orderByAsc(KnowledgeTaskPo::getCreatedAt)
                .last("LIMIT 200"));
        for (KnowledgeTaskPo task : tasks) {
            if (task.getDocumentId() != null) {
                task.setStatus(TASK_STATUS_PENDING);
                task.setProgressMessage("服务恢复后重新排队");
                taskMapper.updateById(task);
                enqueueDocumentProcessing(task.getId(), task.getDocumentId());
            }
        }
        if (!tasks.isEmpty()) {
            log.warn("requeued knowledge processing tasks count={}", tasks.size());
        }
    }

    @Override
    public Long upsertChunk(KnowledgeChunkUpsertReq req) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setKnowledgeBaseId(req.getKnowledgeBaseId());
        chunk.setDocumentId(req.getDocumentId());
        chunk.setChunkIndex(req.getChunkIndex());
        chunk.setContent(req.getContent());
        chunk.setMetadataJson(toJson(req.getMetadata()));
        return vectorRepository.upsert(chunk, req.getEmbedding());
    }

    private KnowledgeBasePo findKnowledgeBaseOrThrow(Long id) {
        KnowledgeBasePo po = knowledgeBaseMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在: " + id);
        }
        return po;
    }

    private KnowledgeDocumentPo findDocumentOrThrow(Long id) {
        KnowledgeDocumentPo po = documentMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在: " + id);
        }
        return po;
    }

    private KnowledgeBaseResp toKnowledgeBaseResp(KnowledgeBasePo po) {
        KnowledgeBaseResp resp = new KnowledgeBaseResp();
        resp.setId(po.getId());
        resp.setWorkspaceId(po.getWorkspaceId());
        resp.setProjectId(po.getProjectId());
        resp.setVisibility(effectiveVisibility(po));
        resp.setShareScope(effectiveShareScope(po));
        resp.setName(po.getName());
        resp.setDescription(po.getDescription());
        resp.setEmbeddingModelConfigId(po.getEmbeddingModelConfigId());
        resp.setEnabled(po.getEnabled());
        resp.setDocumentCount(po.getDocumentCount());
        resp.setChunkCount(po.getChunkCount());
        resp.setRetrievalMode(effectiveRetrievalMode(po));
        resp.setHybridAlpha(effectiveHybridAlpha(po));
        resp.setTopK(effectiveTopK(po));
        resp.setCandidateTopK(effectiveCandidateTopK(po));
        resp.setScoreThreshold(effectiveScoreThreshold(po));
        resp.setChunkSize(po.getChunkSize() == null ? CHUNK_SIZE : po.getChunkSize());
        resp.setChunkOverlap(po.getChunkOverlap() == null ? CHUNK_OVERLAP : po.getChunkOverlap());
        resp.setMaxContextTokens(po.getMaxContextTokens() == null ? DEFAULT_MAX_CONTEXT_TOKENS : po.getMaxContextTokens());
        resp.setRerankEnabled(po.getRerankEnabled() == null ? 0 : po.getRerankEnabled());
        resp.setRerankModelConfigId(po.getRerankModelConfigId());
        resp.setRerankTopN(po.getRerankTopN() == null ? DEFAULT_CANDIDATE_TOP_K : po.getRerankTopN());
        resp.setMetadataFilterEnabled(po.getMetadataFilterEnabled() == null ? 0 : po.getMetadataFilterEnabled());
        resp.setDefaultMetadataFilterJson(po.getDefaultMetadataFilterJson());
        resp.setActiveIndexVersion(effectiveActiveIndexVersion(po));
        resp.setBuildingIndexVersion(po.getBuildingIndexVersion());
        resp.setIndexStatus(effectiveIndexStatus(po));
        resp.setCreatedAt(po.getCreatedAt());
        resp.setUpdatedAt(po.getUpdatedAt());
        return resp;
    }

    private void requireEnabledEmbeddingModel(Long modelConfigId) {
        if (modelConfigId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择向量模型");
        }
        ModelConfigResp modelConfig = modelConfigService.getById(modelConfigId);
        if (modelConfig == null
                || !Integer.valueOf(1).equals(modelConfig.getEnabled())
                || !"EMBEDDING".equals(modelConfig.getModelType())) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "向量模型不存在、未启用或类型不是 EMBEDDING: " + modelConfigId);
        }
    }

    private void requireEnabledRerankModel(Long modelConfigId) {
        if (modelConfigId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "启用 rerank 时必须配置 rerank 模型");
        }
        ModelConfigResp modelConfig = modelConfigService.getById(modelConfigId);
        if (modelConfig == null
                || !Integer.valueOf(1).equals(modelConfig.getEnabled())
                || !"RERANK".equals(modelConfig.getModelType())) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "Rerank 模型不存在、未启用或类型不是 RERANK: " + modelConfigId);
        }
    }

    private KnowledgeDocumentResp toDocumentResp(KnowledgeDocumentPo po) {
        KnowledgeDocumentResp resp = new KnowledgeDocumentResp();
        resp.setId(po.getId());
        resp.setKnowledgeBaseId(po.getKnowledgeBaseId());
        resp.setName(po.getName());
        resp.setFileType(po.getFileType());
        resp.setFileSize(po.getFileSize());
        resp.setDepartment(po.getDepartment());
        resp.setDocumentType(po.getDocumentType());
        resp.setTags(fromJsonList(po.getTagsJson(), String.class));
        resp.setPermissionScope(po.getPermissionScope());
        resp.setStatus(po.getParseStatus());
        resp.setProcessStage(po.getProcessStage());
        resp.setProcessProgress(po.getProcessProgress());
        resp.setProcessedChunkCount(po.getProcessedChunkCount());
        resp.setChunkCount(po.getChunkCount());
        resp.setErrorMessage(po.getErrorMessage());
        resp.setErrorCode(po.getErrorCode());
        resp.setFailedStage(po.getFailedStage());
        resp.setRetryable(po.getRetryable());
        resp.setCancelRequested(po.getCancelRequested());
        resp.setRetryCount(po.getRetryCount());
        resp.setProcessingTaskId(po.getProcessingTaskId());
        resp.setTaskStatus(po.getTaskStatus());
        resp.setProgressMessage(po.getProgressMessage());
        resp.setParseLatencyMs(po.getParseLatencyMs());
        resp.setChunkLatencyMs(po.getChunkLatencyMs());
        resp.setEmbeddingLatencyMs(po.getEmbeddingLatencyMs());
        resp.setVectorSaveLatencyMs(po.getVectorSaveLatencyMs());
        resp.setLastProcessedChunkIndex(po.getLastProcessedChunkIndex());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setUpdatedAt(po.getUpdatedAt());
        return resp;
    }

    private KnowledgeChunkResp toChunkResp(KnowledgeChunk chunk) {
        KnowledgeChunkResp resp = new KnowledgeChunkResp();
        resp.setId(chunk.getId());
        resp.setKnowledgeBaseId(chunk.getKnowledgeBaseId());
        resp.setDocumentId(Long.valueOf(chunk.getDocumentId()));
        resp.setChunkIndex(chunk.getChunkIndex());
        resp.setContent(chunk.getContent());
        resp.setMetadata(fromJson(chunk.getMetadataJson()));
        resp.setCreatedAt(chunk.getCreatedAt());
        return resp;
    }

    private KnowledgeTaskResp toTaskResp(KnowledgeTaskPo po) {
        KnowledgeTaskResp resp = new KnowledgeTaskResp();
        resp.setId(po.getId());
        resp.setKnowledgeBaseId(po.getKnowledgeBaseId());
        resp.setDocumentId(po.getDocumentId());
        resp.setTaskType(po.getTaskType());
        resp.setTargetType(po.getTargetType());
        resp.setTargetId(po.getTargetId());
        resp.setReason(po.getReason());
        resp.setStatus(po.getStatus());
        resp.setProcessStage(po.getProcessStage());
        resp.setProcessProgress(po.getProcessProgress());
        resp.setProgressMessage(po.getProgressMessage());
        resp.setAttempt(po.getAttempt());
        resp.setMaxAttempt(po.getMaxAttempt());
        resp.setLastProcessedChunkIndex(po.getLastProcessedChunkIndex());
        resp.setCancelRequested(po.getCancelRequested());
        resp.setErrorCode(po.getErrorCode());
        resp.setErrorMessage(po.getErrorMessage());
        resp.setStartedAt(po.getStartedAt());
        resp.setFinishedAt(po.getFinishedAt());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setUpdatedAt(po.getUpdatedAt());
        return resp;
    }

    @Override
    public List<KnowledgeSearchResp> searchSimilar(KnowledgeSearchReq req) {
        long startNanos = System.nanoTime();
        String traceId = UUID.randomUUID().toString();
        String status = "SUCCESS";
        String errorMessage = "";
        List<KnowledgeSearchResp> results = List.of();
        try {
            results = doSearchSimilar(req, traceId);
            return results;
        } catch (Exception e) {
            status = "FAILED";
            errorMessage = truncateErrorMessage(e.getMessage());
            throw e;
        } finally {
            if (Boolean.TRUE.equals(req.getIncludeTrace()) || StringUtils.hasText(req.getSourceType())) {
                saveRetrievalTrace(req, traceId, results, status, errorMessage, elapsedMillis(startNanos));
            }
        }
    }

    private List<KnowledgeSearchResp> doSearchSimilar(KnowledgeSearchReq req, String traceId) {
        List<Long> knowledgeBaseIds = req.getKnowledgeBaseIds();
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "知识库 ID 不能为空");
        }
        if ((req.getQueryEmbedding() == null || req.getQueryEmbedding().isEmpty())
                && !StringUtils.hasText(req.getQueryText())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "查询文本或查询向量不能为空");
        }

        boolean hasQueryEmbedding = req.getQueryEmbedding() != null && !req.getQueryEmbedding().isEmpty();
        Map<Long, KnowledgeBasePo> knowledgeBaseMap;
        try {
            knowledgeBaseMap = loadEnabledKnowledgeBases(knowledgeBaseIds);
        } catch (BizException e) {
            if (!hasQueryEmbedding || req.getProjectId() != null) {
                throw e;
            }
            knowledgeBaseMap = Map.of();
        }
        List<Long> searchableKnowledgeBaseIds = knowledgeBaseMap.isEmpty()
                ? knowledgeBaseIds
                : filterKnowledgeBaseIdsByProject(knowledgeBaseMap, req.getProjectId());
        if (searchableKnowledgeBaseIds.isEmpty()) {
            return List.of();
        }
        if (knowledgeBaseMap.isEmpty() && !hasQueryEmbedding) {
            return List.of();
        }

        RetrievalOptions options = knowledgeBaseMap.isEmpty()
                ? resolveOptions(req, null)
                : resolveOptions(req, knowledgeBaseMap.values().iterator().next());
        if (!knowledgeBaseMap.isEmpty()) {
            applyDefaultMetadataFilter(req, knowledgeBaseMap.values().iterator().next());
        }
        req.setTopK(options.topK());
        req.setCandidateTopK(options.candidateTopK());
        req.setScoreThreshold(options.scoreThreshold());
        req.setRetrievalMode(options.retrievalMode());
        KnowledgeSearchFilter filter = buildSearchFilter(req);
        List<KnowledgeSearchHit> hits = new ArrayList<>();
        if ("FULLTEXT".equals(options.retrievalMode())) {
            hits.addAll(vectorRepository.keywordSearch(searchableKnowledgeBaseIds, req.getQueryText(),
                    options.candidateTopK(), filter));
        } else if (hasQueryEmbedding) {
            hits.addAll(vectorRepository.search(searchableKnowledgeBaseIds, req.getQueryEmbedding(),
                    options.candidateTopK(), filter));
        } else {
            Map<Long, List<Long>> grouped = knowledgeBaseMap.values().stream()
                    .filter(kb -> searchableKnowledgeBaseIds.contains(kb.getId()))
                    .filter(kb -> kb.getEmbeddingModelConfigId() != null)
                    .collect(Collectors.groupingBy(KnowledgeBasePo::getEmbeddingModelConfigId,
                            Collectors.mapping(KnowledgeBasePo::getId, Collectors.toList())));
            for (Map.Entry<Long, List<Long>> entry : grouped.entrySet()) {
                List<Double> embedding = embeddingService.embed(entry.getKey(), List.of(req.getQueryText())).get(0);
                hits.addAll(vectorRepository.search(entry.getValue(), embedding, options.candidateTopK(), filter));
            }
        }
        if ("HYBRID".equalsIgnoreCase(options.retrievalMode()) && StringUtils.hasText(req.getQueryText())) {
            hits = mergeHybridHits(hits, vectorRepository.keywordSearch(searchableKnowledgeBaseIds, req.getQueryText(),
                    options.candidateTopK(), filter), options.hybridAlpha());
        } else {
            hits.forEach(hit -> {
                if ("FULLTEXT".equals(options.retrievalMode())) {
                    if (hit.getKeywordScore() == null) {
                        hit.setKeywordScore(hit.getScore());
                    }
                } else if (hit.getVectorScore() == null) {
                    hit.setVectorScore(hit.getScore());
                }
            });
        }
        hits = applyRerankIfEnabled(hits, req, options, knowledgeBaseMap);

        List<KnowledgeSearchResp> responses = hits.stream()
                .filter(hit -> hit.getScore() != null && hit.getScore() >= options.scoreThreshold())
                .sorted(Comparator.comparing(KnowledgeSearchHit::getScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(options.topK())
                .map(this::toResp)
                .toList();
        for (int i = 0; i < responses.size(); i++) {
            KnowledgeSearchResp resp = responses.get(i);
            resp.setTraceId(traceId);
            resp.setRank(i + 1);
            resp.setRetrievalMode(options.retrievalMode());
        }
        return responses;
    }

    private KnowledgeSearchResp toResp(KnowledgeSearchHit hit) {
        KnowledgeSearchResp resp = new KnowledgeSearchResp();
        resp.setId(hit.getId());
        resp.setKnowledgeBaseId(hit.getKnowledgeBaseId());
        resp.setDocumentId(hit.getDocumentId());
        resp.setChunkIndex(hit.getChunkIndex());
        resp.setContent(hit.getContent());
        resp.setScore(hit.getScore());
        resp.setFinalScore(hit.getScore());
        resp.setVectorScore(hit.getVectorScore() == null ? hit.getScore() : hit.getVectorScore());
        resp.setKeywordScore(hit.getKeywordScore());
        resp.setRerankScore(hit.getRerankScore());
        Map<String, Object> metadata = fromJson(hit.getMetadataJson());
        Object documentName = metadata.get("documentName");
        resp.setDocumentName(documentName == null ? null : String.valueOf(documentName));
        resp.setMetadata(metadata);
        resp.setCreatedAt(hit.getCreatedAt());
        return resp;
    }

    @Override
    public RagRetrievalTraceResp getRetrievalTrace(String traceId) {
        RagRetrievalTracePo po = traceMapper.selectOne(Wrappers.lambdaQuery(RagRetrievalTracePo.class)
                .eq(RagRetrievalTracePo::getTraceId, traceId));
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "RAG trace 不存在: " + traceId);
        }
        RagRetrievalTraceResp resp = new RagRetrievalTraceResp();
        resp.setId(po.getId());
        resp.setTraceId(po.getTraceId());
        resp.setSourceType(po.getSourceType());
        resp.setSourceId(po.getSourceId());
        resp.setAgentId(po.getAgentId());
        resp.setQueryText(po.getQueryText());
        resp.setKnowledgeBaseIds(fromJsonList(po.getKnowledgeBaseIdsJson(), Long.class));
        resp.setRetrievalMode(po.getRetrievalMode());
        resp.setTopK(po.getTopK());
        resp.setScoreThreshold(po.getScoreThreshold());
        resp.setHitCount(po.getHitCount());
        resp.setSelectedChunkIds(fromJsonList(po.getSelectedChunkIdsJson(), Long.class));
        resp.setLatencyMs(po.getLatencyMs());
        resp.setStatus(po.getStatus());
        resp.setErrorMessage(po.getErrorMessage());
        resp.setCreatedAt(po.getCreatedAt());
        return resp;
    }

    private Map<Long, KnowledgeBasePo> loadEnabledKnowledgeBases(List<Long> knowledgeBaseIds) {
        Map<Long, KnowledgeBasePo> result = new HashMap<>();
        for (Long knowledgeBaseId : knowledgeBaseIds) {
            KnowledgeBasePo po = findKnowledgeBaseOrThrow(knowledgeBaseId);
            if (Integer.valueOf(1).equals(po.getEnabled())) {
                result.put(po.getId(), po);
            }
        }
        return result;
    }

    private List<Long> filterKnowledgeBaseIdsByProject(Map<Long, KnowledgeBasePo> knowledgeBaseMap, Long projectId) {
        if (knowledgeBaseMap == null || knowledgeBaseMap.isEmpty()) {
            return List.of();
        }
        if (projectId == null) {
            return new ArrayList<>(knowledgeBaseMap.keySet());
        }
        return knowledgeBaseMap.values().stream()
                .filter(kb -> canProjectSearchKnowledgeBase(kb, projectId))
                .map(KnowledgeBasePo::getId)
                .toList();
    }

    private boolean canProjectSearchKnowledgeBase(KnowledgeBasePo knowledgeBase, Long projectId) {
        if (knowledgeBase.getProjectId() == null || knowledgeBase.getProjectId().equals(projectId)) {
            return true;
        }
        String visibility = effectiveVisibility(knowledgeBase);
        return VISIBILITY_WORKSPACE.equals(visibility) || VISIBILITY_PUBLIC.equals(visibility);
    }

    private KnowledgeSearchFilter buildSearchFilter(KnowledgeSearchReq req) {
        return KnowledgeSearchFilter.builder()
                .department(blankToNull(req.getDepartment()))
                .documentType(blankToNull(req.getDocumentType()))
                .tags(req.getTags() == null ? List.of() : req.getTags().stream()
                        .filter(StringUtils::hasText)
                        .toList())
                .createdAtStart(req.getCreatedAtStart())
                .createdAtEnd(req.getCreatedAtEnd())
                .projectId(req.getProjectId())
                .permissionScope(blankToNull(req.getPermissionScope()))
                .build();
    }

    @SuppressWarnings("unchecked")
    private void applyDefaultMetadataFilter(KnowledgeSearchReq req, KnowledgeBasePo knowledgeBase) {
        if (knowledgeBase == null || !Integer.valueOf(1).equals(knowledgeBase.getMetadataFilterEnabled())) {
            return;
        }
        Map<String, Object> defaults = fromJson(knowledgeBase.getDefaultMetadataFilterJson());
        if (!StringUtils.hasText(req.getDepartment()) && defaults.get("department") instanceof String department) {
            req.setDepartment(department);
        }
        if (!StringUtils.hasText(req.getDocumentType()) && defaults.get("documentType") instanceof String documentType) {
            req.setDocumentType(documentType);
        }
        if (!StringUtils.hasText(req.getPermissionScope()) && defaults.get("permissionScope") instanceof String permissionScope) {
            req.setPermissionScope(permissionScope);
        }
        if ((req.getTags() == null || req.getTags().isEmpty()) && defaults.get("tags") instanceof List<?> tags) {
            req.setTags(tags.stream().map(String::valueOf).filter(StringUtils::hasText).toList());
        }
    }

    private List<KnowledgeSearchHit> mergeHybridHits(List<KnowledgeSearchHit> vectorHits,
                                                     List<KnowledgeSearchHit> keywordHits,
                                                     double hybridAlpha) {
        Map<Long, KnowledgeSearchHit> merged = new HashMap<>();
        for (KnowledgeSearchHit hit : vectorHits == null ? List.<KnowledgeSearchHit>of() : vectorHits) {
            KnowledgeSearchHit copy = copyHit(hit);
            copy.setVectorScore(hit.getVectorScore() == null ? hit.getScore() : hit.getVectorScore());
            copy.setScore(scoreHybrid(copy.getVectorScore(), null, hybridAlpha));
            merged.put(copy.getId(), copy);
        }
        for (KnowledgeSearchHit hit : keywordHits == null ? List.<KnowledgeSearchHit>of() : keywordHits) {
            KnowledgeSearchHit current = merged.get(hit.getId());
            if (current == null) {
                KnowledgeSearchHit copy = copyHit(hit);
                copy.setKeywordScore(hit.getKeywordScore() == null ? hit.getScore() : hit.getKeywordScore());
                copy.setScore(scoreHybrid(null, copy.getKeywordScore(), hybridAlpha));
                merged.put(copy.getId(), copy);
            } else {
                current.setKeywordScore(hit.getKeywordScore() == null ? hit.getScore() : hit.getKeywordScore());
                current.setScore(scoreHybrid(current.getVectorScore(), current.getKeywordScore(), hybridAlpha));
            }
        }
        return new ArrayList<>(merged.values());
    }

    private List<KnowledgeSearchHit> applyRerankIfEnabled(List<KnowledgeSearchHit> hits, KnowledgeSearchReq req,
                                                          RetrievalOptions options,
                                                          Map<Long, KnowledgeBasePo> knowledgeBaseMap) {
        if (hits == null || hits.isEmpty() || rerankService == null || !StringUtils.hasText(req.getQueryText())) {
            return hits;
        }
        KnowledgeBasePo knowledgeBase = knowledgeBaseMap == null || knowledgeBaseMap.isEmpty()
                ? null
                : knowledgeBaseMap.values().iterator().next();
        if (knowledgeBase == null || !Integer.valueOf(1).equals(knowledgeBase.getRerankEnabled())
                || knowledgeBase.getRerankModelConfigId() == null) {
            return hits;
        }
        try {
            List<KnowledgeSearchHit> candidates = hits.stream()
                    .sorted(Comparator.comparing(KnowledgeSearchHit::getScore,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(Math.max(options.topK(), knowledgeBase.getRerankTopN() == null
                            ? options.candidateTopK()
                            : knowledgeBase.getRerankTopN()))
                    .map(KnowledgeServiceImpl::copyHit)
                    .toList();
            RerankRequest rerankRequest = new RerankRequest();
            rerankRequest.setModelConfigId(knowledgeBase.getRerankModelConfigId());
            rerankRequest.setQuery(req.getQueryText());
            rerankRequest.setDocuments(candidates.stream().map(KnowledgeSearchHit::getContent).toList());
            rerankRequest.setTopN(Math.min(candidates.size(), Math.max(options.topK(),
                    knowledgeBase.getRerankTopN() == null ? options.topK() : knowledgeBase.getRerankTopN())));
            List<RerankResult> rerankResults = rerankService.rerank(rerankRequest);
            Map<Integer, Double> scores = rerankResults == null ? Map.of() : rerankResults.stream()
                    .filter(item -> item.getIndex() != null && item.getScore() != null)
                    .collect(Collectors.toMap(RerankResult::getIndex, RerankResult::getScore, (left, right) -> left));
            List<KnowledgeSearchHit> reranked = new ArrayList<>(candidates);
            for (int i = 0; i < reranked.size(); i++) {
                Double rerankScore = scores.get(i);
                if (rerankScore != null) {
                    reranked.get(i).setRerankScore(rerankScore);
                    reranked.get(i).setScore(rerankScore);
                }
            }
            reranked.sort(Comparator.comparing(KnowledgeSearchHit::getScore,
                    Comparator.nullsLast(Comparator.reverseOrder())));
            return reranked;
        } catch (Exception e) {
            log.warn("rerank failed, fallback to retrieval score query={} message={}", req.getQueryText(), e.getMessage());
            return hits;
        }
    }

    private static KnowledgeSearchHit copyHit(KnowledgeSearchHit source) {
        KnowledgeSearchHit copy = new KnowledgeSearchHit();
        copy.setId(source.getId());
        copy.setKnowledgeBaseId(source.getKnowledgeBaseId());
        copy.setDocumentId(source.getDocumentId());
        copy.setChunkIndex(source.getChunkIndex());
        copy.setContent(source.getContent());
        copy.setTokenCount(source.getTokenCount());
        copy.setMetadataJson(source.getMetadataJson());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setScore(source.getScore());
        copy.setVectorScore(source.getVectorScore());
        copy.setKeywordScore(source.getKeywordScore());
        copy.setRerankScore(source.getRerankScore());
        return copy;
    }

    private static double scoreHybrid(Double vectorScore, Double keywordScore, double hybridAlpha) {
        double alpha = Math.max(0D, Math.min(1D, hybridAlpha));
        return Math.max(0D, vectorScore == null ? 0D : vectorScore) * alpha
                + Math.max(0D, keywordScore == null ? 0D : keywordScore) * (1D - alpha);
    }

    private RetrievalOptions resolveOptions(KnowledgeSearchReq req, KnowledgeBasePo fallbackKnowledgeBase) {
        int topK = req.getTopK() == null ? effectiveTopK(fallbackKnowledgeBase) : req.getTopK();
        int candidateTopK = req.getCandidateTopK() == null
                ? Math.max(topK, fallbackKnowledgeBase == null || fallbackKnowledgeBase.getCandidateTopK() == null
                        ? topK
                        : effectiveCandidateTopK(fallbackKnowledgeBase))
                : req.getCandidateTopK();
        double scoreThreshold = req.getScoreThreshold() == null
                ? effectiveScoreThreshold(fallbackKnowledgeBase)
                : req.getScoreThreshold();
        String retrievalMode = StringUtils.hasText(req.getRetrievalMode())
                ? req.getRetrievalMode()
                : effectiveRetrievalMode(fallbackKnowledgeBase);
        double hybridAlpha = effectiveHybridAlpha(fallbackKnowledgeBase);
        return new RetrievalOptions(topK, candidateTopK, scoreThreshold,
                normalizeRetrievalMode(retrievalMode), hybridAlpha);
    }

    private void saveRetrievalTrace(KnowledgeSearchReq req, String traceId, List<KnowledgeSearchResp> results,
                                    String status, String errorMessage, long latencyMs) {
        try {
            RagRetrievalTracePo trace = new RagRetrievalTracePo();
            trace.setTraceId(traceId);
            trace.setSourceType(StringUtils.hasText(req.getSourceType()) ? req.getSourceType() : "UNKNOWN");
            trace.setSourceId(req.getSourceId());
            trace.setQueryText(StringUtils.hasText(req.getQueryText()) ? req.getQueryText() : "");
            trace.setKnowledgeBaseIdsJson(toJsonList(req.getKnowledgeBaseIds()));
            trace.setRetrievalMode(StringUtils.hasText(req.getRetrievalMode()) ? req.getRetrievalMode() : "VECTOR");
            trace.setTopK(req.getTopK());
            trace.setScoreThreshold(req.getScoreThreshold());
            trace.setRerankEnabled(0);
            trace.setSelectedChunkIdsJson(toJsonList(results == null ? List.of()
                    : results.stream().map(KnowledgeSearchResp::getId).toList()));
            trace.setHitCount(results == null ? 0 : results.size());
            trace.setLatencyMs(latencyMs);
            trace.setStatus(status);
            trace.setErrorMessage(errorMessage == null ? "" : errorMessage);
            traceMapper.insert(trace);
        } catch (Exception e) {
            log.warn("failed to save rag retrieval trace traceId={}: {}", traceId, e.getMessage());
        }
    }

    private static long elapsedMillis(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
    }

    private String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "metadata 不是合法 JSON 对象", e);
        }
    }

    private Map<String, Object> documentMetadata(KnowledgeDocumentPo document, Integer chunkIndex) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("documentId", document.getId());
        metadata.put("documentName", document.getName());
        metadata.put("fileType", document.getFileType());
        metadata.put("department", blankToEmpty(document.getDepartment()));
        metadata.put("documentType", StringUtils.hasText(document.getDocumentType())
                ? document.getDocumentType()
                : document.getFileType());
        metadata.put("tags", fromJsonList(document.getTagsJson(), String.class));
        metadata.put("permissionScope", StringUtils.hasText(document.getPermissionScope())
                ? document.getPermissionScope()
                : "PROJECT");
        KnowledgeBasePo knowledgeBase = knowledgeBaseMapper.selectById(document.getKnowledgeBaseId());
        metadata.put("projectId", knowledgeBase == null ? null : knowledgeBase.getProjectId());
        metadata.put("workspaceId", knowledgeBase == null ? null : knowledgeBase.getWorkspaceId());
        if (chunkIndex != null) {
            metadata.put("chunkIndex", chunkIndex);
        }
        return metadata;
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("failed to parse chunk metadata: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String toJsonList(List<?> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "列表不是合法 JSON", e);
        }
    }

    private <T> List<T> fromJsonList(String json, Class<T> elementType) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (JsonProcessingException e) {
            log.warn("failed to parse trace json list: {}", e.getMessage());
            return List.of();
        }
    }

    private static void setDefaultRetrievalConfig(KnowledgeBasePo po) {
        po.setRetrievalMode("VECTOR");
        po.setHybridAlpha(DEFAULT_HYBRID_ALPHA);
        po.setTopK(DEFAULT_TOP_K);
        po.setCandidateTopK(DEFAULT_CANDIDATE_TOP_K);
        po.setScoreThreshold(DEFAULT_SCORE_THRESHOLD);
        po.setChunkSize(CHUNK_SIZE);
        po.setChunkOverlap(CHUNK_OVERLAP);
        po.setMaxContextTokens(DEFAULT_MAX_CONTEXT_TOKENS);
        po.setRerankEnabled(0);
        po.setRerankTopN(DEFAULT_CANDIDATE_TOP_K);
        po.setMetadataFilterEnabled(0);
        po.setDefaultMetadataFilterJson("{}");
        po.setActiveIndexVersion(1L);
        po.setIndexStatus(INDEX_STATUS_READY);
    }

    private void applyRebuildOptions(KnowledgeBasePo knowledgeBase, KnowledgeRebuildReq req) {
        if (req == null) {
            return;
        }
        boolean changed = false;
        if (req.getEmbeddingModelConfigId() != null
                && !req.getEmbeddingModelConfigId().equals(knowledgeBase.getEmbeddingModelConfigId())) {
            requireEnabledEmbeddingModel(req.getEmbeddingModelConfigId());
            knowledgeBase.setEmbeddingModelConfigId(req.getEmbeddingModelConfigId());
            changed = true;
        }
        if (req.getChunkSize() != null) {
            knowledgeBase.setChunkSize(req.getChunkSize());
            changed = true;
        }
        if (req.getChunkOverlap() != null) {
            knowledgeBase.setChunkOverlap(req.getChunkOverlap());
            changed = true;
        }
        if (knowledgeBase.getChunkOverlap() != null && knowledgeBase.getChunkSize() != null
                && knowledgeBase.getChunkOverlap() >= knowledgeBase.getChunkSize()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "chunkOverlap 必须小于 chunkSize");
        }
        if (changed) {
            knowledgeBaseMapper.updateById(knowledgeBase);
        }
    }

    private void applyRetrievalConfig(KnowledgeBasePo po, UpdateKnowledgeRetrievalConfigReq req) {
        if (StringUtils.hasText(req.getRetrievalMode())) {
            po.setRetrievalMode(normalizeRetrievalMode(req.getRetrievalMode()));
        }
        if (req.getHybridAlpha() != null) {
            po.setHybridAlpha(normalizeHybridAlpha(req.getHybridAlpha()));
        }
        if (req.getTopK() != null) {
            po.setTopK(req.getTopK());
        }
        if (req.getCandidateTopK() != null) {
            po.setCandidateTopK(req.getCandidateTopK());
        }
        if (req.getScoreThreshold() != null) {
            po.setScoreThreshold(req.getScoreThreshold());
        }
        if (req.getChunkSize() != null) {
            po.setChunkSize(req.getChunkSize());
        }
        if (req.getChunkOverlap() != null) {
            po.setChunkOverlap(req.getChunkOverlap());
        }
        if (req.getMaxContextTokens() != null) {
            po.setMaxContextTokens(req.getMaxContextTokens());
        }
        if (req.getRerankEnabled() != null) {
            if (req.getRerankEnabled() == 1) {
                requireEnabledRerankModel(req.getRerankModelConfigId() == null
                        ? po.getRerankModelConfigId()
                        : req.getRerankModelConfigId());
            }
            po.setRerankEnabled(req.getRerankEnabled() == 1 ? 1 : 0);
        }
        if (req.getRerankModelConfigId() != null) {
            if (Integer.valueOf(1).equals(po.getRerankEnabled())) {
                requireEnabledRerankModel(req.getRerankModelConfigId());
            }
            po.setRerankModelConfigId(req.getRerankModelConfigId());
        }
        if (req.getRerankTopN() != null) {
            po.setRerankTopN(req.getRerankTopN());
        }
        if (req.getMetadataFilterEnabled() != null) {
            po.setMetadataFilterEnabled(req.getMetadataFilterEnabled() == 1 ? 1 : 0);
        }
        if (req.getDefaultMetadataFilter() != null) {
            po.setDefaultMetadataFilterJson(toJson(req.getDefaultMetadataFilter()));
        }
        if (effectiveCandidateTopK(po) < effectiveTopK(po)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "candidateTopK 不能小于 topK");
        }
        if (po.getChunkOverlap() != null && po.getChunkSize() != null
                && po.getChunkOverlap() >= po.getChunkSize()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "chunkOverlap 必须小于 chunkSize");
        }
    }

    private static String effectiveRetrievalMode(KnowledgeBasePo po) {
        return po != null && StringUtils.hasText(po.getRetrievalMode())
                ? normalizeRetrievalMode(po.getRetrievalMode())
                : "VECTOR";
    }

    private static double effectiveHybridAlpha(KnowledgeBasePo po) {
        return po == null || po.getHybridAlpha() == null
                ? DEFAULT_HYBRID_ALPHA
                : normalizeHybridAlpha(po.getHybridAlpha());
    }

    private static Long effectiveActiveIndexVersion(KnowledgeBasePo po) {
        return po == null || po.getActiveIndexVersion() == null ? 1L : po.getActiveIndexVersion();
    }

    private static String effectiveIndexStatus(KnowledgeBasePo po) {
        return po != null && StringUtils.hasText(po.getIndexStatus()) ? po.getIndexStatus() : INDEX_STATUS_READY;
    }

    private static String normalizeRetrievalMode(String retrievalMode) {
        String normalized = retrievalMode == null ? "VECTOR" : retrievalMode.trim().toUpperCase();
        if (!List.of("VECTOR", "FULLTEXT", "HYBRID").contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "检索模式仅支持 VECTOR / FULLTEXT / HYBRID");
        }
        return normalized;
    }

    private static double normalizeHybridAlpha(Double hybridAlpha) {
        if (hybridAlpha == null) {
            return DEFAULT_HYBRID_ALPHA;
        }
        if (hybridAlpha < 0D || hybridAlpha > 1D) {
            throw new BizException(ErrorCode.PARAM_ERROR, "hybridAlpha 必须在 0-1 之间");
        }
        return hybridAlpha;
    }

    private static String effectiveVisibility(KnowledgeBasePo po) {
        return po != null && StringUtils.hasText(po.getVisibility()) ? po.getVisibility() : VISIBILITY_PROJECT;
    }

    private static String effectiveShareScope(KnowledgeBasePo po) {
        return po != null && StringUtils.hasText(po.getShareScope()) ? po.getShareScope() : VISIBILITY_PROJECT;
    }

    private static String normalizeVisibility(String value) {
        if (!StringUtils.hasText(value)) {
            return VISIBILITY_PROJECT;
        }
        String normalized = value.trim().toUpperCase();
        if (!List.of(VISIBILITY_PROJECT, VISIBILITY_WORKSPACE, VISIBILITY_PUBLIC).contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "共享范围仅支持 PROJECT / WORKSPACE / PUBLIC");
        }
        return normalized;
    }

    private static int effectiveTopK(KnowledgeBasePo po) {
        return po == null || po.getTopK() == null ? DEFAULT_TOP_K : po.getTopK();
    }

    private static int effectiveCandidateTopK(KnowledgeBasePo po) {
        return po == null || po.getCandidateTopK() == null ? DEFAULT_CANDIDATE_TOP_K : po.getCandidateTopK();
    }

    private static double effectiveScoreThreshold(KnowledgeBasePo po) {
        return po == null || po.getScoreThreshold() == null ? DEFAULT_SCORE_THRESHOLD : po.getScoreThreshold();
    }

    private void processDocumentTask(Long taskId, Long documentId) {
        KnowledgeTaskPo task = taskMapper.selectById(taskId);
        if (task == null || TASK_STATUS_CANCELED.equals(task.getStatus())) {
            return;
        }
        if (Integer.valueOf(1).equals(task.getCancelRequested())) {
            markDocumentCanceled(documentId, "文档处理已取消");
            markTaskCanceled(taskId, "文档处理已取消");
            return;
        }
        markTaskRunning(taskId);
        processDocumentAsync(documentId);
        KnowledgeDocumentPo document = documentMapper.selectById(documentId);
        if (document == null) {
            markTaskFailed(taskId, ErrorCode.NOT_FOUND.name(), "文档不存在: " + documentId);
            return;
        }
        if (STATUS_DONE.equals(document.getParseStatus())) {
            markTaskDone(taskId, "文档处理完成");
        } else if (STATUS_CANCELED.equals(document.getParseStatus())) {
            markTaskCanceled(taskId, document.getErrorMessage());
        } else if (STATUS_FAILED.equals(document.getParseStatus())) {
            markTaskFailed(taskId, document.getErrorCode(), document.getErrorMessage());
        }
    }

    private void processDocumentAsync(Long documentId) {
        KnowledgeDocumentPo document = documentMapper.selectById(documentId);
        if (document == null) {
            return;
        }
        if (STATUS_CANCELED.equals(document.getParseStatus()) || Integer.valueOf(1).equals(document.getCancelRequested())) {
            markDocumentCanceled(documentId, "文档处理已取消");
            return;
        }
        try {
            updateDocumentStatus(documentId, STATUS_PROCESSING, "", "", "", 0, 0, null);
            documentMapper.update(null, Wrappers.lambdaUpdate(KnowledgeDocumentPo.class)
                    .eq(KnowledgeDocumentPo::getId, documentId)
                    .set(KnowledgeDocumentPo::getStartedAt, LocalDateTime.now())
                    .set(KnowledgeDocumentPo::getFinishedAt, null));
            updateDocumentProgress(documentId, STAGE_EXTRACTING, 1, 0);
            KnowledgeBasePo knowledgeBase = findKnowledgeBaseOrThrow(document.getKnowledgeBaseId());
            int chunkCount = processDocumentContent(document, knowledgeBase);
            knowledgeBaseMapper.update(null, Wrappers.lambdaUpdate(KnowledgeBasePo.class)
                    .eq(KnowledgeBasePo::getId, document.getKnowledgeBaseId())
                    .setSql("chunk_count = chunk_count + " + chunkCount));
            updateDocumentProgress(documentId, STATUS_DONE, 100, chunkCount);
            updateDocumentStatus(documentId, STATUS_DONE, "", "", "", 0, 0, chunkCount);
            documentMapper.update(null, Wrappers.lambdaUpdate(KnowledgeDocumentPo.class)
                    .eq(KnowledgeDocumentPo::getId, documentId)
                    .set(KnowledgeDocumentPo::getFinishedAt, LocalDateTime.now()));
            finalizeRebuildIfComplete(document.getKnowledgeBaseId());
            log.info("processed knowledge document id={} chunks={}", documentId, chunkCount);
        } catch (DocumentProcessingCanceledException e) {
            markDocumentCanceled(documentId, e.getMessage());
        } catch (Exception e) {
            log.warn("process knowledge document failed id={}: {}", documentId, e.getMessage(), e);
            deleteFailedDocumentChunks(document);
            markKnowledgeBaseRebuildFailed(document.getKnowledgeBaseId());
            ProcessingFailure failure = classifyFailure(documentId, e);
            updateDocumentProgress(documentId, STATUS_FAILED, 0, null);
            updateDocumentStatus(documentId, STATUS_FAILED, e.getMessage(), failure.errorCode(),
                    failure.failedStage(), failure.retryable() ? 1 : 0, 0, 0);
            documentMapper.update(null, Wrappers.lambdaUpdate(KnowledgeDocumentPo.class)
                    .eq(KnowledgeDocumentPo::getId, documentId)
                    .set(KnowledgeDocumentPo::getFinishedAt, LocalDateTime.now()));
        } catch (Error e) {
            log.error("process knowledge document fatal error id={}: {}", documentId, e.getMessage(), e);
            try {
                vectorRepository.deleteByDocumentId(documentId);
                updateDocumentProgress(documentId, STATUS_FAILED, 0, null);
                updateDocumentStatus(documentId, STATUS_FAILED,
                        "文档处理发生严重错误: " + e.getClass().getSimpleName(), ERROR_INTERNAL,
                        currentProcessStage(documentId), 1, 0, 0);
                documentMapper.update(null, Wrappers.lambdaUpdate(KnowledgeDocumentPo.class)
                        .eq(KnowledgeDocumentPo::getId, documentId)
                        .set(KnowledgeDocumentPo::getFinishedAt, LocalDateTime.now()));
            } catch (Exception updateError) {
                log.error("failed to mark knowledge document as FAILED id={}: {}",
                        documentId, updateError.getMessage(), updateError);
            }
            throw e;
        }
    }

    private void deleteFailedDocumentChunks(KnowledgeDocumentPo document) {
        KnowledgeBasePo knowledgeBase = knowledgeBaseMapper.selectById(document.getKnowledgeBaseId());
        if (knowledgeBase != null && INDEX_STATUS_REBUILDING.equals(effectiveIndexStatus(knowledgeBase))
                && knowledgeBase.getBuildingIndexVersion() != null) {
            vectorRepository.deleteByDocumentIdAndIndexVersion(document.getId(), knowledgeBase.getBuildingIndexVersion());
            return;
        }
        vectorRepository.deleteByDocumentId(document.getId());
    }

    private void finalizeRebuildIfComplete(Long knowledgeBaseId) {
        KnowledgeBasePo knowledgeBase = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (knowledgeBase == null || !INDEX_STATUS_REBUILDING.equals(effectiveIndexStatus(knowledgeBase))
                || knowledgeBase.getBuildingIndexVersion() == null) {
            return;
        }
        Long remaining = documentMapper.selectCount(Wrappers.lambdaQuery(KnowledgeDocumentPo.class)
                .eq(KnowledgeDocumentPo::getKnowledgeBaseId, knowledgeBaseId)
                .ne(KnowledgeDocumentPo::getParseStatus, STATUS_DONE));
        if (remaining != null && remaining > 0) {
            return;
        }
        vectorRepository.activateIndexVersion(knowledgeBaseId, knowledgeBase.getBuildingIndexVersion());
        knowledgeBase.setActiveIndexVersion(knowledgeBase.getBuildingIndexVersion());
        knowledgeBase.setBuildingIndexVersion(null);
        knowledgeBase.setIndexStatus(INDEX_STATUS_READY);
        knowledgeBaseMapper.updateById(knowledgeBase);
    }

    private void markKnowledgeBaseRebuildFailed(Long knowledgeBaseId) {
        KnowledgeBasePo knowledgeBase = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (knowledgeBase == null || !INDEX_STATUS_REBUILDING.equals(effectiveIndexStatus(knowledgeBase))) {
            return;
        }
        knowledgeBase.setIndexStatus(INDEX_STATUS_FAILED);
        knowledgeBaseMapper.updateById(knowledgeBase);
    }

    private int processDocumentContent(KnowledgeDocumentPo document, KnowledgeBasePo knowledgeBase) throws IOException {
        Long embeddingModelConfigId = knowledgeBase.getEmbeddingModelConfigId();
        if (embeddingModelConfigId == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_VECTORIZE_FAILED, "知识库未配置 embedding 模型");
        }
        DocumentChunkProcessor processor = new DocumentChunkProcessor(document, embeddingModelConfigId,
                targetIndexVersion(knowledgeBase), targetActive(knowledgeBase), true);
        Path filePath = Path.of(document.getFileKey());
        if ("pdf".equals(document.getFileType())) {
            processPdfDocument(filePath, processor);
        } else if ("csv".equals(document.getFileType())) {
            processCsvDocument(filePath, processor);
        } else {
            processTextDocument(filePath, processor);
        }
        return processor.finish();
    }

    int processTextSegments(KnowledgeDocumentPo document, Long embeddingModelConfigId, Iterable<String> segments) {
        KnowledgeBasePo knowledgeBase = knowledgeBaseMapper.selectById(document.getKnowledgeBaseId());
        DocumentChunkProcessor processor = new DocumentChunkProcessor(document, embeddingModelConfigId,
                targetIndexVersion(knowledgeBase), targetActive(knowledgeBase), false);
        for (String segment : segments) {
            processor.accept(segment);
        }
        return processor.finish();
    }

    private void submitAfterCommit(Long taskId, Long documentId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            enqueueDocumentProcessing(taskId, documentId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                enqueueDocumentProcessing(taskId, documentId);
            }
        });
    }

    private void enqueueDocumentProcessing(Long taskId, Long documentId) {
        try {
            if (knowledgeTaskQueue != null) {
                knowledgeTaskQueue.submit(TaskRequest.builder()
                        .taskType(TaskType.KNOWLEDGE_PROCESS)
                        .taskId("knowledge-task-" + taskId)
                        .task(() -> processDocumentTask(taskId, documentId))
                        .build());
                return;
            }
            asyncExecutor.execute(() -> processDocumentTask(taskId, documentId));
        } catch (TaskRejectedException e) {
            handleDocumentQueueFull(taskId, documentId);
        } catch (RejectedExecutionException e) {
            handleDocumentQueueFull(taskId, documentId);
        }
    }

    private void handleDocumentQueueFull(Long taskId, Long documentId) {
        log.warn("knowledge document processing queue is full id={}", documentId);
        markTaskFailed(taskId, ERROR_QUEUE_FULL, "文档处理队列已满，请稍后重试");
        updateDocumentProgress(documentId, STATUS_FAILED, 0, 0);
        updateDocumentStatus(documentId, STATUS_FAILED, "文档处理队列已满，请稍后重试", ERROR_QUEUE_FULL,
                STAGE_SAVED, 1, 0, 0);
    }

    private void updateDocumentStatus(Long documentId, String status, String errorMessage, Integer chunkCount) {
        updateDocumentStatus(documentId, status, errorMessage, null, null, null, null, chunkCount);
    }

    private void updateDocumentStatus(Long documentId, String status, String errorMessage, String errorCode,
                                      String failedStage, Integer retryable, Integer cancelRequested,
                                      Integer chunkCount) {
        var wrapper = Wrappers.lambdaUpdate(KnowledgeDocumentPo.class)
                .eq(KnowledgeDocumentPo::getId, documentId)
                .set(KnowledgeDocumentPo::getParseStatus, status)
                .set(KnowledgeDocumentPo::getTaskStatus, toTaskStatus(status))
                .set(KnowledgeDocumentPo::getErrorMessage, truncateErrorMessage(errorMessage));
        if (errorCode != null) {
            wrapper.set(KnowledgeDocumentPo::getErrorCode, errorCode);
        }
        if (failedStage != null) {
            wrapper.set(KnowledgeDocumentPo::getFailedStage, failedStage);
        }
        if (retryable != null) {
            wrapper.set(KnowledgeDocumentPo::getRetryable, retryable);
        }
        if (cancelRequested != null) {
            wrapper.set(KnowledgeDocumentPo::getCancelRequested, cancelRequested);
        }
        if (chunkCount != null) {
            wrapper.set(KnowledgeDocumentPo::getChunkCount, chunkCount);
        }
        documentMapper.update(null, wrapper);
    }

    private void markDocumentCanceled(Long documentId, String message) {
        vectorRepository.deleteByDocumentId(documentId);
        KnowledgeDocumentPo document = documentMapper.selectById(documentId);
        if (document == null) {
            return;
        }
        document.setParseStatus(STATUS_CANCELED);
        document.setProcessStage(STATUS_CANCELED);
        document.setProcessProgress(0);
        document.setProcessedChunkCount(0);
        document.setChunkCount(0);
        document.setErrorMessage(truncateErrorMessage(message));
        document.setErrorCode(ERROR_CANCELED_BY_USER);
        document.setFailedStage(STATUS_CANCELED);
        document.setRetryable(0);
        document.setCancelRequested(1);
        document.setTaskStatus(TASK_STATUS_CANCELED);
        document.setProgressMessage("文档处理已取消");
        document.setFinishedAt(LocalDateTime.now());
        documentMapper.updateById(document);
        markTaskCanceled(document.getProcessingTaskId(), message);
        log.info("canceled knowledge document id={}", documentId);
    }

    private void resetDocumentForProcessing(KnowledgeDocumentPo document, String progressMessage) {
        document.setParseStatus(STATUS_PENDING);
        document.setProcessStage(STAGE_SAVED);
        document.setProcessProgress(0);
        document.setProcessedChunkCount(0);
        document.setChunkCount(0);
        document.setErrorMessage("");
        document.setErrorCode("");
        document.setFailedStage("");
        document.setRetryable(0);
        document.setCancelRequested(0);
        document.setStartedAt(null);
        document.setFinishedAt(null);
        document.setTaskStatus(TASK_STATUS_PENDING);
        document.setProgressMessage(progressMessage);
        document.setParseLatencyMs(null);
        document.setChunkLatencyMs(null);
        document.setEmbeddingLatencyMs(null);
        document.setVectorSaveLatencyMs(null);
        document.setLastProcessedChunkIndex(0);
    }

    private void checkCancellation(Long documentId) {
        KnowledgeDocumentPo document = documentMapper.selectById(documentId);
        if (document != null && (STATUS_CANCELED.equals(document.getParseStatus())
                || Integer.valueOf(1).equals(document.getCancelRequested()))) {
            throw new DocumentProcessingCanceledException("文档处理已取消");
        }
    }

    private String currentProcessStage(Long documentId) {
        KnowledgeDocumentPo document = documentMapper.selectById(documentId);
        return document == null || !StringUtils.hasText(document.getProcessStage())
                ? STAGE_SAVED
                : document.getProcessStage();
    }

    private ProcessingFailure classifyFailure(Long documentId, Exception e) {
        String stage = currentProcessStage(documentId);
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("未配置 embedding 模型")) {
            return new ProcessingFailure(ERROR_EMBEDDING_MODEL_MISSING, stage, true);
        }
        if (message.contains("未提取到文字内容") || message.contains("没有可向量化内容")) {
            return new ProcessingFailure(ERROR_FILE_EMPTY, stage, false);
        }
        if (message.contains("分块数量超过上限")) {
            return new ProcessingFailure(ERROR_CHUNK_LIMIT_EXCEEDED, stage, false);
        }
        if (STAGE_EMBEDDING.equals(stage)) {
            return new ProcessingFailure(ERROR_EMBEDDING_CALL_FAILED, stage, true);
        }
        if (STAGE_SAVING.equals(stage)) {
            return new ProcessingFailure(ERROR_VECTOR_SAVE_FAILED, stage, true);
        }
        return new ProcessingFailure(ERROR_INTERNAL, stage, true);
    }

    private void updateDocumentProgress(Long documentId, String stage, Integer progress, Integer processedChunkCount) {
        var wrapper = Wrappers.lambdaUpdate(KnowledgeDocumentPo.class)
                .eq(KnowledgeDocumentPo::getId, documentId)
                .set(KnowledgeDocumentPo::getProcessStage, stage)
                .set(KnowledgeDocumentPo::getProgressMessage, progressMessage(stage));
        if (progress != null) {
            wrapper.set(KnowledgeDocumentPo::getProcessProgress, Math.max(0, Math.min(100, progress)));
        }
        if (processedChunkCount != null) {
            int count = Math.max(0, processedChunkCount);
            wrapper.set(KnowledgeDocumentPo::getProcessedChunkCount, count)
                    .set(KnowledgeDocumentPo::getLastProcessedChunkIndex, count);
        }
        documentMapper.update(null, wrapper);
        updateTaskProgressForDocument(documentId, stage, progress, processedChunkCount, progressMessage(stage));
    }

    private KnowledgeTaskPo createTask(Long knowledgeBaseId, Long documentId, String taskType,
                                       String targetType, Long targetId, String reason) {
        KnowledgeTaskPo task = new KnowledgeTaskPo();
        task.setKnowledgeBaseId(knowledgeBaseId);
        task.setDocumentId(documentId);
        task.setTaskType(taskType);
        task.setTargetType(targetType);
        task.setTargetId(targetId);
        task.setReason(StringUtils.hasText(reason) ? reason : "UPLOAD");
        task.setStatus(TASK_STATUS_PENDING);
        task.setProcessStage(STAGE_SAVED);
        task.setProcessProgress(0);
        task.setProgressMessage("任务已创建，等待处理");
        task.setAttempt(0);
        task.setMaxAttempt(3);
        task.setLastProcessedChunkIndex(0);
        task.setCancelRequested(0);
        task.setErrorCode("");
        task.setErrorMessage("");
        taskMapper.insert(task);
        return task;
    }

    private void markTaskRunning(Long taskId) {
        if (taskId == null) {
            return;
        }
        taskMapper.update(null, Wrappers.lambdaUpdate(KnowledgeTaskPo.class)
                .eq(KnowledgeTaskPo::getId, taskId)
                .set(KnowledgeTaskPo::getStatus, TASK_STATUS_RUNNING)
                .set(KnowledgeTaskPo::getStartedAt, LocalDateTime.now())
                .setSql("attempt = attempt + 1")
                .set(KnowledgeTaskPo::getProgressMessage, "任务开始处理"));
    }

    private void markTaskDone(Long taskId, String message) {
        if (taskId == null) {
            return;
        }
        taskMapper.update(null, Wrappers.lambdaUpdate(KnowledgeTaskPo.class)
                .eq(KnowledgeTaskPo::getId, taskId)
                .set(KnowledgeTaskPo::getStatus, TASK_STATUS_DONE)
                .set(KnowledgeTaskPo::getProcessStage, STATUS_DONE)
                .set(KnowledgeTaskPo::getProcessProgress, 100)
                .set(KnowledgeTaskPo::getProgressMessage, blankToEmpty(message))
                .set(KnowledgeTaskPo::getFinishedAt, LocalDateTime.now()));
    }

    private void markTaskFailed(Long taskId, String errorCode, String errorMessage) {
        if (taskId == null) {
            return;
        }
        taskMapper.update(null, Wrappers.lambdaUpdate(KnowledgeTaskPo.class)
                .eq(KnowledgeTaskPo::getId, taskId)
                .set(KnowledgeTaskPo::getStatus, TASK_STATUS_FAILED)
                .set(KnowledgeTaskPo::getProcessStage, STATUS_FAILED)
                .set(KnowledgeTaskPo::getProgressMessage, "任务失败")
                .set(KnowledgeTaskPo::getErrorCode, blankToEmpty(errorCode))
                .set(KnowledgeTaskPo::getErrorMessage, truncateErrorMessage(errorMessage))
                .set(KnowledgeTaskPo::getFinishedAt, LocalDateTime.now()));
    }

    private void markTaskCanceled(Long taskId, String message) {
        if (taskId == null) {
            return;
        }
        taskMapper.update(null, Wrappers.lambdaUpdate(KnowledgeTaskPo.class)
                .eq(KnowledgeTaskPo::getId, taskId)
                .set(KnowledgeTaskPo::getStatus, TASK_STATUS_CANCELED)
                .set(KnowledgeTaskPo::getProcessStage, STATUS_CANCELED)
                .set(KnowledgeTaskPo::getProcessProgress, 0)
                .set(KnowledgeTaskPo::getProgressMessage, truncateErrorMessage(message))
                .set(KnowledgeTaskPo::getCancelRequested, 1)
                .set(KnowledgeTaskPo::getFinishedAt, LocalDateTime.now()));
    }

    private void updateTaskProgressForDocument(Long documentId, String stage, Integer progress,
                                               Integer processedChunkCount, String message) {
        KnowledgeDocumentPo document = documentMapper.selectById(documentId);
        if (document == null || document.getProcessingTaskId() == null) {
            return;
        }
        var wrapper = Wrappers.lambdaUpdate(KnowledgeTaskPo.class)
                .eq(KnowledgeTaskPo::getId, document.getProcessingTaskId())
                .set(KnowledgeTaskPo::getProcessStage, stage)
                .set(KnowledgeTaskPo::getProgressMessage, message);
        if (progress != null) {
            wrapper.set(KnowledgeTaskPo::getProcessProgress, Math.max(0, Math.min(100, progress)));
        }
        if (processedChunkCount != null) {
            wrapper.set(KnowledgeTaskPo::getLastProcessedChunkIndex, Math.max(0, processedChunkCount));
        }
        taskMapper.update(null, wrapper);
    }

    private static String progressMessage(String stage) {
        return switch (stage) {
            case STAGE_SAVED -> "文档已保存";
            case STAGE_EXTRACTING -> "正在解析文档";
            case STAGE_CHUNKING -> "正在切分文档";
            case STAGE_EMBEDDING -> "正在生成向量";
            case STAGE_SAVING -> "正在写入向量库";
            case STATUS_DONE -> "处理完成";
            case STATUS_FAILED -> "处理失败";
            case STATUS_CANCELED -> "处理已取消";
            default -> stage == null ? "" : stage;
        };
    }

    private static String truncateErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "";
        }
        return errorMessage.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? errorMessage
                : errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private String extractText(Path filePath, String fileType) throws IOException {
        String text;
        if ("pdf".equals(fileType)) {
            try (PDDocument pdf = PDDocument.load(filePath.toFile())) {
                text = new PDFTextStripper().getText(pdf);
            }
        } else if ("csv".equals(fileType)) {
            text = extractCsvText(readTextFile(filePath));
        } else {
            text = readTextFile(filePath);
        }
        if (!StringUtils.hasText(text)) {
            throw new BizException(ErrorCode.KNOWLEDGE_VECTORIZE_FAILED, "文档未提取到文字内容，扫描版 PDF 一期不支持");
        }
        return text;
    }

    private void processTextDocument(Path filePath, DocumentChunkProcessor processor) throws IOException {
        try (BufferedReader reader = newTextReader(filePath)) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                processor.accept(new String(buffer, 0, read));
            }
        }
    }

    private void processCsvDocument(Path filePath, DocumentChunkProcessor processor) throws IOException {
        try (BufferedReader reader = newTextReader(filePath)) {
            String line;
            List<String> headers = List.of();
            while ((line = reader.readLine()) != null) {
                List<String> row = parseCsvLine(line);
                if (row.stream().noneMatch(StringUtils::hasText)) {
                    continue;
                }
                if (headers.isEmpty()) {
                    headers = row;
                    continue;
                }
                String formatted = formatCsvRow(headers, row);
                if (StringUtils.hasText(formatted)) {
                    processor.accept(formatted);
                    processor.accept("\n");
                }
            }
        }
    }

    static String readTextFile(Path filePath) throws IOException {
        return Files.readString(filePath, detectTextCharset(filePath));
    }

    private static BufferedReader newTextReader(Path filePath) throws IOException {
        return Files.newBufferedReader(filePath, detectTextCharset(filePath));
    }

    private static Charset detectTextCharset(Path filePath) throws IOException {
        IOException lastError = null;
        for (Charset charset : TEXT_CHARSET_CANDIDATES) {
            try {
                verifyReadableWithCharset(filePath, charset);
                return charset;
            } catch (MalformedInputException | UnmappableCharacterException e) {
                lastError = e;
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        return StandardCharsets.UTF_8;
    }

    private static void verifyReadableWithCharset(Path filePath, Charset charset) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(filePath, charset)) {
            char[] buffer = new char[8192];
            while (reader.read(buffer) != -1) {
                // Drain the reader to force decoder errors before processing starts.
            }
        }
    }

    private void processPdfDocument(Path filePath, DocumentChunkProcessor processor) throws IOException {
        try (PDDocument pdf = PDDocument.load(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            int pages = pdf.getNumberOfPages();
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                processor.accept(stripper.getText(pdf));
                processor.accept("\n");
            }
        }
    }

    static String extractCsvText(String csvText) {
        String normalized = csvText == null ? "" : csvText.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty()) {
            return "";
        }
        List<List<String>> rows = normalized.lines()
                .map(KnowledgeServiceImpl::parseCsvLine)
                .filter(row -> row.stream().anyMatch(StringUtils::hasText))
                .toList();
        if (rows.isEmpty()) {
            return "";
        }
        List<String> headers = rows.get(0);
        boolean hasHeader = rows.size() > 1 && headers.stream().anyMatch(StringUtils::hasText);
        List<String> lines = new ArrayList<>();
        int startIndex = hasHeader ? 1 : 0;
        for (int i = startIndex; i < rows.size(); i++) {
            String formatted = hasHeader ? formatCsvRow(headers, rows.get(i)) : formatCsvRow(List.of(), rows.get(i));
            if (StringUtils.hasText(formatted)) {
                lines.add(formatted);
            }
        }
        if (lines.isEmpty() && !hasHeader) {
            return normalized;
        }
        return String.join("\n", lines);
    }

    private static List<String> parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(ch);
            }
        }
        cells.add(cell.toString());
        return cells;
    }

    private static String formatCsvRow(List<String> headers, List<String> row) {
        List<String> cells = new ArrayList<>();
        for (int j = 0; j < row.size(); j++) {
            String value = row.get(j).trim();
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (j < headers.size() && StringUtils.hasText(headers.get(j))) {
                cells.add(headers.get(j).trim() + ": " + value);
            } else {
                cells.add(value);
            }
        }
        return String.join(" | ", cells);
    }

    private List<ChunkDTO> splitChunks(String text) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n").trim();
        if (normalized.isEmpty()) {
            throw new BizException(ErrorCode.KNOWLEDGE_VECTORIZE_FAILED, "文档解析后没有可向量化内容");
        }
        List<ChunkDTO> chunks = new ArrayList<>();
        int start = 0;
        int chunkIndex = 0;
        int steps = 0;
        while (start < normalized.length()) {
            steps++;
            if (steps > maxSplitSteps || chunks.size() >= maxChunksPerDocument) {
                throw new BizException(ErrorCode.KNOWLEDGE_VECTORIZE_FAILED, "文档分块数量超过上限，请检查文档格式或减小文件大小");
            }
            int end = chooseChunkEnd(normalized, start);
            String content = normalized.substring(start, end).trim();
            if (!content.isBlank()) {
                chunks.add(new ChunkDTO(chunkIndex++, content, countTokens(content), null));
            }
            if (end == normalized.length()) {
                break;
            }
            start = nextChunkStart(normalized, start, end, CHUNK_OVERLAP);
        }
        return chunks;
    }

    private List<ChunkDTO> embedChunks(Long knowledgeBaseId, List<ChunkDTO> chunks) {
        KnowledgeBasePo knowledgeBase = findKnowledgeBaseOrThrow(knowledgeBaseId);
        List<ChunkDTO> result = new ArrayList<>(chunks.size());
        for (int start = 0; start < chunks.size(); start += 100) {
            List<ChunkDTO> batch = chunks.subList(start, Math.min(start + 100, chunks.size()));
            List<List<Double>> embeddings = embeddingService.embed(knowledgeBase.getEmbeddingModelConfigId(),
                    batch.stream().map(ChunkDTO::content).toList());
            for (int i = 0; i < batch.size(); i++) {
                ChunkDTO chunk = batch.get(i);
                result.add(new ChunkDTO(chunk.chunkIndex(), chunk.content(), chunk.tokenCount(), embeddings.get(i)));
            }
        }
        return result;
    }

    private void saveChunks(Long documentId, Long knowledgeBaseId, KnowledgeDocumentPo document, List<ChunkDTO> chunks) {
        List<KnowledgeChunk> rows = new ArrayList<>(chunks.size());
        KnowledgeBasePo knowledgeBase = knowledgeBaseMapper.selectById(knowledgeBaseId);
        Long indexVersion = targetIndexVersion(knowledgeBase);
        boolean active = !INDEX_STATUS_REBUILDING.equals(effectiveIndexStatus(knowledgeBase));
        for (ChunkDTO dto : chunks) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setKnowledgeBaseId(knowledgeBaseId);
            chunk.setDocumentId(documentId.toString());
            chunk.setChunkIndex(dto.chunkIndex());
            chunk.setContent(dto.content());
            chunk.setTokenCount(dto.tokenCount());
            chunk.setEmbedding(dto.embedding());
            chunk.setMetadataJson(toJson(documentMetadata(document, null)));
            chunk.setIndexVersion(indexVersion);
            chunk.setActive(active);
            rows.add(chunk);
        }
        vectorRepository.saveDocumentChunks(rows);
    }

    private static Long targetIndexVersion(KnowledgeBasePo knowledgeBase) {
        if (INDEX_STATUS_REBUILDING.equals(effectiveIndexStatus(knowledgeBase))
                && knowledgeBase.getBuildingIndexVersion() != null) {
            return knowledgeBase.getBuildingIndexVersion();
        }
        return effectiveActiveIndexVersion(knowledgeBase);
    }

    private static boolean targetActive(KnowledgeBasePo knowledgeBase) {
        return !INDEX_STATUS_REBUILDING.equals(effectiveIndexStatus(knowledgeBase));
    }

    private static int chooseChunkEnd(String text, int start) {
        return chooseChunkEnd(text, start, CHUNK_SIZE);
    }

    private static int chooseChunkEnd(String text, int start, int chunkSize) {
        int maxEnd = Math.min(start + chunkSize, text.length());
        if (maxEnd == text.length()) {
            return maxEnd;
        }
        int paragraph = text.lastIndexOf("\n\n", maxEnd);
        if (paragraph > start) {
            return paragraph + 2;
        }
        int sentence = Math.max(Math.max(text.lastIndexOf("。", maxEnd), text.lastIndexOf(".", maxEnd)),
                Math.max(Math.max(text.lastIndexOf("？", maxEnd), text.lastIndexOf("?", maxEnd)),
                        Math.max(text.lastIndexOf("！", maxEnd), text.lastIndexOf("!", maxEnd))));
        if (sentence > start) {
            return sentence + 1;
        }
        return maxEnd;
    }

    private static int rewindByTokens(String text, int end, int overlapTokens) {
        int count = 0;
        int pos = end;
        while (pos > 0 && count < overlapTokens) {
            pos--;
            if (Character.isWhitespace(text.charAt(pos))) {
                count++;
            }
        }
        return pos;
    }

    static int nextChunkStart(String text, int currentStart, int end, int overlapTokens) {
        int nextStart = rewindByTokens(text, end, overlapTokens);
        if (nextStart <= currentStart || nextStart >= end) {
            return end;
        }
        return nextStart;
    }

    private static int countTokens(String content) {
        int count = 0;
        StringBuilder ascii = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                count++;
            } else if (Character.isLetterOrDigit(ch)) {
                ascii.append(ch);
            } else if (!ascii.isEmpty()) {
                count++;
                ascii.setLength(0);
            }
        }
        if (!ascii.isEmpty()) {
            count++;
        }
        return Math.max(1, count);
    }

    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "上传文件不能为空");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new BizException(ErrorCode.KNOWLEDGE_FILE_TOO_LARGE, "文件大小不能超过 200MB");
        }
        String fileType = getFileType(file.getOriginalFilename());
        if (!ALLOWED_TYPES.contains(fileType)) {
            throw new BizException(ErrorCode.KNOWLEDGE_FILE_TYPE_UNSUPPORTED, "仅支持 txt/md/pdf/csv 文件");
        }
    }

    private static Path saveUploadFile(Long knowledgeBaseId, MultipartFile file, String fileType) {
        try {
            Path dir = Path.of("upload", "knowledge", knowledgeBaseId.toString());
            Files.createDirectories(dir);
            Path target = dir.resolve(UUID.randomUUID() + "." + fileType).normalize();
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target);
            }
            return target;
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文件保存失败", e);
        }
    }

    private static String getFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    @SuppressWarnings("unused")
    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int safeChunkCount(Integer chunkCount) {
        return chunkCount == null ? 0 : Math.max(0, chunkCount);
    }

    private static String toTaskStatus(String documentStatus) {
        if (STATUS_DONE.equals(documentStatus)) {
            return TASK_STATUS_DONE;
        }
        if (STATUS_FAILED.equals(documentStatus)) {
            return TASK_STATUS_FAILED;
        }
        if (STATUS_CANCELED.equals(documentStatus)) {
            return TASK_STATUS_CANCELED;
        }
        if (STATUS_PROCESSING.equals(documentStatus)) {
            return TASK_STATUS_RUNNING;
        }
        return TASK_STATUS_PENDING;
    }

    private static String normalizeReason(KnowledgeRebuildReq req, String fallback) {
        return req == null || !StringUtils.hasText(req.getReason()) ? fallback : req.getReason().trim().toUpperCase();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private class DocumentChunkProcessor {
        private final KnowledgeDocumentPo document;
        private final Long embeddingModelConfigId;
        private final Long indexVersion;
        private final boolean active;
        private final boolean progressEnabled;
        private final int embeddingBatchLimit;
        private final int chunkSize;
        private final int chunkOverlap;
        private final StringBuilder buffer = new StringBuilder();
        private final List<KnowledgeChunk> batch = new ArrayList<>();
        private int batchCharCount;
        private int chunkIndex;
        private int chunkCount;
        private int splitSteps;

        private DocumentChunkProcessor(KnowledgeDocumentPo document, Long embeddingModelConfigId,
                                       Long indexVersion, boolean active, boolean progressEnabled) {
            this.document = document;
            this.embeddingModelConfigId = embeddingModelConfigId;
            this.indexVersion = indexVersion;
            this.active = active;
            this.progressEnabled = progressEnabled;
            this.embeddingBatchLimit = resolveEmbeddingBatchSize(embeddingModelConfigId);
            this.chunkSize = effectiveChunkSize(document);
            this.chunkOverlap = Math.min(CHUNK_OVERLAP, Math.max(0, chunkSize / 4));
        }

        private void accept(String text) {
            if (text == null || text.isEmpty()) {
                return;
            }
            buffer.append(text);
            drain(false);
        }

        private int finish() {
            drain(true);
            flushBatch();
            if (chunkCount == 0) {
                throw new BizException(ErrorCode.KNOWLEDGE_VECTORIZE_FAILED, "文档解析后没有可向量化内容");
            }
            return chunkCount;
        }

        private void drain(boolean flushRemaining) {
            while (buffer.length() >= chunkSize || (flushRemaining && !buffer.isEmpty())) {
                checkCancellation(document.getId());
                splitSteps++;
                if (splitSteps > maxSplitSteps || chunkCount >= maxChunksPerDocument) {
                    throw new BizException(ErrorCode.KNOWLEDGE_VECTORIZE_FAILED,
                            "文档分块数量超过上限，请检查文档格式或减小文件大小");
                }
                String current = buffer.toString();
                int end = flushRemaining && current.length() <= chunkSize
                        ? current.length()
                        : chooseChunkEnd(current, 0, chunkSize);
                String content = current.substring(0, end).trim();
                if (StringUtils.hasText(content)) {
                    if (!batch.isEmpty() && batchCharCount + content.length() > effectiveEmbeddingBatchMaxChars()) {
                        flushBatch();
                    }
                    batch.add(toKnowledgeChunk(content));
                    batchCharCount += content.length();
                    chunkCount++;
                    if (batch.size() >= embeddingBatchLimit) {
                        flushBatch();
                    }
                }
                if (end == current.length()) {
                    buffer.setLength(0);
                    break;
                }
                int nextStart = nextChunkStart(current, 0, end, chunkOverlap);
                buffer.delete(0, nextStart);
            }
        }

        private KnowledgeChunk toKnowledgeChunk(String content) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            int currentChunkIndex = chunkIndex++;
            chunk.setKnowledgeBaseId(document.getKnowledgeBaseId());
            chunk.setDocumentId(document.getId().toString());
            chunk.setChunkIndex(currentChunkIndex);
            chunk.setContent(content);
            chunk.setTokenCount(countTokens(content));
            chunk.setMetadataJson(toJson(documentMetadata(document, currentChunkIndex)));
            chunk.setIndexVersion(indexVersion);
            chunk.setActive(active);
            return chunk;
        }

        private void flushBatch() {
            if (batch.isEmpty()) {
                return;
            }
            checkCancellation(document.getId());
            updateProgress(STAGE_EMBEDDING, progressForChunkCount(chunkCount), chunkCount);
            List<List<Double>> embeddings = embeddingService.embed(embeddingModelConfigId,
                    batch.stream().map(KnowledgeChunk::getContent).toList());
            if (embeddings.size() != batch.size()) {
                throw new BizException(ErrorCode.KNOWLEDGE_VECTORIZE_FAILED,
                        "Embedding 返回数量不匹配，expected=" + batch.size() + " actual=" + embeddings.size());
            }
            for (int i = 0; i < batch.size(); i++) {
                batch.get(i).setEmbedding(embeddings.get(i));
            }
            checkCancellation(document.getId());
            updateProgress(STAGE_SAVING, progressForChunkCount(chunkCount), chunkCount);
            vectorRepository.saveDocumentChunks(new ArrayList<>(batch));
            batch.clear();
            batchCharCount = 0;
            checkCancellation(document.getId());
            updateProgress(STAGE_CHUNKING, progressForChunkCount(chunkCount), chunkCount);
        }

        private void updateProgress(String stage, Integer progress, Integer processedChunkCount) {
            if (progressEnabled) {
                updateDocumentProgress(document.getId(), stage, progress, processedChunkCount);
            }
        }
    }

    private int resolveEmbeddingBatchSize(Long embeddingModelConfigId) {
        int configuredBatchSize = embeddingBatchSize;
        try {
            ModelConfigResp modelConfig = embeddingModelConfigId == null
                    ? null
                    : modelConfigService.getById(embeddingModelConfigId);
            if (modelConfig != null && "OLLAMA".equalsIgnoreCase(modelConfig.getProviderType())) {
                configuredBatchSize = ollamaEmbeddingBatchSize;
            }
        } catch (Exception e) {
            log.debug("resolve embedding provider type failed modelConfigId={} message={}",
                    embeddingModelConfigId, e.getMessage());
        }
        return Math.max(1, Math.min(100, configuredBatchSize));
    }

    private int effectiveEmbeddingBatchMaxChars() {
        return Math.max(effectiveEmbeddingChunkMaxChars(), maxEmbeddingBatchChars);
    }

    private int effectiveEmbeddingChunkMaxChars() {
        return Math.max(CHUNK_SIZE, Math.min(MAX_DYNAMIC_CHUNK_SIZE, maxEmbeddingChunkChars));
    }

    private int effectiveChunkSize(KnowledgeDocumentPo document) {
        long fileSize = document.getFileSize() == null ? 0L : Math.max(0L, document.getFileSize());
        if (fileSize <= 0) {
            return CHUNK_SIZE;
        }
        int targetChunkCount = Math.max(1, maxChunksPerDocument / 2);
        long estimatedExtractedTextSize = fileSize * EXTRACTED_TEXT_EXPANSION_SAFETY_FACTOR;
        long dynamicChunkSize = ((estimatedExtractedTextSize + targetChunkCount - 1L) / targetChunkCount)
                + CHUNK_OVERLAP;
        return (int) Math.max(CHUNK_SIZE, Math.min(effectiveEmbeddingChunkMaxChars(), dynamicChunkSize));
    }

    private static int progressForChunkCount(int chunkCount) {
        return Math.min(95, 5 + Math.max(1, chunkCount / 10));
    }

    private record ChunkDTO(int chunkIndex, String content, int tokenCount, List<Double> embedding) {
    }

    private record RetrievalOptions(int topK, int candidateTopK, double scoreThreshold,
                                    String retrievalMode, double hybridAlpha) {
    }

    private record ProcessingFailure(String errorCode, String failedStage, boolean retryable) {
    }

    private static class DocumentProcessingCanceledException extends RuntimeException {
        private DocumentProcessingCanceledException(String message) {
            super(message);
        }
    }
}
