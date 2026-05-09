package com.hify.knowledge.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class KnowledgeSearchReq {

    private List<Long> knowledgeBaseIds;

    @NotEmpty(message = "查询向量不能为空")
    private List<Double> queryEmbedding;

    @Min(value = 1, message = "topK 最小为 1")
    @Max(value = 50, message = "topK 最大为 50")
    private Integer topK = 5;
}
