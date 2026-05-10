package com.hify.workflow.domain;

import java.util.Map;

public interface WorkflowEventPublisher {

    void publishRunEvent(Long workflowRunId, String eventType, String status, Map<String, Object> payload);

    void publishNodeEvent(Long workflowRunId, String eventType, String nodeKey, String status, Map<String, Object> payload);
}
