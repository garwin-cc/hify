package com.hify.workflow.engine;

import java.util.Map;

public record WorkflowCallTrace(
        String callType,
        String target,
        Map<String, Object> requestSnapshot,
        Map<String, Object> responseSnapshot,
        String status,
        String errorMessage,
        Integer durationMs
) {
}
