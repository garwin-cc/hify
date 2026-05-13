package com.hify.workflow.engine;

public interface WorkflowCallTraceSink {

    void record(Long workflowRunId,
                Long workflowNodeRunId,
                String nodeKey,
                String nodeType,
                WorkflowCallTrace trace);

    static WorkflowCallTraceSink noop() {
        return (workflowRunId, workflowNodeRunId, nodeKey, nodeType, trace) -> {
        };
    }
}
