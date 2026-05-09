package com.hify.workflow.engine.executor;

import com.hify.workflow.engine.NodeConfigDef;

public record ConditionNodeConfig(
        String expression,
        String outputVariable
) implements NodeConfigDef {
}
