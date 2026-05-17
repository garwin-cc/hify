package com.hify.workflow.domain.config;

public record IterationNodeConfig(
        String inputArrayVariable,
        String itemVariable,
        String subflowStartNodeKey,
        String outputVariable,
        Integer maxConcurrency,
        Integer maxItems
) implements NodeConfig {
}
