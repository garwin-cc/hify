package com.hify.workflow.api;

import lombok.Data;

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
}
