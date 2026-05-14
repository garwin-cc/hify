package com.hify.workflow.engine.executor;

import com.hify.workflow.engine.NodeConfigDef;

public record CodeTaskConfig(
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
) implements NodeConfigDef {
}
