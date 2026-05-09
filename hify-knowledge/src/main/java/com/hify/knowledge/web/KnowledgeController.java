package com.hify.knowledge.web;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.knowledge.api.CreateKnowledgeBaseReq;
import com.hify.knowledge.api.KnowledgeBaseQuery;
import com.hify.knowledge.api.KnowledgeBaseResp;
import com.hify.knowledge.api.KnowledgeChunkUpsertReq;
import com.hify.knowledge.api.KnowledgeDocumentQuery;
import com.hify.knowledge.api.KnowledgeDocumentResp;
import com.hify.knowledge.api.KnowledgeSearchReq;
import com.hify.knowledge.api.KnowledgeSearchResp;
import com.hify.knowledge.api.KnowledgeService;
import com.hify.knowledge.api.UpdateKnowledgeBaseReq;
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
    public Result<KnowledgeBaseResp> update(@PathVariable Long id,
                                            @Valid @RequestBody UpdateKnowledgeBaseReq req) {
        return Result.ok(knowledgeService.updateKnowledgeBase(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.deleteKnowledgeBase(id);
        return Result.ok();
    }

    @PostMapping("/{kbId}/documents")
    public Result<Long> uploadDocument(@PathVariable Long kbId,
                                       @RequestParam("file") MultipartFile file) {
        return Result.ok(knowledgeService.uploadDocument(kbId, file));
    }

    @GetMapping("/{kbId}/documents")
    public PageResult<KnowledgeDocumentResp> listDocuments(@PathVariable Long kbId,
                                                           KnowledgeDocumentQuery query) {
        return knowledgeService.listDocuments(kbId, query);
    }

    @PostMapping("/chunks")
    public Result<Long> upsertChunk(@Valid @RequestBody KnowledgeChunkUpsertReq req) {
        return Result.ok(knowledgeService.upsertChunk(req));
    }

    @PostMapping("/search")
    public Result<List<KnowledgeSearchResp>> search(@Valid @RequestBody KnowledgeSearchReq req) {
        return Result.ok(knowledgeService.searchSimilar(req));
    }
}
