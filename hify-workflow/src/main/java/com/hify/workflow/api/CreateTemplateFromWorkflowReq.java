package com.hify.workflow.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateTemplateFromWorkflowReq {

    @NotNull(message = "工作流 ID 不能为空")
    private Long workflowId;

    @NotBlank(message = "模板名称不能为空")
    private String name;

    private String description;

    private String category;

    private String icon;

    private List<String> tags;

    private Boolean publish;

    private String changelog;
}
