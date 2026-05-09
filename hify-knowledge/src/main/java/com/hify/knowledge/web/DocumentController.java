package com.hify.knowledge.web;

import com.hify.common.web.Result;
import com.hify.knowledge.api.KnowledgeChunkResp;
import com.hify.knowledge.api.KnowledgeDocumentResp;
import com.hify.knowledge.api.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final KnowledgeService knowledgeService;

    @GetMapping("/{id}")
    public Result<KnowledgeDocumentResp> getDetail(@PathVariable Long id) {
        return Result.ok(knowledgeService.getDocument(id));
    }

    @GetMapping("/{id}/chunks")
    public Result<List<KnowledgeChunkResp>> listChunks(@PathVariable Long id) {
        return Result.ok(knowledgeService.listDocumentChunks(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.deleteDocument(id);
        return Result.ok();
    }
}
