package com.hify.workflow.domain;

import java.util.List;

public interface WorkflowReviewHandler {

    void createWaitingReview(Long workflowRunId, String nodeKey, String title, String content,
                             List<String> actions, boolean allowEdit, String outputVariable);
}
