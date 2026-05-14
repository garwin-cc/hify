package com.hify.knowledge.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateKnowledgeBaseReq {

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 100, message = "知识库名称不超过 100 个字符")
    private String name;

    @Size(max = 500, message = "描述不超过 500 个字符")
    private String description;

    @NotNull(message = "请选择向量模型")
    private Long embeddingModelConfigId;

    private Long workspaceId;

    private Long projectId;

    private String visibility;

    private String shareScope;
}
