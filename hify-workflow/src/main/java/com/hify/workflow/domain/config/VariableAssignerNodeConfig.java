package com.hify.workflow.domain.config;

import java.util.Map;

public record VariableAssignerNodeConfig(
        Map<String, String> assignments
) implements NodeConfig {
}
