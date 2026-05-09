package com.hify.workflow.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreateWorkflowReq {

    @NotBlank(message = "工作流名称不能为空")
    private String name;

    private String description;

    private Integer enabled;

    @NotBlank(message = "开始节点不能为空")
    private String startNodeKey;

    @Valid
    @NotEmpty(message = "节点不能为空")
    private List<WorkflowNodeDto> nodes;

    @Valid
    private List<WorkflowEdgeDto> edges;
}
