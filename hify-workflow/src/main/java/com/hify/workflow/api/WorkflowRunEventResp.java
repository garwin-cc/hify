package com.hify.workflow.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class WorkflowRunEventResp {

    private Long id;

    private Long workflowRunId;

    private Integer eventSeq;

    private String eventType;

    private String nodeKey;

    private String status;

    private Map<String, Object> payload;

    private LocalDateTime createdAt;
}
