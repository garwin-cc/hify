package com.hify.workflow.engine.executor;

import com.hify.workflow.engine.NodeConfigDef;

public record IterationEndConfig(
        String outputVariable
) implements NodeConfigDef {
}
