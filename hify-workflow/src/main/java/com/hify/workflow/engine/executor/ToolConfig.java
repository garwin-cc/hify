package com.hify.workflow.engine.executor;

import com.hify.workflow.engine.NodeConfigDef;

import java.util.Map;

public record ToolConfig(
        Long mcpServerId,
        String toolName,
        Map<String, Object> inputMapping,
        String outputVariable
) implements NodeConfigDef {
}
