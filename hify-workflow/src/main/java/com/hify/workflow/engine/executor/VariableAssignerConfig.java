package com.hify.workflow.engine.executor;

import com.hify.workflow.engine.NodeConfigDef;

import java.util.Map;

public record VariableAssignerConfig(
        Map<String, String> assignments
) implements NodeConfigDef {
}
