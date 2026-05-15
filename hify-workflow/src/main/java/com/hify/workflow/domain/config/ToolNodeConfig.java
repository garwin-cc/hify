package com.hify.workflow.domain.config;

import java.util.Map;

public record ToolNodeConfig(
        Long mcpServerId,
        String toolName,
        Map<String, Object> inputMapping,
        String outputVariable,
        Integer timeoutSeconds
) implements NodeConfig {
}
