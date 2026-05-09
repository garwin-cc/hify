package com.hify.knowledge.api;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateKnowledgeBaseReq {

    @Size(max = 100, message = "知识库名称不超过 100 个字符")
    private String name;

    @Size(max = 500, message = "描述不超过 500 个字符")
    private String description;

    private Long embeddingModelConfigId;

    private Integer enabled;
}
