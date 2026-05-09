package com.hify.workflow.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class WorkflowNodeRunResp {

    private Long id;

    private Long workflowRunId;

    private String nodeKey;

    private String nodeType;

    private String status;

    private Map<String, Object> outputs;

    private String error;

    private Integer elapsedMs;

    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;
}
