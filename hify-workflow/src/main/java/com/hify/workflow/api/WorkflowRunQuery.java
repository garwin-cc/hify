package com.hify.workflow.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowRunQuery {

    private Long workflowId;

    private String status;

    private String traceId;

    private String runMode;

    private String source;

    private LocalDateTime createdAtStart;

    private LocalDateTime createdAtEnd;

    private int page = 1;

    private int size = 20;
}
