package com.hify.workflow.engine.executor;

import com.hify.workflow.engine.NodeConfigDef;

public record IterationConfig(
        String inputArrayVariable,
        String itemVariable,
        String subflowStartNodeKey,
        String outputVariable,
        Integer maxConcurrency,
        Integer maxItems
) implements NodeConfigDef {
}
