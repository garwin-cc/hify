package com.hify.workflow.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.workflow.engine.WorkflowCallTrace;
import com.hify.workflow.engine.WorkflowCallTraceSink;
import com.hify.workflow.infra.WorkflowNodeCallTraceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowNodeCallTraceService implements WorkflowCallTraceSink {

    private static final int MAX_ERROR_LENGTH = 500;

    private final WorkflowNodeCallTraceMapper workflowNodeCallTraceMapper;
    private final WorkflowSnapshotSanitizer sanitizer;
    private final ObjectMapper objectMapper;

    @Override
    public void record(Long workflowRunId,
                       Long workflowNodeRunId,
                       String nodeKey,
                       String nodeType,
                       WorkflowCallTrace trace) {
        try {
            WorkflowNodeCallTracePo po = new WorkflowNodeCallTracePo();
            po.setWorkflowRunId(workflowRunId);
            po.setWorkflowNodeRunId(workflowNodeRunId);
            po.setNodeKey(nodeKey);
            po.setNodeType(nodeType);
            po.setCallType(trace.callType());
            po.setTarget(trace.target() == null ? "" : trace.target());
            po.setRequestSnapshot(toJson(sanitizer.sanitize(trace.requestSnapshot())));
            po.setResponseSnapshot(toJson(sanitizer.sanitize(trace.responseSnapshot())));
            po.setStatus(trace.status());
            po.setErrorMessage(shortError(trace.errorMessage()));
            po.setDurationMs(trace.durationMs());
            po.setStartedAt(LocalDateTime.now().minusNanos((long) Math.max(0, trace.durationMs() == null ? 0 : trace.durationMs()) * 1_000_000L));
            po.setFinishedAt(LocalDateTime.now());
            workflowNodeCallTraceMapper.insert(po);
        } catch (Exception e) {
            log.warn("failed to record workflow node call trace runId={} nodeKey={}: {}",
                    workflowRunId, nodeKey, e.getMessage());
        }
    }

    private String toJson(Map<String, Object> value) throws JsonProcessingException {
        if (value == null || value.isEmpty()) {
            return "{}";
        }
        return objectMapper.writeValueAsString(value);
    }

    private static String shortError(String error) {
        if (error == null || error.isBlank()) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
