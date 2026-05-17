package com.hify.workflow.domain.config;

public record IterationEndNodeConfig(
        String outputVariable
) implements NodeConfig {
}
