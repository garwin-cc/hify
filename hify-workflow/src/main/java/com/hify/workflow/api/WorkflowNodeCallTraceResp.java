package com.hify.workflow.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class WorkflowNodeCallTraceResp {

    private Long id;

    private Long workflowRunId;

    private Long workflowNodeRunId;

    private String nodeKey;

    private String nodeType;

    private String callType;

    private String target;

    private Map<String, Object> requestSnapshot;

    private Map<String, Object> responseSnapshot;

    private String status;

    private String errorMessage;

    private Integer durationMs;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
