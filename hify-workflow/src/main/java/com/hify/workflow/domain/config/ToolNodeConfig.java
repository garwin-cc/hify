package com.hify.workflow.domain.config;

import java.util.Map;

public record ToolNodeConfig(
        String toolName,
        Map<String, Object> inputMapping
) implements NodeConfig {
}
