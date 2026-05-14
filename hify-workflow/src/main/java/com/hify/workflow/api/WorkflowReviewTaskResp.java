package com.hify.workflow.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class WorkflowReviewTaskResp {

    private Long id;

    private Long workflowRunId;

    private String nodeKey;

    private String status;

    private String title;

    private String content;

    private List<String> actions;

    private Boolean allowEdit;

    private String outputVariable;

    private Long assigneeUserId;

    private String assigneeUsername;

    private LocalDateTime dueAt;

    private String timeoutAction;

    private LocalDateTime notifiedAt;

    private LocalDateTime expiredAt;

    private String reviewAction;

    private String reviewComment;

    private String reviewedBy;

    private LocalDateTime reviewedAt;
}
