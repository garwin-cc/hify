package com.hify.knowledge.web;

import com.hify.auth.api.RequireRole;
import com.hify.auth.api.UserRole;
import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.knowledge.api.CreateKnowledgeBaseReq;
import com.hify.knowledge.api.KnowledgeBaseQuery;
import com.hify.knowledge.api.KnowledgeBaseResp;
import com.hify.knowledge.api.KnowledgeChunkUpsertReq;
import com.hify.knowledge.api.KnowledgeDocumentQuery;
import com.hify.knowledge.api.KnowledgeDocumentResp;
import com.hify.knowledge.api.KnowledgeRebuildReq;
import com.hify.knowledge.api.KnowledgeSearchReq;
import com.hify.knowledge.api.KnowledgeSearchResp;
import com.hify.knowledge.api.KnowledgeService;
import com.hify.knowledge.api.KnowledgeTaskResp;
import com.hify.knowledge.api.RagRetrievalTraceResp;
import com.hify.knowledge.api.UpdateKnowledgeBaseReq;
import com.hify.knowledge.api.UpdateKnowledgeRetrievalConfigReq;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @PostMapping
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<KnowledgeBaseResp> create(@Valid @RequestBody CreateKnowledgeBaseReq req) {
        return Result.ok(knowledgeService.createKnowledgeBase(req));
    }

    @GetMapping
    public PageResult<KnowledgeBaseResp> list(KnowledgeBaseQuery query) {
        return knowledgeService.listKnowledgeBases(query);
    }

    @GetMapping("/{id}")
    public Result<KnowledgeBaseResp> getDetail(@PathVariable Long id) {
        return Result.ok(knowledgeService.getKnowledgeBase(id));
    }

    @PutMapping("/{id}")
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<KnowledgeBaseResp> update(@PathVariable Long id,
                                            @Valid @RequestBody UpdateKnowledgeBaseReq req) {
        return Result.ok(knowledgeService.updateKnowledgeBase(id, req));
    }

    @PutMapping("/{id}/retrieval-config")
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<KnowledgeBaseResp> updateRetrievalConfig(@PathVariable Long id,
                                                           @Valid @RequestBody UpdateKnowledgeRetrievalConfigReq req) {
        return Result.ok(knowledgeService.updateRetrievalConfig(id, req));
    }

    @DeleteMapping("/{id}")
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.deleteKnowledgeBase(id);
        return Result.ok();
    }

    @PostMapping("/{id}/rebuild-index")
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<Long> rebuildIndex(@PathVariable Long id,
                                     @Valid @RequestBody(required = false) KnowledgeRebuildReq req) {
        return Result.ok(knowledgeService.rebuildKnowledgeBaseIndex(id, req));
    }

    @PostMapping("/{kbId}/documents")
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<Long> uploadDocument(@PathVariable Long kbId,
                                       @RequestParam("file") MultipartFile file) {
        return Result.ok(knowledgeService.uploadDocument(kbId, file));
    }

    @GetMapping("/{kbId}/documents")
    public PageResult<KnowledgeDocumentResp> listDocuments(@PathVariable Long kbId,
                                                           KnowledgeDocumentQuery query) {
        return knowledgeService.listDocuments(kbId, query);
    }

    @GetMapping("/{kbId}/tasks")
    public Result<List<KnowledgeTaskResp>> listTasks(@PathVariable Long kbId,
                                                     @RequestParam(required = false) Long documentId) {
        return Result.ok(knowledgeService.listProcessingTasks(kbId, documentId));
    }

    @PostMapping("/chunks")
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<Long> upsertChunk(@Valid @RequestBody KnowledgeChunkUpsertReq req) {
        return Result.ok(knowledgeService.upsertChunk(req));
    }

    @PostMapping("/search")
    public Result<List<KnowledgeSearchResp>> search(@Valid @RequestBody KnowledgeSearchReq req) {
        return Result.ok(knowledgeService.searchSimilar(req));
    }

    @PostMapping("/{id}/retrieval-test")
    public Result<List<KnowledgeSearchResp>> retrievalTest(@PathVariable Long id,
                                                           @Valid @RequestBody KnowledgeSearchReq req) {
        req.setKnowledgeBaseIds(List.of(id));
        req.setSourceType("TEST");
        req.setSourceId("knowledge-base:" + id);
        req.setIncludeTrace(true);
        return Result.ok(knowledgeService.searchSimilar(req));
    }

    @GetMapping("/rag-traces/{traceId}")
    public Result<RagRetrievalTraceResp> getRetrievalTrace(@PathVariable String traceId) {
        return Result.ok(knowledgeService.getRetrievalTrace(traceId));
    }
}
