package com.hify.workflow.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class CreateWorkflowFromTemplateReq {

    @NotBlank(message = "工作流名称不能为空")
    private String name;

    private String description;

    private Integer enabled;

    private Map<String, Long> bindings;
}
