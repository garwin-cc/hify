package com.hify.workflow.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class WorkflowRunResp {

    private Long id;

    private Long workflowId;

    private String status;

    private String input;

    private String output;

    private String error;

    private String currentNodeKey;

    private LocalDateTime timeoutAt;

    private String runMode;

    private Integer elapsedMs;

    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;

    private List<WorkflowNodeRunResp> nodeRuns;
}
