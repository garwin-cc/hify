package com.hify.workflow.api;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class WorkflowTemplateDetailResp {

    private Long id;

    private String name;

    private String description;

    private String category;

    private String icon;

    private Integer enabled;

    private Integer builtin;

    private JsonNode configJson;

    private List<WorkflowTemplateRequirementResp> requirements;

    private Integer nodeCount;

    private String status;

    private Long currentVersionId;

    private Integer latestVersionNo;

    private List<String> tags;

    private List<String> nodeTypes;

    private Integer requirementCount;

    private Integer usageCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
