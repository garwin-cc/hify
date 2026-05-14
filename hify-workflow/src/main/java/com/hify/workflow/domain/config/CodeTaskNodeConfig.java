package com.hify.workflow.domain.config;

public record CodeTaskNodeConfig(
        String task,
        String executor,
        Long mcpServerId,
        String toolName,
        Integer timeoutSeconds,
        String outputVariable,
        Boolean sandboxRequired,
        Boolean approvalRequired,
        Boolean diffAuditRequired,
        Long allowedProjectId,
        String allowedRole
) implements NodeConfig {
}
