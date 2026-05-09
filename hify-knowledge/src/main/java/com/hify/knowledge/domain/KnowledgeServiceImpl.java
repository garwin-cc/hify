package com.hify.knowledge.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.knowledge.api.*;
import com.hify.knowledge.infra.KnowledgeBaseMapper;
import com.hify.knowledge.infra.KnowledgeDocumentMapper;
import com.hify.model.api.EmbeddingService;
import com.hify.model.api.ModelConfigResp;
import com.hify.model.api.ModelConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final int DEFAULT_TOP_K = 5;
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int CHUNK_SIZE = 512;
    private static final int CHUNK_OVERLAP = 64;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;
    private static final Set<String> ALLOWED_TYPES = Set.of("txt", "md", "pdf");

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeVectorRepository vectorRepository;
    private final ObjectMapper objectMapper;
    private final @Qualifier("asyncExecutor") ThreadPoolExecutor asyncExecutor;
    private final EmbeddingService embeddingService;
    private final ModelConfigService modelConfigService;

    @Override
    @Transactional
    public KnowledgeBaseResp createKnowledgeBase(CreateKnowledgeBaseReq req) {
        requireEnabledEmbeddingModel(req.getEmbeddingModelConfigId());
        KnowledgeBasePo po = new KnowledgeBasePo();
        po.setName(req.getName());
        po.setDescription(req.getDescription() == null ? "" : req.getDescription());
        po.setEmbeddingModelConfigId(req.getEmbeddingModelConfigId());
        po.setEnabled(1);
        po.setDocumentCount(0);
        po.setChunkCount(0);
        knowledgeBaseMapper.insert(po);
        log.info("created knowledge base id={} name={}", po.getId(), po.getName());
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
        findKnowledgeBaseOrThrow(knowledgeBaseId);
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
        document.setParseStatus("PENDING");
        document.setChunkCount(0);
        document.setErrorMessage("");
        documentMapper.insert(document);

        knowledgeBaseMapper.update(null, Wrappers.lambdaUpdate(KnowledgeBasePo.class)
                .eq(KnowledgeBasePo::getId, knowledgeBaseId)
                .setSql("document_count = document_count + 1"));

        submitAfterCommit(document.getId());
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
        resp.setName(po.getName());
        resp.setDescription(po.getDescription());
        resp.setEmbeddingModelConfigId(po.getEmbeddingModelConfigId());
        resp.setEnabled(po.getEnabled());
        resp.setDocumentCount(po.getDocumentCount());
        resp.setChunkCount(po.getChunkCount());
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

    private KnowledgeDocumentResp toDocumentResp(KnowledgeDocumentPo po) {
        KnowledgeDocumentResp resp = new KnowledgeDocumentResp();
        resp.setId(po.getId());
        resp.setKnowledgeBaseId(po.getKnowledgeBaseId());
        resp.setName(po.getName());
        resp.setFileType(po.getFileType());
        resp.setFileSize(po.getFileSize());
        resp.setStatus(po.getParseStatus());
        resp.setChunkCount(po.getChunkCount());
        resp.setErrorMessage(po.getErrorMessage());
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

    @Override
    public List<KnowledgeSearchResp> searchSimilar(KnowledgeSearchReq req) {
        int topK = req.getTopK() == null ? DEFAULT_TOP_K : req.getTopK();
        return vectorRepository.search(req.getKnowledgeBaseIds(), req.getQueryEmbedding(), topK)
                .stream()
                .map(this::toResp)
                .collect(Collectors.toList());
    }

    private KnowledgeSearchResp toResp(KnowledgeSearchHit hit) {
        KnowledgeSearchResp resp = new KnowledgeSearchResp();
        resp.setId(hit.getId());
        resp.setKnowledgeBaseId(hit.getKnowledgeBaseId());
        resp.setDocumentId(hit.getDocumentId());
        resp.setChunkIndex(hit.getChunkIndex());
        resp.setContent(hit.getContent());
        resp.setScore(hit.getScore());
        resp.setMetadata(fromJson(hit.getMetadataJson()));
        resp.setCreatedAt(hit.getCreatedAt());
        return resp;
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

    private void processDocumentAsync(Long documentId) {
        KnowledgeDocumentPo document = documentMapper.selectById(documentId);
        if (document == null) {
            return;
        }
        try {
            updateDocumentStatus(documentId, "PROCESSING", "", null);
            String text = extractText(Path.of(document.getFileKey()), document.getFileType());
            List<ChunkDTO> chunks = splitChunks(text);
            chunks = embedChunks(document.getKnowledgeBaseId(), chunks);
            saveChunks(document.getId(), document.getKnowledgeBaseId(), document, chunks);
            knowledgeBaseMapper.update(null, Wrappers.lambdaUpdate(KnowledgeBasePo.class)
                    .eq(KnowledgeBasePo::getId, document.getKnowledgeBaseId())
                    .setSql("chunk_count = chunk_count + " + chunks.size()));
            updateDocumentStatus(documentId, "DONE", "", chunks.size());
            log.info("processed knowledge document id={} chunks={}", documentId, chunks.size());
        } catch (Exception e) {
            log.warn("process knowledge document failed id={}: {}", documentId, e.getMessage(), e);
            updateDocumentStatus(documentId, "FAILED", e.getMessage(), null);
        }
    }

    private void submitAfterCommit(Long documentId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            asyncExecutor.execute(() -> processDocumentAsync(documentId));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                asyncExecutor.execute(() -> processDocumentAsync(documentId));
            }
        });
    }

    private void updateDocumentStatus(Long documentId, String status, String errorMessage, Integer chunkCount) {
        var wrapper = Wrappers.lambdaUpdate(KnowledgeDocumentPo.class)
                .eq(KnowledgeDocumentPo::getId, documentId)
                .set(KnowledgeDocumentPo::getParseStatus, status)
                .set(KnowledgeDocumentPo::getErrorMessage, truncateErrorMessage(errorMessage));
        if (chunkCount != null) {
            wrapper.set(KnowledgeDocumentPo::getChunkCount, chunkCount);
        }
        documentMapper.update(null, wrapper);
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
        } else {
            text = Files.readString(filePath, StandardCharsets.UTF_8);
        }
        if (!StringUtils.hasText(text)) {
            throw new BizException(ErrorCode.KNOWLEDGE_VECTORIZE_FAILED, "文档未提取到文字内容，扫描版 PDF 一期不支持");
        }
        return text;
    }

    private List<ChunkDTO> splitChunks(String text) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n").trim();
        if (normalized.isEmpty()) {
            throw new BizException(ErrorCode.KNOWLEDGE_VECTORIZE_FAILED, "文档解析后没有可向量化内容");
        }
        List<ChunkDTO> chunks = new ArrayList<>();
        int start = 0;
        int chunkIndex = 0;
        while (start < normalized.length()) {
            int end = chooseChunkEnd(normalized, start);
            String content = normalized.substring(start, end).trim();
            if (!content.isBlank()) {
                chunks.add(new ChunkDTO(chunkIndex++, content, countTokens(content), null));
            }
            if (end == normalized.length()) {
                break;
            }
            start = rewindByTokens(normalized, end, CHUNK_OVERLAP);
            if (start <= 0 || start >= end) {
                start = end;
            }
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
        for (ChunkDTO dto : chunks) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setKnowledgeBaseId(knowledgeBaseId);
            chunk.setDocumentId(documentId.toString());
            chunk.setChunkIndex(dto.chunkIndex());
            chunk.setContent(dto.content());
            chunk.setTokenCount(dto.tokenCount());
            chunk.setEmbedding(dto.embedding());
            chunk.setMetadataJson(toJson(Map.of(
                    "documentId", documentId,
                    "documentName", document.getName(),
                    "fileType", document.getFileType()
            )));
            rows.add(chunk);
        }
        vectorRepository.saveDocumentChunks(rows);
    }

    private static int chooseChunkEnd(String text, int start) {
        int maxEnd = Math.min(start + CHUNK_SIZE, text.length());
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

    private static void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(ErrorCode.KNOWLEDGE_FILE_TOO_LARGE, "文件大小不能超过 10MB");
        }
        String fileType = getFileType(file.getOriginalFilename());
        if (!ALLOWED_TYPES.contains(fileType)) {
            throw new BizException(ErrorCode.KNOWLEDGE_FILE_TYPE_UNSUPPORTED, "仅支持 txt/md/pdf 文件");
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

    private record ChunkDTO(int chunkIndex, String content, int tokenCount, List<Double> embedding) {
    }
}
