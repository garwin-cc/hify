package com.hify.workflow.engine.executor;

import java.util.List;

public record CodeTaskResult(
        String status,
        String summary,
        List<String> changedFiles,
        String diff,
        String logs,
        String error
) {
}
