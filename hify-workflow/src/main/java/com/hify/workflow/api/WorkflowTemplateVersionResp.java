package com.hify.workflow.api;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class WorkflowTemplateVersionResp {

    private Long id;

    private Long templateId;

    private Integer versionNo;

    private JsonNode snapshotJson;

    private List<WorkflowTemplateRequirementResp> requirements;

    private Integer nodeCount;

    private List<String> nodeTypes;

    private String checksum;

    private String changelog;

    private String validationStatus;

    private LocalDateTime publishedAt;

    private LocalDateTime createdAt;
}
