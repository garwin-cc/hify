package com.hify.workflow.engine.executor;

import com.hify.workflow.engine.NodeConfigDef;

public record CodeTaskConfig(
        String task,
        String executor,
        Long mcpServerId,
        String toolName,
        Integer timeoutSeconds,
        String outputVariable
) implements NodeConfigDef {
}
