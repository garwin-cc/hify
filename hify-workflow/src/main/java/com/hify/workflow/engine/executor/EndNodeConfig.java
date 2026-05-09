package com.hify.workflow.engine.executor;

import com.hify.workflow.engine.NodeConfigDef;

public record EndNodeConfig(
        String outputVariable
) implements NodeConfigDef {
}
