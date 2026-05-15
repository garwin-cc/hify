package com.hify.workflow.engine.executor;

import com.hify.workflow.engine.NodeConfigDef;

import java.util.Map;

public record ApiCallConfig(
        String url,
        String method,
        Map<String, String> headers,
        String body,
        String responseJsonPath,
        String outputVariable
) implements NodeConfigDef {
}
