package com.hify.workflow.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工作流单次执行的变量上下文。
 */
public class ExecutionContext {

    private static final String KEY_SEPARATOR = ".";

    private final Long workflowRunId;
    private final LinkedHashMap<String, Object> variables = new LinkedHashMap<>();
    private Long workflowNodeRunId;
    private String currentNodeKey;
    private String currentNodeType;
    private WorkflowCallTraceSink callTraceSink = WorkflowCallTraceSink.noop();

    public ExecutionContext(Long workflowRunId, String userMessage) {
        this.workflowRunId = workflowRunId;
        set("start", "userMessage", userMessage);
    }

    public ExecutionContext(Long workflowRunId, Map<String, Object> snapshot) {
        this.workflowRunId = workflowRunId;
        if (snapshot != null) {
            this.variables.putAll(snapshot);
        }
    }

    public Long getWorkflowRunId() {
        return workflowRunId;
    }

    public void bindCurrentNodeRun(Long nodeRunId, String nodeKey, String nodeType, WorkflowCallTraceSink traceSink) {
        this.workflowNodeRunId = nodeRunId;
        this.currentNodeKey = nodeKey;
        this.currentNodeType = nodeType;
        this.callTraceSink = traceSink == null ? WorkflowCallTraceSink.noop() : traceSink;
    }

    public void clearCurrentNodeRun() {
        this.workflowNodeRunId = null;
        this.currentNodeKey = null;
        this.currentNodeType = null;
        this.callTraceSink = WorkflowCallTraceSink.noop();
    }

    public void recordCall(WorkflowCallTrace trace) {
        if (trace == null || currentNodeKey == null) {
            return;
        }
        callTraceSink.record(workflowRunId, workflowNodeRunId, currentNodeKey, currentNodeType, trace);
    }

    public void set(String nodeKey, String varName, Object value) {
        variables.put(buildKey(nodeKey, varName), value);
    }

    public Object get(String nodeKey, String varName) {
        return variables.get(buildKey(nodeKey, varName));
    }

    public String resolve(String template) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        String resolved = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            Object value = entry.getValue();
            resolved = resolved.replace(placeholder, value == null ? "" : String.valueOf(value));
        }
        return resolved;
    }

    public Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(variables);
    }

    private static String buildKey(String nodeKey, String varName) {
        return nodeKey + KEY_SEPARATOR + varName;
    }
}
