package com.hify.workflow.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowTemplateListItemResp {

    private Long id;

    private String name;

    private String description;

    private String category;

    private String icon;

    private Integer enabled;

    private Integer builtin;

    private Integer nodeCount;

    private LocalDateTime createdAt;
}
