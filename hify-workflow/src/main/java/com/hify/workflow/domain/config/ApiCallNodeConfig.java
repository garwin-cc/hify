package com.hify.workflow.domain.config;

import java.util.Map;

public record ApiCallNodeConfig(
        String url,
        String method,
        Map<String, String> headers,
        String body,
        String responseJsonPath,
        String outputVariable
) implements NodeConfig {
}
