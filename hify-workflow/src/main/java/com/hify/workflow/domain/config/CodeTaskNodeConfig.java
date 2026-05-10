package com.hify.workflow.domain.config;

public record CodeTaskNodeConfig(
        String task,
        String executor,
        Long mcpServerId,
        String toolName,
        Integer timeoutSeconds,
        String outputVariable
) implements NodeConfig {
}
