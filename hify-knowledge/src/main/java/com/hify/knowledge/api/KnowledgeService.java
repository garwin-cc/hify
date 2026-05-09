package com.hify.knowledge.api;

import java.util.List;
import com.hify.common.web.PageResult;
import org.springframework.web.multipart.MultipartFile;

/** 知识库 RAG 服务，跨模块调用的统一入口。 */
public interface KnowledgeService {

    KnowledgeBaseResp createKnowledgeBase(CreateKnowledgeBaseReq req);

    PageResult<KnowledgeBaseResp> listKnowledgeBases(KnowledgeBaseQuery query);

    KnowledgeBaseResp getKnowledgeBase(Long id);

    KnowledgeBaseResp updateKnowledgeBase(Long id, UpdateKnowledgeBaseReq req);

    void deleteKnowledgeBase(Long id);

    Long uploadDocument(Long knowledgeBaseId, MultipartFile file);

    PageResult<KnowledgeDocumentResp> listDocuments(Long knowledgeBaseId, KnowledgeDocumentQuery query);

    KnowledgeDocumentResp getDocument(Long id);

    List<KnowledgeChunkResp> listDocumentChunks(Long documentId);

    void deleteDocument(Long id);

    Long upsertChunk(KnowledgeChunkUpsertReq req);

    List<KnowledgeSearchResp> searchSimilar(KnowledgeSearchReq req);
}
