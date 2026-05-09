package com.hify.workflow.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkflowEdgeDto {

    @NotBlank(message = "起始节点不能为空")
    private String sourceNodeKey;

    @NotBlank(message = "目标节点不能为空")
    private String targetNodeKey;

    private String edgeType;

    private String conditionExpression;

    private Integer sortOrder;
}
