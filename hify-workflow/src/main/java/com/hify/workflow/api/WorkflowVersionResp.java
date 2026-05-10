package com.hify.workflow.api;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowVersionResp {

    private Long id;

    private Long workflowId;

    private Integer versionNo;

    private String changeSummary;

    private JsonNode snapshotJson;

    private LocalDateTime createdAt;
}
