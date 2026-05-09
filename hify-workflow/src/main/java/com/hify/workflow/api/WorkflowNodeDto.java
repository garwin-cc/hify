package com.hify.workflow.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkflowNodeDto {

    @NotBlank(message = "节点 key 不能为空")
    private String nodeKey;

    @NotBlank(message = "节点类型不能为空")
    private String nodeType;

    @NotBlank(message = "节点名称不能为空")
    private String name;

    private JsonNode config;

    private Integer positionX;

    private Integer positionY;
}
