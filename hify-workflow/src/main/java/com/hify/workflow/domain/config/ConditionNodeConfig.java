package com.hify.workflow.domain.config;

public record ConditionNodeConfig(
        String expression,
        String outputVariable
) implements NodeConfig {
}
