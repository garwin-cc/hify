package com.hify.knowledge.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class KnowledgeChunkUpsertReq {

    @NotNull(message = "知识库 ID 不能为空")
    private Long knowledgeBaseId;

    @NotBlank(message = "文档 ID 不能为空")
    private String documentId;

    @NotNull(message = "分块序号不能为空")
    private Integer chunkIndex;

    @NotBlank(message = "分块内容不能为空")
    private String content;

    @NotEmpty(message = "向量不能为空")
    private List<Double> embedding;

    private Map<String, Object> metadata;
}
